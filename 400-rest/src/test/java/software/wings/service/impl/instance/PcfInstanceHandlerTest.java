package software.wings.service.impl.instance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANKIT;

import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ORGANIZATION;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_APP_GUID_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_APP_GUID_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_0;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ROUTE1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ROUTE2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SPACE;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.PcfAppNotFoundException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentSummary;
import software.wings.api.PcfDeploymentInfo;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.helpers.ext.pcf.response.PcfCommandExecutionResponse;
import software.wings.helpers.ext.pcf.response.PcfInstanceSyncResponse;
import software.wings.service.impl.PcfHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PcfInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private InstanceService instanceService;
  @Mock private PcfHelperService pcfHelperService;
  @Mock private AppService appService;
  @Mock private SettingsService settingsService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock DeploymentService deploymentService;
  @InjectMocks @Inject PcfInstanceHandler pcfInstanceHandler;

  @Before
  public void setUp() {
    doReturn(PcfInfrastructureMapping.builder()
                 .organization(ORGANIZATION)
                 .space(SPACE)
                 .routeMaps(Arrays.asList(ROUTE1))
                 .tempRouteMap(Arrays.asList(ROUTE2))
                 .computeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .uuid(INFRA_MAPPING_ID)
                 .envId(ENV_ID)
                 .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                 .serviceId(SERVICE_ID)
                 .accountId(ACCOUNT_ID)
                 .appId(APP_ID)
                 .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                 .computeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .build())
        .when(infraMappingService)
        .get(anyString(), anyString());

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(anyString());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(anyString(), anyString(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().build()).build())
        .when(settingsService)
        .get(anyString());
  }

  // 2 existing PCF instances,
  // expected 1 Delete, 1 Update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances =
        asList(Instance.builder()
                   .uuid(INSTANCE_1_ID)
                   .accountId(ACCOUNT_ID)
                   .appId(APP_ID)
                   .computeProviderId(COMPUTE_PROVIDER_NAME)
                   .appName(APP_NAME)
                   .envId(ENV_ID)
                   .envName(ENV_NAME)
                   .envType(EnvironmentType.PROD)
                   .infraMappingId(INFRA_MAPPING_ID)
                   .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                   .pcfInstanceKey(PcfInstanceKey.builder().id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0).build())
                   .instanceType(InstanceType.PCF_INSTANCE)
                   .instanceInfo(PcfInstanceInfo.builder()
                                     .organization(ORGANIZATION)
                                     .space(SPACE)
                                     .pcfApplicationName(APP_NAME_1)
                                     .pcfApplicationGuid(PCF_APP_GUID_1)
                                     .instanceIndex(PCF_INSTANCE_INDEX_0)
                                     .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                                     .build())
                   .build(),
            Instance.builder()
                .uuid(INSTANCE_2_ID)
                .accountId(ACCOUNT_ID)
                .appId(APP_ID)
                .computeProviderId(COMPUTE_PROVIDER_NAME)
                .appName(APP_NAME)
                .envId(ENV_ID)
                .envName(ENV_NAME)
                .envType(EnvironmentType.PROD)
                .infraMappingId(INFRA_MAPPING_ID)
                .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                .pcfInstanceKey(PcfInstanceKey.builder().id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1).build())
                .instanceType(InstanceType.PCF_INSTANCE)
                .instanceInfo(PcfInstanceInfo.builder()
                                  .organization(ORGANIZATION)
                                  .space(SPACE)
                                  .pcfApplicationName(APP_NAME_1)
                                  .pcfApplicationGuid(PCF_APP_GUID_1)
                                  .instanceIndex(PCF_INSTANCE_INDEX_1)
                                  .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1)
                                  .build())
                .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(anyString(), anyString());

    List<PcfInstanceInfo> pcfInstanceInfos = Arrays.asList(PcfInstanceInfo.builder()
                                                               .organization(ORGANIZATION)
                                                               .space(SPACE)
                                                               .pcfApplicationName(APP_NAME_1)
                                                               .pcfApplicationGuid(PCF_APP_GUID_1)
                                                               .instanceIndex(PCF_INSTANCE_INDEX_0)
                                                               .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                                                               .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_1)
            .pcfApplicationGuid(PCF_APP_GUID_1)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_2)
            .build());

    doReturn(pcfInstanceInfos)
        .when(pcfHelperService)
        .getApplicationDetails(anyString(), anyString(), anyString(), any(), any());

    pcfInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(INSTANCE_2_ID)).isTrue();
  }

  /**
   * Add 3 new instance for new deployment
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_2() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());

    doReturn(pageResponse).when(instanceService).list(any());

    List<PcfInstanceInfo> pcfInstanceInfos = Arrays.asList(PcfInstanceInfo.builder()
                                                               .organization(ORGANIZATION)
                                                               .space(SPACE)
                                                               .pcfApplicationName(APP_NAME_2)
                                                               .pcfApplicationGuid(PCF_APP_GUID_2)
                                                               .instanceIndex(PCF_INSTANCE_INDEX_0)
                                                               .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0)
                                                               .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_1)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1)
            .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2)
            .build());

    doReturn(pcfInstanceInfos)
        .when(pcfHelperService)
        .getApplicationDetails(anyString(), anyString(), anyString(), any(), any());

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    pcfInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(
                              PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .artifactName("new")
                          .artifactBuildNum("1")
                          .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    Set<String> expectedKeys = new HashSet<>();
    expectedKeys.addAll(Arrays.asList(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0,
        PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1, PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2));

    assertThat(expectedKeys.contains(capturedInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");

    assertThat(expectedKeys.contains(capturedInstances.get(1).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(1).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(1).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");

    assertThat(expectedKeys.contains(capturedInstances.get(2).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(2).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(2).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_rollback() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());

    doReturn(pageResponse).when(instanceService).list(any());

    List<PcfInstanceInfo> pcfInstanceInfos = Arrays.asList(PcfInstanceInfo.builder()
                                                               .organization(ORGANIZATION)
                                                               .space(SPACE)
                                                               .pcfApplicationName(APP_NAME_2)
                                                               .pcfApplicationGuid(PCF_APP_GUID_2)
                                                               .instanceIndex(PCF_INSTANCE_INDEX_0)
                                                               .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0)
                                                               .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_1)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1)
            .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2)
            .build());

    doReturn(pcfInstanceInfos)
        .when(pcfHelperService)
        .getApplicationDetails(anyString(), anyString(), anyString(), any(), any());

    doReturn(
        Optional.of(DeploymentSummary.builder()
                        .deploymentInfo(
                            PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                        .accountId(ACCOUNT_ID)
                        .infraMappingId(INFRA_MAPPING_ID)
                        .workflowExecutionId("workfloeExecution_1")
                        .stateExecutionInstanceId("stateExecutionInstanceId")
                        .artifactBuildNum("1")
                        .artifactName("old")
                        .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    pcfInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(
                              PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .artifactBuildNum("2")
                          .artifactName("new")
                          .build()),
        true, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    Set<String> expectedKeys = new HashSet<>();
    expectedKeys.addAll(Arrays.asList(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0,
        PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1, PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2));

    assertThat(expectedKeys.contains(capturedInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("old");

    assertThat(expectedKeys.contains(capturedInstances.get(1).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(1).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(1).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(1).getLastArtifactName()).isEqualTo("old");

    assertThat(expectedKeys.contains(capturedInstances.get(2).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(2).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(2).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(2).getLastArtifactName()).isEqualTo("old");
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetStatusWhenSuccessResponse() {
    InfrastructureMapping infrastructureMapping = mock(InfrastructureMapping.class);
    when(infrastructureMapping.getUuid()).thenReturn("ID");

    PcfCommandExecutionResponse pcfCommandExecutionResponse = getPcfCommandExecutionResponse(SUCCESS);
    when(pcfHelperService.getInstanceCount(pcfCommandExecutionResponse)).thenReturn(1);

    Status status = pcfInstanceHandler.getStatus(infrastructureMapping, pcfCommandExecutionResponse);
    assertTrue(status.isSuccess());
    assertTrue(status.isRetryable());
    assertTrue(isEmpty(status.getErrorMessage()));

    when(pcfHelperService.getInstanceCount(pcfCommandExecutionResponse)).thenReturn(0);
    status = pcfInstanceHandler.getStatus(infrastructureMapping, pcfCommandExecutionResponse);
    assertTrue(status.isSuccess());
    assertFalse(status.isRetryable());
    assertTrue(isEmpty(status.getErrorMessage()));
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testGetStatusWhenSuccessFailure() throws Exception {
    InfrastructureMapping infrastructureMapping = mock(InfrastructureMapping.class);
    when(infrastructureMapping.getUuid()).thenReturn("ID");

    PcfCommandExecutionResponse pcfCommandExecutionResponse = getPcfCommandExecutionResponse(FAILURE);

    Status status = pcfInstanceHandler.getStatus(infrastructureMapping, pcfCommandExecutionResponse);
    assertFalse(status.isSuccess());
    assertTrue(status.isRetryable());

    when(pcfHelperService.validatePcfInstanceSyncResponse(any(), any(), any(), any()))
        .thenThrow(PcfAppNotFoundException.class);
    status = pcfInstanceHandler.getStatus(infrastructureMapping, pcfCommandExecutionResponse);
    assertFalse(status.isSuccess());
    assertFalse(status.isRetryable());
  }

  private PcfCommandExecutionResponse getPcfCommandExecutionResponse(CommandExecutionStatus commandExecutionStatus) {
    PcfInstanceSyncResponse pcfInstanceSyncResponse =
        PcfInstanceSyncResponse.builder().commandExecutionStatus(commandExecutionStatus).build();

    return PcfCommandExecutionResponse.builder()
        .pcfCommandResponse(pcfInstanceSyncResponse)
        .commandExecutionStatus(commandExecutionStatus)
        .build();
  }
}
