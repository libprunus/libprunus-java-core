package org.libprunus.core.log.runtime

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import spock.lang.Specification

class LoggingFailureReporterSpec extends Specification {

    def "instance returns singleton reporter"() {
        expect:
        LoggingFailureReporter.instance().is(LoggingFailureReporter.instance())
    }

    def "offer reports synchronously when under rate limit"() {
        given:
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when:
        reporter.offer("Sync.method", new RuntimeException("sync-cause"))

        then:
        def output = captured.toString()
        output.contains("libprunus logging failure at Sync.method")
        output.contains("sync-cause")

        and: "the non-SOE non-null throwable branch went through printStackTrace — the stack-frame marker proves it, symmetric to the SOE/null negative assertions which exclude it"
        output.contains("\tat ")

        and: "the same-second CAS consumed exactly one slot — count advanced 0 to 1"
        (reporter.@rateLimiter.get() & 0xFFFFFFFFL) == 1L

        and: "happy path did not increment dropped counter"
        reporter.@droppedCount.sum() == 0L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer fails fast for OOM and non-SOE VirtualMachineError variants"() {
        given: "a fresh reporter and a snapshot of state for change detection"
        def reporter = new LoggingFailureReporter()
        long rateLimiterBefore = reporter.@rateLimiter.get()

        when: "a non-SOE VirtualMachineError (including OOM) is offered"
        reporter.offer("fatal.method", vmError)

        then: "the VME is rethrown unchanged and the early-throw branch leaves all reporter state untouched"
        thrown(expected)

        and: "the rate limiter state is untouched — early throw did not consume a slot"
        reporter.@rateLimiter.get() == rateLimiterBefore

        and: "no drop was recorded — the early throw fired before tryAcquire"
        reporter.@droppedCount.sum() == 0L

        and: "no flush was advanced"
        reporter.@lastReportedDropCount.get() == 0L

        where:
        vmError                            || expected
        new OutOfMemoryError("oom")        || OutOfMemoryError
        new InternalError("internal")      || InternalError
        new UnknownError("unknown")        || UnknownError
    }

    def "offer prints method name only when throwable is null"() {
        given:
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when:
        reporter.offer("null.throwable.method", null)

        then:
        def output = captured.toString()
        output.contains("libprunus logging failure at null.throwable.method")
        !output.contains("stack trace omitted due to StackOverflowError")
        !output.contains("\tat ")

        and: "no dropped-flush line was written — droppedCount=0 so reportDroppedIfNeeded must be a no-op"
        !output.contains("libprunus: ")

        cleanup:
        System.setErr(originalErr)
    }

    def "offer handles StackOverflowError through minimal reporting path"() {
        given:
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when:
        reporter.offer("fatal.method", new StackOverflowError("soe"))

        then:
        def output = captured.toString()
        output.contains("libprunus logging failure at fatal.method")
        output.contains("stack trace omitted due to StackOverflowError")
        !output.contains("\tat ")

        and: "the SOE branch consumed exactly one rate-limit slot — count advanced 0 to 1"
        (reporter.@rateLimiter.get() & 0xFFFFFFFFL) == 1L

        and: "no drop was recorded — the SOE was admitted, not rate-limited"
        reporter.@droppedCount.sum() == 0L

        and: "no flush advanced — droppedCount was 0 when reportDroppedIfNeeded ran"
        reporter.@lastReportedDropCount.get() == 0L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer drops via tryAcquire stored-second-in-future branch without consuming a slot"() {
        given: "a fresh reporter instance with rate limiter seeded to a far-future second so the stored-second-in-future branch fires"
        def reporter = new LoggingFailureReporter()
        long futureSecond = 1_000_000_000L
        reporter.@rateLimiter.set((futureSecond << 32) | 0L)
        long stateBefore = reporter.@rateLimiter.get()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "three offers arrive when stored second is ahead of current second"
        reporter.offer("burst.1", new RuntimeException("boom-1"))
        reporter.offer("burst.2", new RuntimeException("boom-2"))
        reporter.offer("burst.3", new RuntimeException("boom-3"))

        then: "all three are dropped and the counter accumulates correctly"
        captured.toString().findAll("libprunus logging failure at").size() == 0
        reporter.@droppedCount.sum() == 3L

        and: "the rate-limiter state is byte-identical to the seeded future-second value — branch returned without CAS"
        reporter.@rateLimiter.get() == stateBefore

        and: "no flush advanced — the future-second branch fires before report()"
        reporter.@lastReportedDropCount.get() == 0L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer drops via tryAcquire same-second-cap branch when bucket is full for current second"() {
        given: "a fresh reporter with the bucket pre-filled to the per-second cap for the current second"
        def reporter = new LoggingFailureReporter()
        long startNanos = reporter.@startNanos
        long currentSecond = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos)
        long perSecondCap = 10L
        reporter.@rateLimiter.set((currentSecond << 32) | perSecondCap)
        long stateBefore = reporter.@rateLimiter.get()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "an offer arrives while the bucket is at the cap for the same second"
        reporter.offer("rate.capped", new RuntimeException("over-cap"))

