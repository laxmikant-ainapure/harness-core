package io.harness;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;

import io.harness.annotation.HarnessRepo;
import io.harness.beans.converters.SweepingOutputReadMongoConverter;
import io.harness.beans.converters.SweepingOutputWriteMongoConverter;
import io.harness.orchestration.persistence.OrchestrationBasePersistenceConfig;
import io.harness.spring.AliasRegistrar;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.List;
import java.util.Set;

@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.engine"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "orchestrationMongoTemplate")
public class OrchestrationPersistenceConfig extends OrchestrationBasePersistenceConfig {
  private static final List<Class<? extends Converter>> converters =
      ImmutableList.of(SweepingOutputReadMongoConverter.class, SweepingOutputWriteMongoConverter.class);

  @Inject
  public OrchestrationPersistenceConfig(Injector injector, Set<Class<? extends AliasRegistrar>> aliasRegistrars) {
    super(injector, aliasRegistrars, converters);
  }
}