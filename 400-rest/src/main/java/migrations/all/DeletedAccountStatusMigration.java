package migrations.all;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

@OwnedBy(PL)
@Slf4j
public class DeletedAccountStatusMigration implements Migration {
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createAuthorizedQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        String accountId = accounts.next().getUuid();
        String accountStatus = accountService.getAccountStatus(accountId);
        if (AccountStatus.DELETED.equals(accountStatus)) {
          log.info("Updating account's {} status from DELETED to MARKED-FOR-DELETION", accountId);
          accountService.updateAccountStatus(accountId, AccountStatus.MARKED_FOR_DELETION);
        }
      }
    }
  }
}
