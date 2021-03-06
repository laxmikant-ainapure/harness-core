package software.wings.service.impl;

import static io.harness.rule.OwnerRule.HANTANG;

import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SettingAttributeDaoTest extends WingsBaseTest {
  private String accountId = "ACCOUNT_ID";
  @Inject private SettingAttributeDao settingAttributeDao;

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldList() {
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder().build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withCategory(CLOUD_PROVIDER)
                                            .withValue(kubernetesClusterConfig)
                                            .build();
    settingAttributeDao.save(settingAttribute);
    List<SettingAttribute> settingAttributes = settingAttributeDao.list(accountId, CLOUD_PROVIDER);
    assertThat(settingAttributes).hasSize(1);
  }
}
