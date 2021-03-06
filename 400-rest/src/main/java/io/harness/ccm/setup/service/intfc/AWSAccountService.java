package io.harness.ccm.setup.service.intfc;

import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CECloudAccount;

import java.util.List;

public interface AWSAccountService {
  List<CECloudAccount> getAWSAccounts(String accountId, String settingId, CEAwsConfig ceAwsConfig);

  void updateAccountPermission(String accountId, String settingId);

  void updateAccountPermission(SettingAttribute settingAttribute);
}