        then: "tryAcquire takes the fast-return branch: no CAS, no spinning, state unchanged, drop counted"
        captured.toString().findAll("libprunus logging failure at").size() == 0
        reporter.@droppedCount.sum() == 1L
        reporter.@rateLimiter.get() == stateBefore

        and: "the dropped branch returned before report() — reportDroppedIfNeeded never ran, lastReportedDropCount untouched"
        reporter.@lastReportedDropCount.get() == 0L

        and: "no dropped-flush line was emitted — confirms the same-second-cap branch returned before any stderr write"
        !captured.toString().contains("libprunus: ")

        cleanup:
        System.setErr(originalErr)
    }

    def "offer resets rate-limiter bucket when current second advances past stored second"() {
        given: "a fresh reporter; wait until currentSecond is at least 1 so the seeded past-second value can legitimately be smaller"
        def reporter = new LoggingFailureReporter()
        long startNanos = reporter.@startNanos
        // Wait until at least one full wall-clock second has elapsed since reporter construction,
        // so a storedSecond of 0 is unambiguously older than the offer's computed currentSecond.
        // Without this wait the offer may compute currentSecond=0 and fall into the same-second
        // branch (count already at cap → drop), turning this test into a redundant duplicate of
        // the "drops immediately when same-second cap is reached" case.
        while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos) < 1L) {
            Thread.sleep(50L)
        }
        reporter.@rateLimiter.set((0L << 32) | 10L)
        long offerCurrentSecond = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos)
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "an offer arrives while the bucket is from a prior second (stored=0, current>=1)"
        reporter.offer("new.second.event", new RuntimeException("ok"))

        then: "the offer is reported synchronously — the cross-second branch reset the bucket"
        captured.toString().contains("libprunus logging failure at new.second.event")

        and: "the stored second has advanced to at least the offer's currentSecond"
        long after = reporter.@rateLimiter.get()
        (after >>> 32) >= offerCurrentSecond

        and: "the count was reset to 1 — proving the cross-second branch installed a fresh bucket"
        (after & 0xFFFFFFFFL) == 1L

        and: "no drop was recorded — the offer was admitted, not dropped"
        reporter.@droppedCount.sum() == 0L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer admits exactly MAX_DETAILED_PER_SECOND detailed reports per second before dropping"() {
        given: "a fresh reporter with the rate-limiter bucket explicitly cleared to count=0 for the current second"
        def reporter = new LoggingFailureReporter()
        long startNanos = reporter.@startNanos
        long currentSecond = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos)
        reporter.@rateLimiter.set((currentSecond << 32) | 0L)
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "11 offers are issued back-to-back in the same second"
        11.times { reporter.offer("bucket.fill.${it}", new RuntimeException("e${it}")) }

        then: "exactly 10 detailed lines were emitted — the documented cap"
        def detailedLines = captured.toString().findAll("libprunus logging failure at bucket.fill.").size()
        detailedLines == 10

        and: "the 11th offer was dropped — the cap rejected exactly one extra"
        reporter.@droppedCount.sum() == 1L

        and: "the rate-limiter bucket settled at exactly 10 — the count never drifted past the cap"
        (reporter.@rateLimiter.get() & 0xFFFFFFFFL) == 10L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer propagates OutOfMemoryError thrown by System.err during report"() {
        given: "stderr replaced with a stream that throws OOM on every write"
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def oomStream = new PrintStream(new OutputStream() {
            @Override
            void write(int b) throws IOException {
                throw new OutOfMemoryError("simulated OOM while writing stderr")
            }

            @Override
            void write(byte[] b, int off, int len) throws IOException {
                throw new OutOfMemoryError("simulated OOM while writing stderr")
            }
        }, true)
        System.setErr(oomStream)

        when: "an offer with a non-fatal throwable triggers a stderr write that runs out of memory"
        reporter.offer("oom.during.report", new RuntimeException("trigger"))

        then: "the VirtualMachineError branch of report() rethrows and no drop/flush bookkeeping is committed"
        thrown(OutOfMemoryError)
        reporter.@droppedCount.sum() == 0L
        reporter.@lastReportedDropCount.get() == 0L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer degrades to fallback line and swallows nested write failures across stderr fault modes"() {
        given: "a fresh reporter and a faulting stderr whose first write always throws and whose subsequent writes either pass through to a capture or also throw"
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def capture = new ByteArrayOutputStream()
        def firstWriteSeen = new AtomicBoolean(false)
        boolean failOnceLocal = failOnce
        def faultingStream = new PrintStream(new OutputStream() {
            @Override
            void write(int b) throws IOException {
                if (firstWriteSeen.compareAndSet(false, true) || !failOnceLocal) {
                    throw new RuntimeException("simulated non-VME failure on stderr write")
                }
                capture.write(b)
            }

            @Override
            void write(byte[] b, int off, int len) throws IOException {
                if (firstWriteSeen.compareAndSet(false, true) || !failOnceLocal) {
                    throw new RuntimeException("simulated non-VME failure on stderr write")
                }
                capture.write(b, off, len)
            }
        }, true)
        System.setErr(faultingStream)

        when: "an offer triggers a primary write (which fails) and then a degraded fallback (which either succeeds or also fails)"
        reporter.offer("io.failure.method", new RuntimeException("trigger"))

        then: "no exception propagates to the caller — the outer catch-Throwable swallows both primary and degraded write failures"
        noExceptionThrown()

        and: "captured output reflects the per-mode contract: degraded line on transient failure, empty on permanent failure"
        capture.toString() == expectedCapturedOutput

        and: "the primary detail line was NOT emitted — the first write failed before printing it"
        !capture.toString().contains("libprunus logging failure at io.failure.method")

        and: "no drop was recorded — the offer was admitted by tryAcquire, the degraded path does not mutate dropped state"
        reporter.@droppedCount.sum() == 0L

        and: "no flush advanced — the degraded fallback runs after reportDroppedIfNeeded was a no-op for droppedCount=0"
        reporter.@lastReportedDropCount.get() == 0L

        cleanup:
        System.setErr(originalErr)

        where:
        failOnce || expectedCapturedOutput
        true     || "libprunus logging failure and failed to report it: io.failure.method" + System.lineSeparator()
        false    || ""
    }

    def "offer skips dropped-flush line when no drops have accumulated since last flush"() {
        given: "a fresh reporter with no dropped events and captured stderr"
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "a single offer succeeds with droppedCount=lastReported=0 — reportDroppedIfNeeded must take the early-return guard"
        reporter.offer("first.success", new RuntimeException("e"))

        then: "the primary failure line was written — the offer was admitted"
        def output = captured.toString()
        output.contains("libprunus logging failure at first.success")

        and: "no dropped-flush line was emitted — the guard fired before any stderr write"
        !output.contains("libprunus: ")

        and: "lastReportedDropCount remained at 0 — the guard returned before the CAS advancement"
        reporter.@lastReportedDropCount.get() == 0L

        and: "droppedCount remained at 0 — happy path does not increment the dropped counter"
        reporter.@droppedCount.sum() == 0L

        and: "the offer still consumed exactly one rate-limit slot — proves tryAcquire ran to completion rather than the offer being short-circuited as dropped"
        (reporter.@rateLimiter.get() & 0xFFFFFFFFL) == 1L

        cleanup:
        System.setErr(originalErr)
    }

    def "previously dropped events are reported on the next successful offer"() {
        given: "a fresh reporter with pre-accumulated dropped events"
        def reporter = new LoggingFailureReporter()
        reporter.@droppedCount.add(7L)
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "the next offer passes the rate limit and triggers reporting"
        reporter.offer("after.drops", new RuntimeException("err"))

        then: "the prior dropped events are flushed and the detailed line follows; counter is reconciled"
        def output = captured.toString()
        output.contains("libprunus: 7 logging failure event(s) dropped (rate-limited)")
        output.contains("libprunus logging failure at after.drops")
        reporter.@lastReportedDropCount.get() == 7L

        and: "the successful offer consumed exactly one rate-limit slot — proves the report() path admitted, not dropped"
        (reporter.@rateLimiter.get() & 0xFFFFFFFFL) == 1L

        and: "the degraded fallback line was NOT emitted — confirms report() completed via the primary path, not the catch-Throwable branch"
        !output.contains("libprunus logging failure and failed to report it")

        cleanup:
        System.setErr(originalErr)
    }

    def "subsequent successful offer flushes only the newly accumulated drops"() {
        given: "a fresh reporter with 3 pre-accumulated drops and captured stderr"
        def reporter = new LoggingFailureReporter()
        reporter.@droppedCount.add(3L)
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        when: "the first successful offer flushes the 3 prior drops"
        reporter.offer("first.flush", new RuntimeException("a"))

        and: "4 more drops accumulate and a second successful offer fires"
        reporter.@droppedCount.add(4L)
        reporter.offer("second.flush", new RuntimeException("b"))

        then: "the first flush line reports exactly 3 drops"
        def output = captured.toString()
        output.contains("libprunus: 3 logging failure event(s) dropped (rate-limited)")

        and: "the second flush line reports exactly 4 newly accumulated drops — not 7"
        output.contains("libprunus: 4 logging failure event(s) dropped (rate-limited)")

        and: "no aggregate 7-drop line was written — CAS advancement is incremental, not a re-flush from zero"
        !output.contains("libprunus: 7 logging failure event(s) dropped (rate-limited)")

        and: "the cumulative reported counter has advanced to the full 7"
        reporter.@lastReportedDropCount.get() == 7L

        cleanup:
        System.setErr(originalErr)
    }

    def "offer still emits primary failure line when dropped-flush write throws"() {
        given: "a fresh reporter with 2 pre-accumulated drops and a stderr stream that throws once then captures the rest"
        def reporter = new LoggingFailureReporter()
        reporter.@droppedCount.add(2L)
        def originalErr = System.err
        def writes = new ByteArrayOutputStream()
        def firstWriteFailed = new AtomicBoolean(false)
        def faultingStream = new PrintStream(new OutputStream() {
            @Override
            void write(int b) throws IOException {
                if (firstWriteFailed.compareAndSet(false, true)) {
                    throw new RuntimeException("flush-failure")
                }
                writes.write(b)
            }

            @Override
            void write(byte[] buf, int off, int len) throws IOException {
                if (firstWriteFailed.compareAndSet(false, true)) {
                    throw new RuntimeException("flush-failure")
                }
                writes.write(buf, off, len)
            }
        }, true)
        System.setErr(faultingStream)

        when: "an offer arrives — the dropped-flush write throws (and is swallowed) but the primary report continues"
        reporter.offer("after.flush.failure", new RuntimeException("primary"))

        then: "the primary failure line was still written — the swallowed flush exception did not abort report()"
        def out = writes.toString()
        out.contains("libprunus logging failure at after.flush.failure")

        and: "the lastReportedDropCount was still advanced — the CAS succeeded before the swallowed write attempt"
        reporter.@lastReportedDropCount.get() == 2L

        cleanup:
        System.setErr(originalErr)
    }

    def "reportDroppedIfNeeded rethrows VirtualMachineError raised from System.err writes instead of swallowing it"() {
        given: "a reporter with one pre-accumulated drop and a stderr stream whose writes raise OOM"
        def reporter = new LoggingFailureReporter()
        reporter.@droppedCount.add(1L)
        def originalErr = System.err
        def oomErr = new PrintStream(new OutputStream() {
            @Override
            void write(int b) throws IOException {
                throw new OutOfMemoryError("synthetic for spec")
            }

            @Override
            void write(byte[] buf, int off, int len) throws IOException {
                throw new OutOfMemoryError("synthetic for spec")
            }
        }, true)
        System.setErr(oomErr)

        when:
        reporter.reportDroppedIfNeeded()

        then: "the VME propagates unchanged — the new catch (VirtualMachineError) branch rethrows it instead of letting catch (Throwable) swallow it"
        def ex = thrown(OutOfMemoryError)
        ex.message == "synthetic for spec"

        and: "the CAS still advanced before the throwing write attempt — the failure happened mid-print, not before the bookkeeping"
        reporter.@lastReportedDropCount.get() == 1L

        cleanup:
        System.setErr(originalErr)
    }
}
