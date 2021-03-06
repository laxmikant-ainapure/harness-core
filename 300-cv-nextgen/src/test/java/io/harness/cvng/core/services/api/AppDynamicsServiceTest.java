package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.RequestExecutor;
import io.harness.cvng.client.VerificationManagerClient;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.AppdynamicsImportStatus;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class AppDynamicsServiceTest extends CvNextGenTestBase {
  @Inject AppDynamicsService appDynamicsService;
  @Inject OnboardingService onboardingService;
  @Mock VerificationManagerClient verificationManagerClient;
  @Mock NextGenService nextGenService;
  @Mock VerificationManagerService verificationManagerService;
  @Mock private RequestExecutor requestExecutor;
  private String accountId;
  private String connectorIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUuid();
    connectorIdentifier = generateUuid();
    FieldUtils.writeField(appDynamicsService, "verificationManagerClient", verificationManagerClient, true);
    FieldUtils.writeField(appDynamicsService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);
    FieldUtils.writeField(appDynamicsService, "requestExecutor", requestExecutor, true);
    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(
                ConnectorInfoDTO.builder().connectorConfig(AppDynamicsConnectorDTO.builder().build()).build()));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createMonitoringSource() {
    AppDynamicsCVConfig appDynamicsDSConfig1 = createAppDynamicsDataSourceCVConfig("App 1", "prod 1");
    AppDynamicsCVConfig appDynamicsDSConfig2 = createAppDynamicsDataSourceCVConfig("App 2", "prod 1");
    AppDynamicsCVConfig appDynamicsDSConfig3 = createAppDynamicsDataSourceCVConfig("App 3", "prod 2");
    List<CVConfig> cvConfigs = Arrays.asList(appDynamicsDSConfig1, appDynamicsDSConfig2, appDynamicsDSConfig3);
    List<AppDynamicsApplication> appDynamicsApplications = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      appDynamicsApplications.add(AppDynamicsApplication.builder().name(generateUuid()).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(appDynamicsApplications));
    AppdynamicsImportStatus appdynamicsImportStatus =
        (AppdynamicsImportStatus) appDynamicsService.createMonitoringSourceImportStatus(cvConfigs, 3);
    assertThat(appdynamicsImportStatus).isNotNull();
    assertThat(appdynamicsImportStatus.getNumberOfEnvironments()).isEqualTo(2);
    assertThat(appdynamicsImportStatus.getNumberOfApplications()).isEqualTo(3);
    assertThat(appdynamicsImportStatus.getTotalNumberOfApplications()).isEqualTo(5);
    assertThat(appdynamicsImportStatus.getTotalNumberOfEnvironments()).isEqualTo(3);
  }

  private AppDynamicsCVConfig createAppDynamicsDataSourceCVConfig(String applicationName, String envIdentifier) {
    AppDynamicsCVConfig appDynamicsDSConfig = new AppDynamicsCVConfig();
    appDynamicsDSConfig.setConnectorIdentifier(connectorIdentifier);
    appDynamicsDSConfig.setApplicationName(applicationName);
    appDynamicsDSConfig.setEnvIdentifier(envIdentifier);
    appDynamicsDSConfig.setAccountId(accountId);
    return appDynamicsDSConfig;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetApplications() {
    List<AppDynamicsApplication> appDynamicsApplications = new ArrayList<>();
    int numOfApplications = 100;
    for (int i = 0; i < numOfApplications; i++) {
      appDynamicsApplications.add(AppDynamicsApplication.builder().name("app-" + i).id(i).build());
    }
    Collections.shuffle(appDynamicsApplications);
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(appDynamicsApplications));
    PageResponse<AppDynamicsApplication> applications = appDynamicsService.getApplications(
        accountId, connectorIdentifier, generateUuid(), generateUuid(), 0, 5, "ApP-2");
    assertThat(applications.getContent())
        .isEqualTo(Lists.newArrayList(AppDynamicsApplication.builder().name("app-2").id(2).build(),
            AppDynamicsApplication.builder().name("app-20").id(20).build(),
            AppDynamicsApplication.builder().name("app-21").id(21).build(),
            AppDynamicsApplication.builder().name("app-22").id(22).build(),
            AppDynamicsApplication.builder().name("app-23").id(23).build()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetTiers() {
    Set<AppDynamicsTier> appDynamicsTiers = new HashSet<>();
    int numOfApplications = 100;
    for (int i = 0; i < numOfApplications; i++) {
      appDynamicsTiers.add(AppDynamicsTier.builder().name("tier-" + i).id(i).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(appDynamicsTiers));
    PageResponse<AppDynamicsTier> applications = appDynamicsService.getTiers(
        accountId, connectorIdentifier, generateUuid(), generateUuid(), generateUuid(), 0, 5, "IeR-2");
    assertThat(applications.getContent())
        .isEqualTo(Lists.newArrayList(AppDynamicsTier.builder().name("tier-2").id(2).build(),
            AppDynamicsTier.builder().name("tier-20").id(20).build(),
            AppDynamicsTier.builder().name("tier-21").id(21).build(),
            AppDynamicsTier.builder().name("tier-22").id(22).build(),
            AppDynamicsTier.builder().name("tier-23").id(23).build()));
  }
}
