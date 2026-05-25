package org.libprunus.core.log.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Specification

class StringBuilderPoolPerThreadIsolationIntegrationSpec extends Specification {

    def "two platform threads maintain independent pool stacks so an oversized release on one does not affect the other's reuse"() {
        given: "reset the per-thread pool from the test thread; spawn two platform threads that each touch their own per-thread PoolState"
        StringBuilderPool.@POOL.remove()
        def threadAReused = new AtomicBoolean(false)
        def threadAOwn = new AtomicReference<StringBuilderWithContext>()
        def threadBOwn = new AtomicReference<StringBuilderWithContext>()
        def aDone = new CountDownLatch(1)

        when:
        def threadB = Thread.ofPlatform().name("sbp-iso-B").start {
            def own = StringBuilderPool.acquire()
            own.builder.ensureCapacity(8193)
            threadBOwn.set(own)
            StringBuilderPool.release(own)
            aDone.await(5, TimeUnit.SECONDS)
        }
        def threadA = Thread.ofPlatform().name("sbp-iso-A").start {
            def own = StringBuilderPool.acquire()
            threadAOwn.set(own)
            StringBuilderPool.release(own)
            def reacq = StringBuilderPool.acquire()
            threadAReused.set(reacq.is(own))
            aDone.countDown()
        }
        threadA.join()
        threadB.join()

        then: "thread A's pool was unaffected by thread B's oversized rejection — A successfully reused its own context"
        threadAReused.get()

        and: "thread A and thread B operated on physically distinct context instances — confirming the ThreadLocal isolation"
        !threadAOwn.get().is(threadBOwn.get())

        cleanup:
        StringBuilderPool.@POOL.remove()
    }
}
