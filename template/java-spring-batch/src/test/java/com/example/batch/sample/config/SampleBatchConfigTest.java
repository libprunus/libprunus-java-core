package com.example.batch.sample.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.example.batch.sample.model.SampleItem;
import com.example.batch.sample.processor.SampleItemProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.transaction.PlatformTransactionManager;

class SampleBatchConfigTest {

    private final SampleBatchConfig config = new SampleBatchConfig();

    @Test
    void readerProvidesAllThreeSampleItemsInOrderThenNull() throws Exception {
        ItemReader<SampleItem> reader = config.sampleItemReader();

        assertEquals(new SampleItem(1, "alpha"), reader.read());
        assertEquals(new SampleItem(2, "beta"), reader.read());
        assertEquals(new SampleItem(3, "gamma"), reader.read());
        assertNull(reader.read());
    }

    @Test
    void jobWiresSampleStepIntoSampleJob() {
        Step step = config.sampleStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                config.sampleItemReader(),
                new SampleItemProcessor(),
                chunk -> {});
        Job job = config.sampleJob(mock(JobRepository.class), step);

        assertEquals("sampleJob", job.getName());
        assertIterableEquals(List.of("sampleStep"), ((AbstractJob) job).getStepNames());
    }
}
