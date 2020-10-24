package io.harness.ng.core;

import com.google.inject.Inject;
import com.google.inject.Injector;

import io.harness.annotation.HarnessRepo;
import io.harness.springdata.SpringPersistenceConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Collections;

@Configuration
@EnableMongoRepositories(
    basePackages = {"io.harness.ng.core"}, includeFilters = @ComponentScan.Filter(HarnessRepo.class))
public class NGCorePersistenceConfig extends SpringPersistenceConfig {
  @Inject
  public NGCorePersistenceConfig(Injector injector) {
    super(injector, Collections.emptyList());
  }
}
