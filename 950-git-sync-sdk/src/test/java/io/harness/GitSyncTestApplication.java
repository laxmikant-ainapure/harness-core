package io.harness;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.maintenance.MaintenanceController;
import io.harness.persistence.HPersistence;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Slf4j
public class GitSyncTestApplication extends Application<GitSyncTestConfiguration> {
  private static final String APPLICATION_NAME = "Git Sync Application";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new GitSyncTestApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<GitSyncTestConfiguration> bootstrap) {
    initializeLogging();
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
  }

  @Override
  public void run(GitSyncTestConfiguration config, Environment environment) {
    log.info("Starting Git Sync Application ...");
    MaintenanceController.forceMaintenance(true);
    Injector injector = Guice.createInjector(new GitSyncTestModule(config));

    final HPersistence instance = injector.getInstance(HPersistence.class);
    registerJerseyProviders(environment, injector);

    MaintenanceController.forceMaintenance(false);
    final Tester instance1 = injector.getInstance(Tester.class);
    instance1.test();
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(JsonProcessingExceptionMapper.class);
    environment.jersey().register(EarlyEofExceptionMapper.class);

    environment.jersey().register(MultiPartFeature.class);
  }
}