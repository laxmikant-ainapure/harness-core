package software.wings.service.impl.ce;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.ParametersAreNonnullByDefault;

@OwnedBy(CE)
@Singleton
@ParametersAreNonnullByDefault
public class CeAccountExpirationCheckerImpl implements CeAccountExpirationChecker {
  @Inject private HPersistence persistence;
  private static final long CACHE_SIZE = 1000;

  private LoadingCache<String, Boolean> accountIdToIsCeEnabled =
      Caffeine.newBuilder().maximumSize(CACHE_SIZE).build(this::isCeEnabledForAccount);

  @Override
  public void checkIsCeEnabled(String accountId) {
    if (!accountIdToIsCeEnabled.get(accountId)) {
      throw new InvalidRequestException("Continuous Efficiency is not enabled on account: " + accountId);
    }
  }

  private boolean isCeEnabledForAccount(String accountId) {
    Account account = persistence.createQuery(Account.class, excludeAuthority)
                          .filter(AccountKeys.cloudCostEnabled, Boolean.TRUE)
                          .field(AccountKeys.uuid)
                          .equal(accountId)
                          .get();
    return account != null;
  }
}
