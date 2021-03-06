package migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Account.AccountKeys;

import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

@Slf4j
public class AddHarnessGroupAccessFlagToAccount implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Starting migration for adding isHarnessSupportAccessAllowed flag to accounts");

    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority)
                                                           .project(AccountKeys.isHarnessSupportAccessAllowed, true)
                                                           .fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        wingsPersistence.updateField(
            Account.class, account.getUuid(), AccountKeys.isHarnessSupportAccessAllowed, Boolean.TRUE);
      }
    } catch (Exception e) {
      log.error("Error happened while adding isHarnessSupportAccessAllowed flag to accounts", e);
    }
  }
}
