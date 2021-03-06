package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.tasklet.ClusterDataToBigQueryTasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ClusterDataToBigQueryConfiguration {
  @Bean
  public Tasklet clusterDataToBigQueryTasklet() {
    return new ClusterDataToBigQueryTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "clusterDataToBigQueryJob")
  public Job clusterDataToBigQueryJob(JobBuilderFactory jobBuilderFactory, Step clusterDataToBigQueryStep) {
    return jobBuilderFactory.get(BatchJobType.CLUSTER_DATA_TO_BIG_QUERY.name())
        .incrementer(new RunIdIncrementer())
        .start(clusterDataToBigQueryStep)
        .build();
  }

  @Bean
  public Step clusterDataToBigQueryStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("clusterDataToBigQueryStep").tasklet(clusterDataToBigQueryTasklet()).build();
  }
}
