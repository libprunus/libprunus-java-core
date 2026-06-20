package org.libprunus.core.plugin.aot.log;

import java.util.List;
import net.bytebuddy.description.method.MethodDescription;
import org.jspecify.annotations.Nullable;
import org.libprunus.core.log.runtime.LogLevel;

final class ClassPlanAssembler {

    private ClassPlanAssembler() {
        throw new UnsupportedOperationException();
    }

    static MethodPlan assembleFromRouteGraph(
            MethodDescription method,
            MethodNode methodNode,
            LogLevel effectiveEnterLevel,
            LogLevel effectiveExitLevel) {
        List<Family> parameterFamilies = methodNode.parameterFamilies();
        int paramCount = parameterFamilies.size();
        int bitsetLength = (paramCount + Long.SIZE - 1) >>> 6;
        long[] ignoredParamMask = new long[bitsetLength];
        long[] maskedParamMask = new long[bitsetLength];
        for (int i = 0; i < paramCount; i++) {
            Family family = parameterFamilies.get(i);
            if (family == Family.SUPPRESS) {
                ignoredParamMask[i >>> 6] |= (1L << (i & 63));
            } else if (family == Family.MASK) {
                maskedParamMask[i >>> 6] |= (1L << (i & 63));
            }
        }
        Family returnFamily = methodNode.returnFamily();
        boolean returnIgnored = returnFamily == Family.SUPPRESS;
        boolean returnMasked = returnFamily == Family.MASK;
        MethodKey key = new MethodKey(
                method.getDeclaringType().asErasure().getInternalName(),
                method.getInternalName(),
                method.getDescriptor());
        return new MethodPlan(
                key,
                ignoredParamMask,
                maskedParamMask,
                returnMasked,
                returnIgnored,
                effectiveEnterLevel,
                effectiveExitLevel);
    }

    record MethodKey(String ownerInternalName, String methodName, String methodDescriptor) {}

    record MethodPlan(
            MethodKey methodKey,
            long @Nullable [] ignoredParamMask,
            long @Nullable [] maskedParamMask,
            boolean returnMasked,
            boolean returnIgnored,
            LogLevel effectiveEnterLevel,
            LogLevel effectiveExitLevel) {
        public MethodPlan {
            ignoredParamMask = ignoredParamMask != null ? ignoredParamMask.clone() : null;
            maskedParamMask = maskedParamMask != null ? maskedParamMask.clone() : null;
        }

        public long @Nullable [] ignoredParamMask() {
            return ignoredParamMask != null ? ignoredParamMask.clone() : null;
        }

        public long @Nullable [] maskedParamMask() {
            return maskedParamMask != null ? maskedParamMask.clone() : null;
        }

        boolean isParamIgnored(int index) {
            if (ignoredParamMask == null) return false;
            int segment = index >>> 6;
            return segment < ignoredParamMask.length && (ignoredParamMask[segment] & (1L << (index & 63))) != 0;
        }

        boolean isParamMasked(int index) {
            if (maskedParamMask == null) return false;
            int segment = index >>> 6;
            return segment < maskedParamMask.length && (maskedParamMask[segment] & (1L << (index & 63))) != 0;
        }
    }
}
