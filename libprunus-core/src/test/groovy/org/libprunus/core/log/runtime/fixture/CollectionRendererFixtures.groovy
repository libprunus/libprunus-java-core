package org.libprunus.core.log.runtime.fixture

final class CollectionRendererFixtures {

    static AbstractList<String> listWithIteratorYieldingThenThrowing(
            Throwable error, int totalSize, String element = "item") {
        new AbstractList<String>() {
            @Override
            String get(int index) { return element }
            @Override
            int size() { return totalSize }
            @Override
            Iterator<String> iterator() {
                int count = 0
                new Iterator<String>() {
                    @Override
                    boolean hasNext() { return count < totalSize }
                    @Override
                    String next() {
                        if (count++ > 0) throw error
                        return element
                    }
                }
            }
        }
    }

    private CollectionRendererFixtures() {
        throw new UnsupportedOperationException()
    }
}
