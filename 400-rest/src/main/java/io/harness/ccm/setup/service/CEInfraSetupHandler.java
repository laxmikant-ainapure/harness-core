package io.harness.ccm.setup.service;

import io.harness.ccm.setup.CECloudAccountDao;

import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CECloudAccount;
import software.wings.beans.ce.CECloudAccount.AccountStatus;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class CEInfraSetupHandler {
  @Inject protected CECloudAccountDao ceCloudAccountDao;

  public abstract void syncCEInfra(SettingAttribute settingAttribute);

  public abstract boolean updateAccountPermission(CECloudAccount ceCloudAccount);

  @Value
  private static class AccountIdentifierKey {
    String accountId;
    String infraAccountId;
    String infraMasterAccountId;
  }

  protected void updateLinkedAccounts(
      String accountId, String settingId, String infraMasterAccountId, List<CECloudAccount> infraAccounts) {
    Map<AccountIdentifierKey, CECloudAccount> infraAccountMap = createAccountMap(infraAccounts);

    List<CECloudAccount> ceExistingAccounts =
        ceCloudAccountDao.getByMasterAccountId(accountId, settingId, infraMasterAccountId);
    Map<AccountIdentifierKey, CECloudAccount> ceExistingAccountMap = createAccountMap(ceExistingAccounts);

    infraAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!ceExistingAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.create(ceCloudAccount);
      }
    });

    ceExistingAccountMap.forEach((accountIdentifierKey, ceCloudAccount) -> {
      if (!infraAccountMap.containsKey(accountIdentifierKey)) {
        ceCloudAccountDao.deleteAccount(ceCloudAccount.getUuid());
      }
    });
  }

  protected void updateAccountStatus(CECloudAccount ceCloudAccount, boolean verifyAccess) {
    AccountStatus accountStatus = AccountStatus.NOT_CONNECTED;
    if (verifyAccess) {
      accountStatus = AccountStatus.CONNECTED;
    }
    ceCloudAccountDao.updateAccountStatus(ceCloudAccount, accountStatus);
  }

  private Map<AccountIdentifierKey, CECloudAccount> createAccountMap(List<CECloudAccount> cloudAccounts) {
    return cloudAccounts.stream().collect(Collectors.toMap(cloudAccount
        -> new AccountIdentifierKey(
            cloudAccount.getAccountId(), cloudAccount.getInfraAccountId(), cloudAccount.getInfraMasterAccountId()),
        Function.identity()));
  }
}
