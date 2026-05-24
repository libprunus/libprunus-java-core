package org.libprunus.core.log.runtime;

final class LoggableRenderer implements TypeRenderer {

    static final LoggableRenderer INSTANCE = new LoggableRenderer();

    private LoggableRenderer() {}

    @Override
    public void render(StringBuilderWithContext context, Object value) {
        if (context.isTruncated()) return;
        if (!context.enterRenderDepth()) return;
        try {
            ((Loggable) value)._libprunus_render(context);
        } catch (Throwable throwable) {
            StringBuilderWithContext.handleRenderError(context, throwable);
        } finally {
            context.exitRenderDepth();
        }
    }
}
