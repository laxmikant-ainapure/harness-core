package io.harness.dashboard;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.RUSHABH;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static software.wings.beans.Account.Builder.anAccount;

import com.google.inject.Inject;

import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.features.api.PremiumFeature;
import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;
import software.wings.utils.WingsTestConstants;

public class DashboardSettingsServiceTest extends WingsBaseTest {
  @Inject private AccountService accountService;
  @Inject private LicenseService licenseService;
  @Inject DashboardSettingsService dashboardSettingsService;
  @Mock private PremiumFeature dashboardFeature;

  String accountId = "ACCOUNTID";

  @Before
  public void setupMocks() {
    Account account = anAccount()
                          .withUuid(accountId)
                          .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                          .withCompanyName(WingsTestConstants.COMPANY_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    accountService.save(account, false);
    accountId = account.getUuid();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDashboardCreate() {
    DashboardSettings dashboardSettings = getDashboardSettings(accountId, 1);

    DashboardSettings settings = dashboardSettingsService.createDashboardSettings(accountId, dashboardSettings);

    validateSettings(dashboardSettings, settings);

    settings = dashboardSettingsService.get(accountId, settings.getUuid());

    validateSettings(dashboardSettings, settings);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDashboardCreateUpdate() {
    DashboardSettings dashboardSettings = getDashboardSettings(accountId, 1);

    DashboardSettings settings = dashboardSettingsService.createDashboardSettings(accountId, dashboardSettings);

    validateSettings(dashboardSettings, settings);

    settings.setData("dataUpdated");
    settings.setName("updatedName");
    settings.setDescription("updatedDescription");

    DashboardSettings updatedSettings = dashboardSettingsService.updateDashboardSettings(accountId, settings);

    validateSettings(updatedSettings, settings);

    settings.setAccountId("FakeAccountID");
    updatedSettings = dashboardSettingsService.updateDashboardSettings(accountId, settings);

    assertThat(updatedSettings.getAccountId()).isEqualTo(accountId);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testCrudOnDashboardSettingsIfUnavailable() {
    when(dashboardFeature.isAvailableForAccount(accountId)).thenReturn(false);

    for (String restrictedAccountType : dashboardFeature.getRestrictedAccountTypes()) {
      LicenseInfo newLicenseInfo = getLicenseInfo();
      newLicenseInfo.setAccountType(restrictedAccountType);
      licenseService.updateAccountLicense(accountId, newLicenseInfo);
      try {
        dashboardSettingsService.createDashboardSettings(accountId, DashboardSettings.builder().build());
        fail("Dashboard Settings created when Dashboard feature is unavailable for account");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }

      try {
        dashboardSettingsService.deleteDashboardSettings(accountId, RandomStringUtils.random(5));
        fail("Dashboard Settings deleted when Dashboard feature is unavailable for account");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }

      try {
        dashboardSettingsService.get(accountId, RandomStringUtils.random(5));
        fail("Dashboard Settings retrieved when Dashboard feature is unavailable for account");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }

      try {
        dashboardSettingsService.updateDashboardSettings(accountId, DashboardSettings.builder().build());
        fail("Dashboard Settings updated when Dashboard feature is unavailable for account");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDashboardInvalidUpdate() {
    DashboardSettings dashboardSettings = getDashboardSettings(accountId, 1);

    DashboardSettings settings = dashboardSettingsService.createDashboardSettings(accountId, dashboardSettings);

    validateSettings(dashboardSettings, settings);

    settings.setData("dataUpdated");
    settings.setName("updatedName");
    settings.setDescription("updatedDescription");
    settings.setUuid(null);
    try {
      DashboardSettings updatedSettings = dashboardSettingsService.updateDashboardSettings(accountId, settings);
      fail();
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("Invalid Dashboard update request");
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDashboardDelete() {
    DashboardSettings dashboardSettings = getDashboardSettings(accountId, 1);

    DashboardSettings settings = dashboardSettingsService.createDashboardSettings(accountId, dashboardSettings);

    validateSettings(dashboardSettings, settings);

    settings = dashboardSettingsService.get(accountId, settings.getUuid());

    validateSettings(dashboardSettings, settings);

    assertThat(dashboardSettingsService.deleteDashboardSettings(accountId, settings.getUuid())).isTrue();
    assertThat(dashboardSettingsService.get(accountId, settings.getUuid())).isNull();
    assertThat(dashboardSettingsService.deleteDashboardSettings(accountId, settings.getUuid())).isFalse();
    assertThat(dashboardSettingsService.deleteDashboardSettings("Fake", accountId)).isFalse();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGetDashboardSummary() {
    for (int i = 0; i < 10; i++) {
      DashboardSettings dashboardSettings = getDashboardSettings(accountId, i);
      DashboardSettings settings = dashboardSettingsService.createDashboardSettings(accountId, dashboardSettings);
    }

    PageResponse<DashboardSettings> pageResponse = dashboardSettingsService.getDashboardSettingSummary(
        accountId, PageRequestBuilder.aPageRequest().withOffset("0").withLimit("20").build());

    assertThat(pageResponse.getTotal()).isEqualTo(10);

    assertThat(pageResponse.getResponse().get(0).getData()).isNullOrEmpty();
  }

  private void validateSettings(DashboardSettings source, DashboardSettings target) {
    assertThat(target.getAccountId()).isEqualTo(source.getAccountId());
    assertThat(target.getData()).isEqualTo(source.getData());
    assertThat(target.getDescription()).isEqualTo(source.getDescription());
    assertThat(target.getName()).isEqualTo(source.getName());
    assertThat(target.getUuid()).isNotEmpty();
  }

  private DashboardSettings getDashboardSettings(String accountId, int value) {
    return DashboardSettings.builder()
        .accountId(accountId)
        .data("fakedata" + value)
        .description("fakedescription" + value)
        .name("dashboard" + value)
        .build();
  }
}
