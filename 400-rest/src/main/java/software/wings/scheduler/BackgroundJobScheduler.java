package software.wings.scheduler;

import software.wings.app.MainConfiguration;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class BackgroundJobScheduler extends JobScheduler {
  @Inject
  public BackgroundJobScheduler(Injector injector, MainConfiguration configuration) {
    super(injector, configuration.getBackgroundSchedulerConfig(), configuration.getMongoConnectionFactory().getUri());
  }
}
