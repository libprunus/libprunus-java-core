package org.libprunus.core.plugin.aot

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.ToIntFunction
import net.bytebuddy.dynamic.ClassFileLocator
import spock.lang.Specification
import spock.lang.TempDir

class AotCompileContextSpec extends Specification {

    @TempDir
    File tempDir

    def "computeMaskIfAbsent invokes loader once on cache miss and reuses cached value on subsequent calls"() {
        given:
        def context = new AotCompileContext()
        def callCount = new AtomicInteger(0)
        ToIntFunction<String> loader = { key ->
            callCount.incrementAndGet()
            13
        }

        when:
        def first = context.computeMaskIfAbsent("sample.demo.MaskedService", loader)
        def second = context.computeMaskIfAbsent("sample.demo.MaskedService", loader)

        then:
        first == 13
        second == 13
        callCount.get() == 1
    }

    def "computeMaskIfAbsent bypasses loader when value is already cached"() {
        given:
        def context = new AotCompileContext()
        def callCount = new AtomicInteger(0)
        context.@matchedPluginMasks.put("sample.demo.MaskedService", 99)
        ToIntFunction<String> loader = { key ->
            callCount.incrementAndGet()
            13
        }

        when:
        def result = context.computeMaskIfAbsent("sample.demo.MaskedService", loader)

        then:
        result == 99
        callCount.get() == 0
        context.peekMask("sample.demo.MaskedService") == 99
    }

    def "computeMaskIfAbsent stores independent values for different classes"() {
        given:
        def context = new AotCompileContext()
        def firstCallCount = new AtomicInteger(0)
        def secondCallCount = new AtomicInteger(0)
        ToIntFunction<String> firstLoader = { k ->
            firstCallCount.incrementAndGet()
            3
        }
        ToIntFunction<String> secondLoader = { k ->
            secondCallCount.incrementAndGet()
            5
        }

        when:
        def first = context.computeMaskIfAbsent("sample.demo.FirstService", firstLoader)
        def second = context.computeMaskIfAbsent("sample.demo.SecondService", secondLoader)

        then:
        first == 3
        second == 5
        firstCallCount.get() == 1
        secondCallCount.get() == 1
        context.peekMask("sample.demo.FirstService") == 3
        context.peekMask("sample.demo.SecondService") == 5
    }

    def "computeMaskIfAbsent is computed exactly once under concurrent access for the same class"() {
        given:
        def context = new AotCompileContext()
        def callCount = new AtomicInteger(0)
        def latch = new CountDownLatch(1)
        ToIntFunction<String> loader = { key ->
            callCount.incrementAndGet()
            7
        }

        when:
        def futures = (0..<32).collect {
            CompletableFuture.supplyAsync({
                latch.await()
                context.computeMaskIfAbsent("sample.demo.RacingService", loader)
            })
        }
        latch.countDown()
        def results = futures.collect { it.join() }

        then:
        results.every { it == 7 }
        callCount.get() == 1
        context.peekMask("sample.demo.RacingService") == 7
    }

    def "computeMaskIfAbsent rethrows loader failure and leaves cache unpoisoned for retry"() {
        given:
        def context = new AotCompileContext()
        def callCount = new AtomicInteger(0)
        ToIntFunction<String> loader = { key ->
            callCount.incrementAndGet()
            throw new IllegalStateException("loader-failure")
        }

        when:
        context.computeMaskIfAbsent("sample.demo.FailedMask", loader)

        then:
        def firstEx = thrown(IllegalStateException)
        firstEx.message == "loader-failure"
        callCount.get() == 1
        context.peekMask("sample.demo.FailedMask") == -1
        context.@matchedPluginMasks.isEmpty()

        when:
        context.computeMaskIfAbsent("sample.demo.FailedMask", loader)

        then:
        def secondEx = thrown(IllegalStateException)
        secondEx.message == "loader-failure"
        callCount.get() == 2
        context.peekMask("sample.demo.FailedMask") == -1
        context.@matchedPluginMasks.isEmpty()
    }

    def "peekMask returns NO_CACHED_MASK sentinel when name was never computed"() {
        given:
        def context = new AotCompileContext()

        expect:
        AotCompileContext.isMissingMask(context.peekMask("sample.demo.NeverComputed"))
    }

    def "peekMask returns cached value after computeMaskIfAbsent miss"() {
        given:
        def context = new AotCompileContext()
        context.computeMaskIfAbsent("sample.demo.Seeded", { k -> 5 } as ToIntFunction)

        expect:
        context.peekMask("sample.demo.Seeded") == 5
        !AotCompileContext.isMissingMask(context.peekMask("sample.demo.Seeded"))
        context.peekMask("sample.demo.UnseededOther") == -1
        AotCompileContext.isMissingMask(context.peekMask("sample.demo.UnseededOther"))
    }

    def "isMissingMask returns true only for sentinel -1"() {
        expect:
        AotCompileContext.isMissingMask(mask) == expected

        where:
        mask              || expected
        -1                || true
        0                 || false
        1                 || false
        Integer.MAX_VALUE || false
        Integer.MIN_VALUE || false
    }

    def "sharedTypePool reuses instance for the same locator"() {
        given:
        def context = new AotCompileContext()
        def locator = new ClassFileLocator.ForFolder(tempDir)

        when:
        def first = context.sharedTypePool(locator)
        def second = context.sharedTypePool(locator)

        then:
        first.is(second)
    }

    def "sharedTypePool keeps pools isolated across different locators"() {
        given:
        def context = new AotCompileContext()
        def one = new File(tempDir, "pool-one")
        def two = new File(tempDir, "pool-two")
        one.mkdirs()
        two.mkdirs()
        def firstLocator = new ClassFileLocator.ForFolder(one)
        def secondLocator = new ClassFileLocator.ForFolder(two)

        when:
        def first = context.sharedTypePool(firstLocator)
        def second = context.sharedTypePool(secondLocator)

        then:
        !first.is(second)
    }

    def "sharedTypePool keeps one lazy slot per locator and returns identical pool under concurrent access"() {
        given:
        def context = new AotCompileContext()
        def locator = new ClassFileLocator.ForFolder(tempDir)

        when:
        def futures = (0..<32).collect {
            CompletableFuture.supplyAsync({ context.sharedTypePool(locator) })
        }
        def pools = futures.collect { it.join() }

        then:
        pools.unique { System.identityHashCode(it) }.size() == 1
        context.@typePoolsByLocator.size() == 1
        context.@typePoolsByLocator.containsKey(locator)
    }

    def "clear empties both mask cache and shared typePool cache"() {
        given:
        def context = new AotCompileContext()
        def locator = new ClassFileLocator.ForFolder(tempDir)
        context.computeMaskIfAbsent("sample.demo.Cached", { k -> 42 } as ToIntFunction)
        context.sharedTypePool(locator)
        assert !context.@matchedPluginMasks.isEmpty()
        assert !context.@typePoolsByLocator.isEmpty()

        when:
        context.clear()

        then:
        context.@matchedPluginMasks.isEmpty()
        context.@typePoolsByLocator.isEmpty()
        context.peekMask("sample.demo.Cached") == -1
        AotCompileContext.isMissingMask(context.peekMask("sample.demo.Cached"))
    }
}
