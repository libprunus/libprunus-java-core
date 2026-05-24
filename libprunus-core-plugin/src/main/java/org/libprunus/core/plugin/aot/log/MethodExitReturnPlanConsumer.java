package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.jar.asm.Type;

final class MethodExitReturnPlanConsumer {

    private MethodExitReturnPlanConsumer() {
        throw new UnsupportedOperationException();
    }

    static ExitReturnPlan consume(MethodDescription method, ClassPlanAssembler.MethodPlan plan) {
        if (plan.returnIgnored()) {
            return new ExitReturnPlan(Type.VOID_TYPE, false);
        }
        Type returnType = Type.getReturnType(method.getDescriptor());
        return new ExitReturnPlan(returnType, plan.returnMasked());
    }

    record ExitReturnPlan(Type returnType, boolean masked) {}
}
