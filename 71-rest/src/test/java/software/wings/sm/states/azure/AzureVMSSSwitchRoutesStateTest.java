package software.wings.sm.states.azure;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import com.google.common.collect.ImmutableMap;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSSwitchRoutesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.ResizeStrategy;
import software.wings.beans.Service;
import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import java.util.Collections;

public class AzureVMSSSwitchRoutesStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock private ActivityService activityService;
  @Mock private LogService logService;
  @InjectMocks
  private final AzureVMSSSwitchRoutesState switchRoutesState = new AzureVMSSSwitchRoutesState("switch-route-state");
  @InjectMocks
  private final AzureVMSSSwitchRoutesRollbackState switchRouteRollbackState =
      new AzureVMSSSwitchRoutesRollbackState("switch-route-rollback-state");

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    verifyDelegateTaskCreationResult(response, false);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, true);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, false);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteHandleAsyncResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    AzureVMSSTaskExecutionResponse taskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(
                AzureVMSSSwitchRoutesResponse.builder().delegateMetaInfo(DelegateMetaInfo.builder().build()).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    ExecutionResponse response =
        switchRoutesState.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, true);
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    verifyDelegateTaskCreationResult(response, true);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, false);
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  private ExecutionContextImpl initializeMockSetup(
      AzureVMSSSwitchRoutesState routeState, boolean isSuccess, boolean contextElement) {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    routeState.setDownsizeOldVMSSS(true);

    AzureVMSSStateData azureVMSSStateData = AzureVMSSStateData.builder()
                                                .application(anApplication().uuid(APP_ID).build())
                                                .service(Service.builder().build())
                                                .serviceId("serviceId")
                                                .azureConfig(AzureConfig.builder().build())
                                                .azureEncryptedDataDetails(emptyList())
                                                .infrastructureMapping(AzureVMSSInfrastructureMapping.builder()
                                                                           .resourceGroupName("ResourceGroup")
                                                                           .subscriptionId("subscriptionId")
                                                                           .baseVMSSName("baseScaleSet")
                                                                           .build())
                                                .artifact(anArtifact().withUuid(ARTIFACT_ID).build())
                                                .environment(anEnvironment().uuid(ENV_ID).build())
                                                .build();

    doReturn(azureVMSSStateData).when(azureVMSSStateHelper).populateStateData(mockContext);

    if (contextElement) {
      AzureVMSSSetupContextElement azureVMSSSetupContextElement =
          AzureVMSSSetupContextElement.builder()
              .isBlueGreen(true)
              .desiredInstances(2)
              .maxInstances(4)
              .minInstances(1)
              .autoScalingSteadyStateVMSSTimeout(10)
              .newVirtualMachineScaleSetName("newScaleSet")
              .oldVirtualMachineScaleSetName("oldScaleSet")
              .oldDesiredCount(1)
              .baseVMSSScalingPolicyJSONs(emptyList())
              .resizeStrategy(ResizeStrategy.RESIZE_NEW_FIRST)
              .commandName(AzureVMSSSwitchRoutesState.AZURE_VMSS_SWAP_ROUTE)
              .infraMappingId("infraId")
              .azureLoadBalancerDetail(AzureLoadBalancerDetailForBGDeployment.builder()
                                           .loadBalancerName("loadBalancer")
                                           .prodBackendPool("prodPool")
                                           .stageBackendPool("stagePool")
                                           .build())
              .preDeploymentData(AzureVMSSPreDeploymentData.builder().build())
              .build();

      doReturn(azureVMSSSetupContextElement).when(mockContext).getContextElement(any());
    }

    Activity activity = Activity.builder()
                            .uuid(ACTIVITY_ID)
                            .commandUnits(Collections.singletonList(new AzureVMSSDummyCommandUnit()))
                            .build();
    doReturn(activity)
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());

    ManagerExecutionLogCallback executionLogCallback = new ManagerExecutionLogCallback(logService,
        aLog().appId(activity.getAppId()).activityId(activity.getUuid()).commandUnitName("test"), activity.getUuid());
    doReturn(executionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(eq(activity));

    AzureVMSSSwitchRouteStateExecutionData stateExecutionData =
        AzureVMSSSwitchRouteStateExecutionData.builder().build();
    doReturn(stateExecutionData).when(mockContext).getStateExecutionData();

    doReturn(SUCCESS).when(azureVMSSStateHelper).getExecutionStatus(any());

    if (!isSuccess) {
      doThrow(Exception.class).when(delegateService).queueTask(any());
    }
    return mockContext;
  }

  private void verifyDelegateTaskCreationResult(ExecutionResponse response, boolean isRollback) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(captor.capture());

    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AzureVMSSCommandRequest).isTrue();
    AzureVMSSCommandRequest params = (AzureVMSSCommandRequest) delegateTask.getData().getParameters()[0];

    assertThat(params.getAzureVMSSTaskParameters() instanceof AzureVMSSSwitchRouteTaskParameters).isTrue();
    AzureVMSSSwitchRouteTaskParameters azureVMSSTaskParameters =
        (AzureVMSSSwitchRouteTaskParameters) params.getAzureVMSSTaskParameters();

    assertThat(azureVMSSTaskParameters.getOldVMSSName()).isEqualTo("oldScaleSet");
    assertThat(azureVMSSTaskParameters.getNewVMSSName()).isEqualTo("newScaleSet");
    assertThat(azureVMSSTaskParameters.isDownscaleOldVMSS()).isEqualTo(true);
    assertThat(azureVMSSTaskParameters.isRollback()).isEqualTo(isRollback);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }
}
