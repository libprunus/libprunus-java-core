package com.example.batch.sample.config;

import com.example.batch.sample.model.SampleItem;
import com.example.batch.sample.processor.SampleItemProcessor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SampleBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(SampleBatchConfig.class);

    @Bean
    public ItemReader<SampleItem> sampleItemReader() {
        return new ListItemReader<>(
                List.of(new SampleItem(1, "alpha"), new SampleItem(2, "beta"), new SampleItem(3, "gamma")));
    }

    @Bean
    public ItemWriter<SampleItem> sampleItemWriter() {
        return chunk -> chunk.forEach(item -> log.info("wrote {}", item));
    }

    @Bean
    public Step sampleStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<SampleItem> sampleItemReader,
            SampleItemProcessor sampleItemProcessor,
            ItemWriter<SampleItem> sampleItemWriter) {
        return new StepBuilder("sampleStep", jobRepository)
                .<SampleItem, SampleItem>chunk(2)
                .transactionManager(transactionManager)
                .reader(sampleItemReader)
                .processor(sampleItemProcessor)
                .writer(sampleItemWriter)
                .build();
    }

    @Bean
    public Job sampleJob(JobRepository jobRepository, Step sampleStep) {
        return new JobBuilder("sampleJob", jobRepository).start(sampleStep).build();
    }
}
