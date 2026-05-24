package org.libprunus.core.plugin.aot.log;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import org.libprunus.core.plugin.aot.PrunusPluginConstants;

final class FamilyDetector {

    private FamilyDetector() {
        throw new UnsupportedOperationException();
    }

    static Family detect(AnnotationList annotations, String targetDescriptor) {
        if (annotations == null) {
            return Family.NONE;
        }
        Family found = Family.NONE;
        String foundName = null;
        for (AnnotationDescription annotation : annotations) {
            String name = annotation.getAnnotationType().getName();
            Family candidate = familyOf(name);
            if (candidate == Family.NONE) {
                continue;
            }
            if (found != Family.NONE) {
                throw new IllegalStateException("@Sensitive / @DoNotLog / @DoLog are mutually exclusive on "
                        + targetDescriptor + " (found " + foundName + " and " + name + ")");
            }
            found = candidate;
            foundName = name;
        }
        return found;
    }

    static boolean hasAnyFamily(AnnotationList annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationDescription annotation : annotations) {
            if (familyOf(annotation.getAnnotationType().getName()) != Family.NONE) {
                return true;
            }
        }
        return false;
    }

    private static Family familyOf(String annotationName) {
        if (PrunusPluginConstants.SENSITIVE_ANNOTATION_BINARY_NAME.equals(annotationName)) {
            return Family.MASK;
        }
        if (PrunusPluginConstants.DO_NOT_LOG_ANNOTATION_BINARY_NAME.equals(annotationName)) {
            return Family.SUPPRESS;
        }
        if (PrunusPluginConstants.DO_LOG_ANNOTATION_BINARY_NAME.equals(annotationName)) {
            return Family.PASS_THROUGH;
        }
        return Family.NONE;
    }
}
