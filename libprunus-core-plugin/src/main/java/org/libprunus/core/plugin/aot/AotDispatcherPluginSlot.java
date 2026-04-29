package org.libprunus.core.plugin.aot;

import net.bytebuddy.build.Plugin;
import org.libprunus.core.plugin.aot.log.AotLogByteBuddyPlugin;

enum AotDispatcherPluginSlot {
    LOG(0, AotLogByteBuddyPlugin.class) {
        @Override
        Plugin createPlugin(AotPluginArguments arguments, AotCompileContext context) {
            return new AotLogByteBuddyPlugin(arguments, context);
        }
    };

    private final int bitIndex;
    private final Class<? extends Plugin> pluginType;

    AotDispatcherPluginSlot(int bitIndex, Class<? extends Plugin> pluginType) {
        this.bitIndex = bitIndex;
        this.pluginType = pluginType;
    }

    int bitMask() {
        return 1 << bitIndex;
    }

    boolean supports(Plugin plugin) {
        return pluginType.isInstance(plugin);
    }

    Class<? extends Plugin> pluginType() {
        return pluginType;
    }

    abstract Plugin createPlugin(AotPluginArguments arguments, AotCompileContext context);
}
