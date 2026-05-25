package org.libprunus.core.log.runtime

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import spock.lang.Specification

class LoggingFailureReporterMultiSecondBurstIntegrationSpec extends Specification {

    def "detailed output cap renews when wall-clock crosses a second boundary under concurrent load"() {
        given: "a fresh reporter and captured stderr"
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        and: "two consecutive bursts of concurrent offers, each tagged with its burst label"
        int threads = 8
        int perBurst = 30
        def pool = Executors.newFixedThreadPool(threads)
        def runBurst = { String tag ->
            def start = new CountDownLatch(1)
            def done = new CountDownLatch(threads)
            threads.times { t ->
                pool.submit {
                    start.await(2, TimeUnit.SECONDS)
                    perBurst.times { i -> reporter.offer("${tag}.${t}.${i}", new RuntimeException("e")) }
                    done.countDown()
                }
            }
            start.countDown()
            done.await(5, TimeUnit.SECONDS)
        }

        when: "the first burst runs, the wall-clock advances past one second, then the second burst runs"
        runBurst("first")
        Thread.sleep(1_100L)
        runBurst("second")

        then: "the captured stderr contains detailed lines tagged with both burst labels — proving each burst contributed admitted offers"
        def output = captured.toString()
        def firstDetailed = output.findAll(/first\.\d+\.\d+/).size()
        def secondDetailed = output.findAll(/second\.\d+\.\d+/).size()

        firstDetailed >= 1
        firstDetailed <= 10

        and: "the second burst's detailed line count also lies within (1, MAX_DETAILED_PER_SECOND] — the cross-second branch renewed the bucket"
        secondDetailed >= 1
        secondDetailed <= 10

        and: "both bursts together produced at least 2 detailed lines — proving the cap renewed across the second boundary rather than being permanently consumed"
        (firstDetailed + secondDetailed) >= 2

        and: "every offered event was tracked: detailed lines plus dropped counter equals the total offers, modulo a small interleaving slack from concurrent stderr writes that may merge or split lines"
        (reporter.@droppedCount.sum() + firstDetailed + secondDetailed) <= (2L * threads * perBurst)
        (reporter.@droppedCount.sum() + firstDetailed + secondDetailed) >= (2L * threads * perBurst - threads * 4L)

        cleanup:
        pool?.shutdownNow()
        System.setErr(originalErr)
    }
}
