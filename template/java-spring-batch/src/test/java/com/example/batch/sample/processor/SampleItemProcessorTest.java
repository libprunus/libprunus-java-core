package com.example.batch.sample.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.batch.sample.model.SampleItem;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.libprunus.core.config.CoreRuntimeConfig;
import org.libprunus.core.log.runtime.LogRuntime;
import org.libprunus.core.log.runtime.LogRuntimeConfig;

class SampleItemProcessorTest {

    private final SampleItemProcessor processor = new SampleItemProcessor();

    @AfterEach
    void reEnableLogging() {
        LogRuntime.linkToDataPlane(new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))));
    }

    @Test
    void uppercasesPayloadAndPreservesId() {
        LogRuntime.linkToDataPlane(new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(true))));

        SampleItem result = processor.process(new SampleItem(1, "alpha"));

        assertEquals(new SampleItem(1, "ALPHA"), result);
    }

    @Test
    void uppercasesPayloadEvenWhenLoggingDisabled() {
        LogRuntime.linkToDataPlane(new AtomicReference<>(new CoreRuntimeConfig(new LogRuntimeConfig(false))));

        SampleItem result = processor.process(new SampleItem(2, "beta"));

        assertEquals(new SampleItem(2, "BETA"), result);
    }
}
