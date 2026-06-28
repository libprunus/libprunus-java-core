package com.example.batch.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBatchTest
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class SampleJobTest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Test
    void sampleJobProcessesAllThreeItemsAndPersists() throws Exception {
        JobExecution execution = jobOperatorTestUtils.startJob(jobOperatorTestUtils.getUniqueJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertNotNull(execution.getId());
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertEquals(3L, stepExecution.getReadCount());
        assertEquals(3L, stepExecution.getWriteCount());
    }
}
