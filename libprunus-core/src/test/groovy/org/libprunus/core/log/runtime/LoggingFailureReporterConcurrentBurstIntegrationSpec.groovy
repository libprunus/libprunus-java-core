package org.libprunus.core.log.runtime

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import spock.lang.Specification

class LoggingFailureReporterConcurrentBurstIntegrationSpec extends Specification {

    def "concurrent burst keeps detailed output capped and records dropped events"() {
        given: "a fresh reporter instance and captured stderr"
        def reporter = new LoggingFailureReporter()
        def originalErr = System.err
        def captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured, true))

        and: "a concurrent burst setup"
        int threads = 16
        int perThread = 20
        def start = new CountDownLatch(1)
        def done = new CountDownLatch(threads)
        def pool = Executors.newFixedThreadPool(threads)

        when:
        (1..threads).each { t ->
            pool.submit {
                start.await(2, TimeUnit.SECONDS)
                (1..perThread).each { i ->
                    reporter.offer("thread-${t}.event-${i}", new RuntimeException("err-${t}-${i}"))
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await(5, TimeUnit.SECONDS)

        then:
        def output = captured.toString()
        def detailedCount = output.count("libprunus logging failure at")
        def droppedSum = reporter.@droppedCount.sum()
        detailedCount >= 1
        detailedCount <= 10
        droppedSum >= (threads * perThread - 10)
        detailedCount + droppedSum == threads * perThread

        cleanup:
        pool?.shutdownNow()
        System.setErr(originalErr)
    }
}
