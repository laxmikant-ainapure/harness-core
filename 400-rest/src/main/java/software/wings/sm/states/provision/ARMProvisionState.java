package software.wings.sm.states.provision;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.ExceptionUtils.getMessage;

import static software.wings.beans.ARMSourceType.GIT;
import static software.wings.beans.TaskType.AZURE_ARM_TASK;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;

import static java.util.Collections.singletonList;

import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.request.AzureARMDeploymentParameters;
import io.harness.delegate.task.azure.arm.response.AzureARMDeploymentResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.Cd1SetupFields;
import io.harness.tasks.ResponseData;

import software.wings.api.ARMStateExecutionData;
import software.wings.api.ARMStateExecutionData.ARMStateExecutionDataBuilder;
import software.wings.api.arm.ARMPreExistingTemplate;
import software.wings.beans.ARMInfrastructureProvisioner;
import software.wings.beans.Activity;
import software.wings.beans.AzureConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.TaskType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.states.azure.AzureSweepingOutputServiceHelper;
import software.wings.sm.states.azure.AzureVMSSStateHelper;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "ARMProvisionStateKeys")
public class ARMProvisionState extends State {
  private static final String TEMPLATE_KEY = "TEMPLATE";
  private static final String VARIABLES_KEY = "VARIABLES";

  @Getter @Setter protected String provisionerId;
  @Getter @Setter protected String cloudProviderId;
  @Getter @Setter protected String timeoutExpression;
  @Getter @Setter private AzureDeploymentMode deploymentMode = AzureDeploymentMode.INCREMENTAL;

  @Getter @Setter private String locationExpression;
  @Getter @Setter protected String subscriptionExpression;
  @Getter @Setter protected String resourceGroupExpression;
  @Getter @Setter private String managementGroupExpression;

  @Getter @Setter private String inlineParametersExpression;
  @Getter @Setter private GitFileConfig parametersGitFileConfig;

  @Inject protected ARMStateHelper helper;
  @Inject protected DelegateService delegateService;
  @Inject private ActivityService activityService;
  @Inject protected AzureVMSSStateHelper azureVMSSStateHelper;
  @Inject protected AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;

