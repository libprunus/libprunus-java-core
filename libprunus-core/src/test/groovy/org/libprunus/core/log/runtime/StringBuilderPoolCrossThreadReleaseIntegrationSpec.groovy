package org.libprunus.core.log.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Specification

class StringBuilderPoolCrossThreadReleaseIntegrationSpec extends Specification {

    def "cross-thread release can be reused by the releasing thread"() {
        given:
        StringBuilderPool.@POOL.remove()
        def sharedBuilder = new AtomicReference<StringBuilderWithContext>()
        def consumerAcquire = new AtomicReference<StringBuilderWithContext>()
        def producerAcquire = new AtomicReference<StringBuilderWithContext>()
        def producerReady = new CountDownLatch(1)
        def consumerDone = new CountDownLatch(1)

        def producer = Thread.ofPlatform().name("sbp-producer").start {
            def sb = StringBuilderPool.acquire()
            sb.builder.append("payload")
            sharedBuilder.set(sb)
            producerReady.countDown()
            consumerDone.await(5, TimeUnit.SECONDS)
            StringBuilderPool.release(sb)
            producerAcquire.set(StringBuilderPool.acquire())
        }

        def consumer = Thread.ofPlatform().name("sbp-consumer").start {
            producerReady.await(5, TimeUnit.SECONDS)
            StringBuilderPool.release(sharedBuilder.get())
            consumerAcquire.set(StringBuilderPool.acquire())
            consumerDone.countDown()
        }

        when:
        producer.join()
        consumer.join()

        then:
        consumerAcquire.get().is(sharedBuilder.get())
        producerAcquire.get().is(sharedBuilder.get())
        consumerAcquire.get().builder.length() == 0
        producerAcquire.get().builder.length() == 0

        cleanup:
        if (consumerAcquire.get() != null) {
            StringBuilderPool.release(consumerAcquire.get())
        }
        if (producerAcquire.get() != null) {
            StringBuilderPool.release(producerAcquire.get())
        }
        StringBuilderPool.@POOL.remove()
    }

    def "two threads can exchange builders through cross-thread release"() {
        given:
        StringBuilderPool.@POOL.remove()
        def producerOwned = new AtomicReference<StringBuilderWithContext>()
        def consumerOwned = new AtomicReference<StringBuilderWithContext>()
        def producerAfterExchange = new AtomicReference<StringBuilderWithContext>()
        def consumerAfterExchange = new AtomicReference<StringBuilderWithContext>()
        def producerReady = new CountDownLatch(1)
        def consumerReady = new CountDownLatch(1)

        def producer = Thread.ofPlatform().name("sbp-exchange-producer").start {
            def own = StringBuilderPool.acquire()
            producerOwned.set(own)
            producerReady.countDown()
            consumerReady.await(5, TimeUnit.SECONDS)
            StringBuilderPool.release(consumerOwned.get())
            producerAfterExchange.set(StringBuilderPool.acquire())
        }

        def consumer = Thread.ofPlatform().name("sbp-exchange-consumer").start {
            def own = StringBuilderPool.acquire()
            consumerOwned.set(own)
            consumerReady.countDown()
            producerReady.await(5, TimeUnit.SECONDS)
            StringBuilderPool.release(producerOwned.get())
            consumerAfterExchange.set(StringBuilderPool.acquire())
        }

        when:
        producer.join()
        consumer.join()

        then:
        producerAfterExchange.get().is(consumerOwned.get())
        consumerAfterExchange.get().is(producerOwned.get())
        !producerAfterExchange.get().is(producerOwned.get())
        !consumerAfterExchange.get().is(consumerOwned.get())

        cleanup:
        if (producerAfterExchange.get() != null) {
            StringBuilderPool.release(producerAfterExchange.get())
        }
        if (consumerAfterExchange.get() != null) {
            StringBuilderPool.release(consumerAfterExchange.get())
        }
        StringBuilderPool.@POOL.remove()
    }
}
