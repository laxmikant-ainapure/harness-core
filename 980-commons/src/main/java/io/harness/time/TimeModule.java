package io.harness.time;

import io.harness.threading.ExecutorModule;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;

public class TimeModule extends AbstractModule {
  private static volatile TimeModule instance;

  public static TimeModule getInstance() {
    if (instance == null) {
      instance = new TimeModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(ExecutorModule.getInstance());
  }

  @Provides
  @Singleton
  public TimeLimiter timeLimiter(ExecutorService executorService) {
    return new SimpleTimeLimiter(executorService);
  }
}
