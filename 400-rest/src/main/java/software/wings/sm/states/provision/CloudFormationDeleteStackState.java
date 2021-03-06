package software.wings.sm.states.provision;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.tasks.Cd1SetupFields;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandResponse;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class CloudFormationDeleteStackState extends CloudFormationState {
  private static final String COMMAND_UNIT = "Delete Stack";

  public CloudFormationDeleteStackState(String name) {
    super(name, StateType.CLOUD_FORMATION_DELETE_STACK.name());
  }

  @Override
  protected List<String> commandUnits() {
    return Collections.singletonList(mainCommandUnit());
  }

  @Override
  protected String mainCommandUnit() {
    return COMMAND_UNIT;
  }

  @Override
  protected ExecutionResponse buildAndQueueDelegateTask(ExecutionContextImpl executionContext,
      CloudFormationInfrastructureProvisioner provisioner, AwsConfig awsConfig, String activityId) {
    notNullCheck("Application cannot be null", executionContext.getApp());

    CloudFormationDeleteStackRequest request =
        CloudFormationDeleteStackRequest.builder()
            .region(region)
            .stackNameSuffix(getStackNameSuffix(executionContext, provisioner.getUuid()))
            .customStackName(
                useCustomStackName ? executionContext.renderExpression(customStackName) : StringUtils.EMPTY)
            .commandType(CloudFormationCommandType.DELETE_STACK)
            .cloudFormationRoleArn(executionContext.renderExpression(getCloudFormationRoleArn()))
            .accountId(executionContext.getApp().getAccountId())
            .appId(executionContext.getApp().getUuid())
            .activityId(activityId)
            .commandName(mainCommandUnit())
            .awsConfig(awsConfig)
            .build();
    setTimeOutOnRequest(request);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(executionContext.getApp().getAccountId())
            .waitId(activityId)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, executionContext.getApp().getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(CLOUD_FORMATION_TASK.name())
                      .parameters(new Object[] {request,
                          secretManager.getEncryptionDetails(
                              awsConfig, GLOBAL_APP_ID, executionContext.getWorkflowExecutionId())})
                      .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                      .build())
            .build();
    String delegateTaskId = delegateService.queueTask(delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  @Override
  protected List<CloudFormationElement> handleResponse(
      CloudFormationCommandResponse commandResponse, ExecutionContext context) {
    clearRollbackConfig((ExecutionContextImpl) context);
    return emptyList();
  }
}
