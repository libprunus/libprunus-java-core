package org.libprunus.core.log.runtime;

final class ObjectArrayRenderer implements TypeRenderer {

    static final ObjectArrayRenderer INSTANCE = new ObjectArrayRenderer();

    private ObjectArrayRenderer() {}

    @Override
    public void render(StringBuilderWithContext context, Object value) {
        if (!context.append('[')) return;
        if (!context.enterRenderDepth()) return;
        try {
            Object[] array = (Object[]) value;
            int length = array.length;
            if (length > 0 && context.appendObjectTo(array[0])) {
                for (int index = 1; index < length; index++) {
                    if (!context.prependSeparator() || !context.appendObjectTo(array[index])) {
                        break;
                    }
                }
            }
        } catch (Throwable throwable) {
            StringBuilderWithContext.handleRenderError(context, throwable);
            return;
        } finally {
            context.exitRenderDepth();
        }
        context.append(']');
    }
}