  public ARMProvisionState(String name) {
    super(name, StateType.ARM_CREATE_RESOURCE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  protected ExecutionResponse executeInternal(ExecutionContext context) {
    ARMInfrastructureProvisioner provisioner = helper.getProvisioner(context.getAppId(), provisionerId);
    boolean executeGitTask = helper.executeGitTask(provisioner, parametersGitFileConfig);
    Activity activity = helper.createActivity(context, executeGitTask, getStateType());

    if (executeGitTask) {
      return executeGitTask(context, provisioner, activity);
    } else {
      return executeARMTask(context, null, activity.getUuid());
    }
  }

  private ExecutionResponse executeGitTask(
      ExecutionContext context, ARMInfrastructureProvisioner provisioner, Activity activity) {
    ARMStateExecutionDataBuilder builder = ARMStateExecutionData.builder();
    builder.taskType(GIT_FETCH_FILES_TASK);
    Map<String, GitFetchFilesConfig> filesConfigMap = new HashMap<>();
    if (GIT == provisioner.getSourceType()) {
      filesConfigMap.put(TEMPLATE_KEY, helper.createGitFetchFilesConfig(provisioner.getGitFileConfig(), context));
    }
    if (parametersGitFileConfig != null) {
      filesConfigMap.put(VARIABLES_KEY, helper.createGitFetchFilesConfig(parametersGitFileConfig, context));
    }
    GitFetchFilesTaskParams taskParams = GitFetchFilesTaskParams.builder()
                                             .activityId(activity.getUuid())
                                             .accountId(context.getAccountId())
                                             .appId(context.getAppId())
                                             .executionLogName(AzureConstants.FETCH_FILES)
                                             .isFinalState(true)
                                             .appManifestKind(K8S_MANIFEST)
                                             .gitFetchFilesConfigMap(filesConfigMap)
                                             .containerServiceParams(null)
                                             .isBindTaskFeatureSet(false)
                                             .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId(context.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, context.fetchRequiredEnvironment().getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(GIT_FETCH_FILES_TASK.name())
                      .parameters(new Object[] {taskParams})
                      .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                      .build())
            .build();
    delegateService.queueTask(delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(delegateTask.getUuid()))
        .stateExecutionData(builder.activityId(activity.getUuid()).build())
        .build();
  }

  private ExecutionResponse executeARMTask(
      ExecutionContext context, ARMStateExecutionData stateExecutionData, String activityId) {
    ARMStateExecutionDataBuilder builder = ARMStateExecutionData.builder();
    builder.taskType(TaskType.AZURE_ARM_TASK);
    builder.activityId(activityId);
    ARMInfrastructureProvisioner provisioner = helper.getProvisioner(context.getAppId(), provisionerId);
    if (stateExecutionData != null) {
      builder.fetchFilesResult(stateExecutionData.getFetchFilesResult());
    }
    String templateBody;
    if (GIT == provisioner.getSourceType()) {
      templateBody = helper.extractJsonFromGitResponse(stateExecutionData, TEMPLATE_KEY);
    } else {
      templateBody = provisioner.getTemplateBody();
    }

    String parametersBody;
    if (parametersGitFileConfig != null) {
      parametersBody = helper.extractJsonFromGitResponse(stateExecutionData, VARIABLES_KEY);
    } else {
      parametersBody = inlineParametersExpression;
    }

    AzureARMDeploymentParameters taskParams =
        AzureARMDeploymentParameters.builder()
            .appId(context.getAppId())
            .accountId(context.getAccountId())
            .activityId(activityId)
            .deploymentScope(provisioner.getScopeType())
            .deploymentMode(deploymentMode(provisioner.getScopeType()))
            .managementGroupId(context.renderExpression(managementGroupExpression))
            .subscriptionId(context.renderExpression(subscriptionExpression))
            .resourceGroupName(context.renderExpression(resourceGroupExpression))
            .deploymentDataLocation(context.renderExpression(locationExpression))
            .templateJson(context.renderExpression(templateBody))
            .parametersJson(context.renderExpression(parametersBody))
            .commandName(ARMStateHelper.AZURE_ARM_COMMAND_UNIT_TYPE)
            .timeoutIntervalInMin(helper.renderTimeout(timeoutExpression, context))
            .build();

    AzureConfig azureConfig = azureVMSSStateHelper.getAzureConfig(cloudProviderId);
    List<EncryptedDataDetail> azureEncryptionDetails =
        azureVMSSStateHelper.getEncryptedDataDetails(context, cloudProviderId);
    AzureConfigDTO azureConfigDTO = azureVMSSStateHelper.createAzureConfigDTO(azureConfig);

    AzureTaskExecutionRequest delegateRequest = AzureTaskExecutionRequest.builder()
                                                    .azureConfigDTO(azureConfigDTO)
                                                    .azureConfigEncryptionDetails(azureEncryptionDetails)
                                                    .azureTaskParameters(taskParams)
                                                    .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId(context.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, context.fetchRequiredEnvironment().getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(AZURE_ARM_TASK.name())
                      .parameters(new Object[] {delegateRequest})
                      .timeout(TimeUnit.MINUTES.toMillis(helper.renderTimeout(timeoutExpression, context)))
                      .build())
            .build();
    delegateService.queueTask(delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(delegateTask.getUuid()))
        .stateExecutionData(builder.build())
        .build();
  }

  private AzureDeploymentMode deploymentMode(ARMScopeType scopeType) {
    if (ARMScopeType.RESOURCE_GROUP == scopeType) {
      return deploymentMode;
    }
    return AzureDeploymentMode.INCREMENTAL;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    ARMStateExecutionData stateExecutionData = context.getStateExecutionData();
    TaskType taskType = stateExecutionData.getTaskType();
    switch (taskType) {
      case GIT_FETCH_FILES_TASK:
        return handleAsyncInternalGitTask(context, response, stateExecutionData);
      case AZURE_ARM_TASK:
        return handleAsyncInternalARMTask(context, response, stateExecutionData);
      default:
        throw new InvalidRequestException("Unhandled task type " + taskType);
    }
  }

  private ExecutionResponse handleAsyncInternalGitTask(
      ExecutionContext context, Map<String, ResponseData> response, ARMStateExecutionData stateExecutionData) {
    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getGitCommandStatus() == GitCommandExecutionResponse.GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(stateExecutionData.getActivityId(), context.getAppId(), executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }
    stateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());
    return executeARMTask(context, stateExecutionData, stateExecutionData.getActivityId());
  }

  private ExecutionResponse handleAsyncInternalARMTask(
      ExecutionContext context, Map<String, ResponseData> response, ARMStateExecutionData stateExecutionData) {
    AzureTaskExecutionResponse executionResponse = (AzureTaskExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    activityService.updateStatus(stateExecutionData.getActivityId(), context.getAppId(), executionStatus);

    if (!isRollback()) {
      savePreDeploymentData(context, executionResponse);
    }
    if (ExecutionStatus.FAILED == executionStatus) {
      return ExecutionResponse.builder()
          .errorMessage(executionResponse.getErrorMessage())
          .executionStatus(executionStatus)
          .build();
    }
    saveARMOutputs(context, executionResponse);
    return ExecutionResponse.builder().stateExecutionData(stateExecutionData).executionStatus(executionStatus).build();
  }

  private void savePreDeploymentData(ExecutionContext context, AzureTaskExecutionResponse executionResponse) {
    AzureARMDeploymentResponse azureTaskResponse =
        (AzureARMDeploymentResponse) executionResponse.getAzureTaskResponse();
    AzureARMPreDeploymentData preDeploymentData = azureTaskResponse.getPreDeploymentData();
    ARMPreExistingTemplate armPreExistingTemplate =
        ARMPreExistingTemplate.builder().preDeploymentData(preDeploymentData).build();

    String prefix = String.format(
        "%s-%s-%s", provisionerId, preDeploymentData.getSubscriptionId(), preDeploymentData.getResourceGroup());
    if (!azureSweepingOutputServiceHelper.dataExist(context, prefix)) {
      azureSweepingOutputServiceHelper.saveToSweepingOutPut(armPreExistingTemplate, prefix, context);
    }
  }

  private void saveARMOutputs(ExecutionContext context, AzureTaskExecutionResponse executionResponse) {
    AzureARMDeploymentResponse azureTaskResponse =
        (AzureARMDeploymentResponse) executionResponse.getAzureTaskResponse();
    helper.saveARMOutputs(azureTaskResponse.getOutputs(), context);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // No implementation done yet for this method
  }
}
