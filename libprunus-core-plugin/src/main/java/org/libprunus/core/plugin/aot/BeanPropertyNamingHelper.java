package org.libprunus.core.plugin.aot;

final class BeanPropertyNamingHelper {

    private BeanPropertyNamingHelper() {}

    static String toGetterName(String fieldName, boolean isBoolean) {
        String prefix = isBoolean ? "is" : "get";
        return prefix + accessorSuffix(fieldName);
    }

    private static String accessorSuffix(String fieldName) {
        if (fieldName.length() >= 2
                && !Character.isUpperCase(fieldName.charAt(0))
                && Character.isUpperCase(fieldName.charAt(1))) {
            return fieldName;
        }
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
