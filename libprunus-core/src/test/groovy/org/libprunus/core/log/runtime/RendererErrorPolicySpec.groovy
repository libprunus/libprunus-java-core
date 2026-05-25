package org.libprunus.core.log.runtime

import spock.lang.Specification

class RendererErrorPolicySpec extends Specification {

    private static StringBuilderWithContext freshContext() {
        def builder = new StringBuilder()
        def context = new StringBuilderWithContext(builder)
        context.setMaxMessageLength(256)
        context
    }

    def "handleRenderError rethrows non-StackOverflowError Error subclasses unchanged so fail-fast escalation is preserved across all renderer-family catch blocks"() {
        given:
        def context = freshContext()

        when:
        StringBuilderWithContext.handleRenderError(context, fatalError)

        then: "the same Error instance escapes the helper untouched, identity-preserved for the caller's finally block to observe"
        def thrown = thrown(Error)
        thrown.is(fatalError)

        and: "no fallback marker was written into the builder — Error escalation skipped the appendThrowableFallback branch"
        context.toString().isEmpty()

        where:
        fatalError << [
                new OutOfMemoryError("oom-probe"),
                new InternalError("internal-probe"),
                new AssertionError("assert-probe"),
                new LinkageError("linkage-probe"),
        ]
    }

    def "handleRenderError routes StackOverflowError into the fallback marker bucket so logging viability survives recursive blowups"() {
        given:
        def context = freshContext()
        def soe = new StackOverflowError("soe-probe")

        when:
        StringBuilderWithContext.handleRenderError(context, soe)

        then: "no Error is rethrown — SOE is the only Error subclass explicitly absorbed by this helper"
        noExceptionThrown()

        and: "the audit marker latch fired with the SOE marker — appendThrowableFallback dispatched to forceAppendAuditMarker(STACK_OVERFLOW_MARKER)"
        context.toString() == StringBuilderWithContext.STACK_OVERFLOW_MARKER
        context.isTruncated()
    }

    def "handleRenderError routes RuntimeException into the fallback marker bucket so checked / unchecked user exceptions become observable fallback text"() {
        given:
        def context = freshContext()
        def runtimeFailure = new IllegalArgumentException("user-supplied iterator failure")

        when:
        StringBuilderWithContext.handleRenderError(context, runtimeFailure)

        then: "no exception escapes — RuntimeException always routes to appendThrowableFallback"
        noExceptionThrown()

        and: "the fallback marker carries the throwable's FQCN so downstream logs identify the offending class"
        context.toString() == "...[java.lang.IllegalArgumentException]"
    }

    def "handleRenderError performs no cleanup on the context so the caller's finally block remains responsible for exitRenderDepth and pool release"() {
        given:
        def context = freshContext()
        context.enterRenderDepth()
        int depthBeforeHelper = context.@renderDepth

        when:
        StringBuilderWithContext.handleRenderError(context, new IllegalStateException("inside-render"))

        then: "depth counter is untouched — handleRenderError did not call exitRenderDepth itself"
        context.@renderDepth == depthBeforeHelper

        and: "fallback marker landed for the caller to observe afterwards"
        context.toString() == "...[java.lang.IllegalStateException]"
    }
}
