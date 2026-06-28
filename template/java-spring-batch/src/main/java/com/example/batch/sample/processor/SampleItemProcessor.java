package com.example.batch.sample.processor;

import com.example.batch.sample.model.SampleItem;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class SampleItemProcessor implements ItemProcessor<SampleItem, SampleItem> {

    @Override
    public @Nullable SampleItem process(SampleItem item) {
        return new SampleItem(item.id(), item.payload().toUpperCase(Locale.ROOT));
    }
}
