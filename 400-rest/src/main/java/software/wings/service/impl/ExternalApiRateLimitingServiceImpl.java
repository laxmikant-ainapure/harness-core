package software.wings.service.impl;

import software.wings.service.intfc.ExternalApiRateLimitingService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;

@Singleton
@ValidateOnExecution
public class ExternalApiRateLimitingServiceImpl implements ExternalApiRateLimitingService {
  private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(2);
  // TODO this needs to be removed when generic rate limiting is implemented.
  private static final double MAX_QPM_PER_MANAGER = 50;
  private static final LoadingCache<String, RateLimiter> limiters =
      CacheBuilder.newBuilder()
          .expireAfterAccess(CACHE_EXPIRATION.toMillis(), TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, RateLimiter>() {
            @Override
            public RateLimiter load(String key) {
              return RateLimiter.create(MAX_QPM_PER_MANAGER / 60);
            }
          });

  @Override
  public boolean rateLimitRequest(String key) {
    return !limiters.getUnchecked(key).tryAcquire();
  }

  @Override
  public double getMaxQPMPerManager() {
    return MAX_QPM_PER_MANAGER;
  }
}
