package io.harness.limits;

import io.harness.limits.checker.MongoStaticLimitChecker;
import io.harness.limits.checker.rate.MongoSlidingWindowRateLimitChecker;
import io.harness.limits.configuration.InvalidLimitConfigurationException;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.configuration.NoLimitConfiguredException;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.limits.lib.Limit;
import io.harness.limits.lib.LimitChecker;
import io.harness.limits.lib.LimitType;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@Singleton
@ParametersAreNonnullByDefault
public class LimitCheckerFactoryImpl implements LimitCheckerFactory {
  @Inject private LimitConfigurationService configuredLimitService;
  @Inject private WingsPersistence wingsPersistence;

  // TODO: add cache once Redis infra is in place. See PR #3150 for relevant discussion
  @Override
  @Nonnull
  public LimitChecker getInstance(Action action) {
    ConfiguredLimit configuredLimit =
        configuredLimitService.getOrDefault(action.getAccountId(), action.getActionType());

    if (null == configuredLimit) {
      throw new NoLimitConfiguredException(action);
    }

    validate(configuredLimit, action);

    Limit limit = configuredLimit.getLimit();
    LimitChecker checker;

    // might need different implementations based on `action` but for now
    // using mongo backed implementations
    switch (limit.getLimitType()) {
      case STATIC:
        checker = new MongoStaticLimitChecker((StaticLimit) limit, wingsPersistence, action);
        break;
      case RATE_LIMIT:
        checker = new MongoSlidingWindowRateLimitChecker((RateLimit) limit, wingsPersistence, action);
        break;
      default:
        throw new IllegalArgumentException("Unhandled limit type: " + limit.getLimitType());
    }

    return checker;
  }

  private void validate(ConfiguredLimit configuredLimit, Action action) {
    if (null == configuredLimit.getLimit()) {
      throw new InvalidLimitConfigurationException(configuredLimit, "configuredLimit.limit is null");
    }

    LimitType limitType = configuredLimit.getLimit().getLimitType();
    ActionType actionType = action.getActionType();

    if (!actionType.getAllowedLimitTypes().contains(limitType)) {
      throw new InvalidLimitConfigurationException(configuredLimit,
          "invalid limitType supplied for given action. See ActionType enum for allowed limit types on a given action");
    }
  }
}
