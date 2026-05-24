package org.libprunus.core.plugin.aot.log;

import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.jar.asm.Type;

final class MethodEnterParameterPlanConsumer {

    private MethodEnterParameterPlanConsumer() {
        throw new UnsupportedOperationException();
    }

    static List<EnterParamPlan> consume(MethodDescription method, ClassPlanAssembler.MethodPlan methodPlan) {
        List<EnterParamPlan> params = new ArrayList<>();
        int slot = 1;
        int parameterIndex = 0;
        for (ParameterDescription param : method.getParameters()) {
            Type paramType = Type.getType(param.getType().asErasure().getDescriptor());
            int paramSlot = slot;
            slot += paramType.getSize();
            if (methodPlan.isParamIgnored(parameterIndex)) {
                parameterIndex++;
                continue;
            }
            boolean masked = methodPlan.isParamMasked(parameterIndex);
            params.add(new EnterParamPlan(
                    AotMethodLoggingTransformer.sanitizeForRecipe(param.getName()), paramType, paramSlot, masked));
            parameterIndex++;
        }
        return params;
    }

    record EnterParamPlan(String name, Type type, int syntheticSlot, boolean masked) {}
}
