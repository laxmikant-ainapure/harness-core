package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.rule.OwnerRule.ARVIND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static software.wings.beans.command.EcsResizeParams.EcsResizeParamsBuilder.anEcsResizeParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import com.google.inject.Inject;

import io.harness.category.element.UnitTests;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.rule.Owner;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceData;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;
import software.wings.service.intfc.security.EncryptionService;

import java.util.Collections;
import java.util.LinkedHashMap;

public class EcsDeployCommandHandlerTest extends WingsBaseTest {
  @Mock private EcsDeployCommandTaskHelper mockEcsDeployCommandTaskHelper;
  @Mock private AwsClusterService mockAwsClusterService;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateLogService mockDelegateLogService;
  @InjectMocks @Inject private EcsDeployCommandHandler handler;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsServiceDeployResponse ecsServiceDeployResponse =
        EcsServiceDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest = new EcsCommandRequest(null, null, null, null, null, null, null, null);
    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo("Invalid request Type, expected EcsServiceDeployRequest");
    assertThat(ecsServiceDeployResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionResult.CommandExecutionStatus.FAILURE);
    assertThat(ecsServiceDeployResponse.getOutput())
        .isEqualTo("Invalid request Type, expected EcsServiceDeployRequest");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsServiceDeployResponse ecsServiceDeployResponse =
        EcsServiceDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest =
        EcsServiceDeployRequest.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .cluster(CLUSTER_NAME)
            .ecsResizeParams(anEcsResizeParams()
                                 .withRollback(true)
                                 .withRollbackAllPhases(true)
                                 .withNewInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .withOldInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .build())
            .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());

    doReturn(new LinkedHashMap<String, Integer>() {
      { put(SERVICE_NAME, 3); }
    })
        .when(mockEcsDeployCommandTaskHelper)
        .listOfStringArrayToMap(any());
    doReturn(new LinkedHashMap<String, Integer>() {
      { put(SERVICE_NAME, 2); }
    })
        .when(mockEcsDeployCommandTaskHelper)
        .getActiveServiceCounts(any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    verify(mockAwsClusterService, times(2))
        .resizeCluster(anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).restoreAutoScalarConfigs(any(), any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
    verify(mockEcsDeployCommandTaskHelper).setDesiredToOriginal(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskAlreadyRolledBack() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsServiceDeployResponse ecsServiceDeployResponse =
        EcsServiceDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest =
        EcsServiceDeployRequest.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .cluster(CLUSTER_NAME)
            .ecsResizeParams(anEcsResizeParams()
                                 .withRollback(true)
                                 .withRollbackAllPhases(true)
                                 .withNewInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .withOldInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .build())
            .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());

    doReturn(new LinkedHashMap<String, Integer>()).when(mockEcsDeployCommandTaskHelper).listOfStringArrayToMap(any());
    doReturn(new LinkedHashMap<String, Integer>()).when(mockEcsDeployCommandTaskHelper).getActiveServiceCounts(any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    verify(mockAwsClusterService, times(0))
        .resizeCluster(anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any());
    verify(mockEcsDeployCommandTaskHelper, times(0)).restoreAutoScalarConfigs(any(), any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(0)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskNoRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsServiceDeployResponse ecsServiceDeployResponse =
        EcsServiceDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest = EcsServiceDeployRequest.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .cluster(CLUSTER_NAME)
                                              .ecsResizeParams(anEcsResizeParams().withRollback(false).build())
                                              .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());
    doReturn(ContainerServiceData.builder().build()).when(mockEcsDeployCommandTaskHelper).getNewInstanceData(any());
    doReturn(Collections.singletonList(ContainerServiceData.builder().build()))
        .when(mockEcsDeployCommandTaskHelper)
        .getOldInstanceData(any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    verify(mockAwsClusterService, times(2))
        .resizeCluster(anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).deregisterAutoScalarsIfExists(any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }
}