package org.libprunus.core.plugin.aot.log;

import java.util.List;

record RegistryMetadata(String registryBinaryName, int maxMessageLength, List<String> directToStringWhitelist) {

    RegistryMetadata {
        directToStringWhitelist = List.copyOf(directToStringWhitelist);
    }
}
