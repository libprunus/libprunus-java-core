package org.libprunus.core.log.runtime

import java.util.ConcurrentModificationException
import org.libprunus.core.log.runtime.fixture.CollectionRendererFixtures
import spock.lang.Specification

/**
 * End-to-end coverage for collection rendering through the public StringBuilderWithContext.render
 * facade — exercising the full dispatch chain (renderer cache → CollectionRenderer →
 * forceAppendAuditMarker) rather than calling CollectionRenderer.INSTANCE directly. The unit-level
 * CollectionRendererSpec covers the same logical branches by direct invocation; these integration
 * cases pin the facade-level contracts so future refactors of the dispatch table cannot silently
 * bypass CollectionRenderer's CME / MAX_DEPTH safeguards.
 */
class CollectionRendererPublicFacadeIntegrationSpec extends Specification {

    def setup() {
        LogRuntimeTestSupport.resetBinding()
    }

    def "render via the public facade renders a collection that throws ConcurrentModificationException with the audit marker and never appends the closing bracket"() {
        given: "unlimited-budget context plus CME-throwing list"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        def cmeList = CollectionRendererFixtures.listWithIteratorYieldingThenThrowing(
                new ConcurrentModificationException("cme"), 3)

        when:
        context.render(cmeList as Object)

        then:
        noExceptionThrown()
        builder.toString().endsWith("[CME]")
        !builder.toString().endsWith("]]")
    }

    def "render via the public facade rethrows OutOfMemoryError from a CollectionRenderer iterator and leaves the context reusable after reset for a subsequent clean render"() {
        given: "an unlimited-budget context plus a list whose iterator yields one element and then throws OOM"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        def oom = new OutOfMemoryError("simulated oom from iterator")
        def oomList = CollectionRendererFixtures.listWithIteratorYieldingThenThrowing(oom, 3)

        when:
        context.render(oomList as Object)

        then: "the exact OOM instance propagates from the facade — proving the facade did not absorb a fatal Error"
        def ex = thrown(OutOfMemoryError)
        ex.is(oom)

        and: "the facade's finally still balanced the renderer's exitRenderDepth — verified by the reusable-after-reset path below"

        when: "the context is explicitly reset and reused for a clean render of an unrelated collection through the same facade"
        context.reset(Integer.MAX_VALUE)
        context.render(["recovered"] as Object)

        then: "the second render produces fully bracketed output — proving the OOM rethrow left no residual depth or truncation state once reset cleared the context"
        builder.toString() == "[recovered]"
        !context.isTruncated()
    }

    def "render via the public facade absorbs a CME, latches truncated state, and after explicit reset cleanly renders a subsequent unrelated collection"() {
        given: "an unlimited-budget context plus a CME-throwing list"
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)
        def cmeList = CollectionRendererFixtures.listWithIteratorYieldingThenThrowing(
                new ConcurrentModificationException("cme"), 3)

        when:
        context.render(cmeList as Object)

        then: "the CME is absorbed via the audit-marker path and the context is latched truncated"
        noExceptionThrown()
        builder.toString().endsWith("[CME]")
        context.isTruncated()

        when: "the context is explicitly reset and reused for a clean render of an unrelated collection through the same facade"
        context.reset(Integer.MAX_VALUE)
        context.render(["recovered"] as Object)

        then: "the second render produces fully bracketed output — proving the CME absorption left no residual depth state once reset cleared the context"
        builder.toString() == "[recovered]"
        !context.isTruncated()
    }

    def "render via the public facade absorbs an infinitely self-referential list via MAX_RENDER_DEPTH guard without StackOverflowError"() {
        given: "self-referential list and unlimited-budget context"
        def self = []
        self.add(self)
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(Integer.MAX_VALUE)

        when:
        context.render(self as Object)

        then:
        noExceptionThrown()
        builder.toString().contains("MAX_DEPTH")
        context.isTruncated()

        and: "no trailing ']]' is silently appended after the MAX_DEPTH marker latched truncated — mirrors the symmetric negative used on the CME / fallback paths"
        !builder.toString().endsWith("]]")
    }
}
