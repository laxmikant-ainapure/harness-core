package software.wings.sm.states.k8s;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.manifest.ManifestHelper.normalizeFolderPath;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.sm.ExecutionContextImpl.PHASE_PARAM;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.deployment.InstanceDetails;
import io.harness.deployment.InstanceDetails.InstanceType;
import io.harness.deployment.InstanceDetails.K8s;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.Cd1SetupFields;
import io.harness.tasks.ResponseData;

import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sHelmDeploymentElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmChartInfo;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig.K8sDelegateManifestConfigBuilder;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.helpers.ext.kustomize.KustomizeHelper;
import software.wings.helpers.ext.openshift.OpenShiftManagerService;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.HelmChartConfigHelperService;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public abstract class AbstractK8sState extends State implements K8sStateExecutor {
  @Inject private transient ApplicationManifestService applicationManifestService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient DelegateService delegateService;
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private transient AwsCommandHelper awsCommandHelper;
  @Inject private transient ActivityService activityService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject GitFileConfigHelperService gitFileConfigHelperService;
  @Inject ApplicationManifestUtils applicationManifestUtils;
  @Inject private HelmChartConfigHelperService helmChartConfigHelperService;
  @Inject private ServiceTemplateHelper serviceTemplateHelper;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private KustomizeHelper kustomizeHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private OpenShiftManagerService openShiftManagerService;
  @Inject private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Inject private InstanceService instanceService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private EnvironmentService environmentService;
  @Inject public K8sStateHelper k8sStateHelper;
  private static final long MIN_TASK_TIMEOUT_IN_MINUTES = 1L;

  public AbstractK8sState(String name, String stateType) {
    super(name, stateType);
  }

  public Activity createK8sActivity(ExecutionContext executionContext, String commandName, String stateType,
      ActivityService activityService, List<CommandUnit> commandUnits) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();

    Activity activity = Activity.builder()
                            .applicationName(app.getName())
                            .appId(app.getUuid())
                            .commandName(commandName)
                            .type(Type.Command)
                            .workflowType(executionContext.getWorkflowType())
                            .workflowExecutionName(executionContext.getWorkflowExecutionName())
                            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                            .commandType(stateType)
                            .workflowExecutionId(executionContext.getWorkflowExecutionId())
                            .workflowId(executionContext.getWorkflowId())
                            .commandUnits(commandUnits)
                            .status(ExecutionStatus.RUNNING)
                            .commandUnitType(CommandUnitType.KUBERNETES)
                            .environmentId(env.getUuid())
                            .environmentName(env.getName())
                            .environmentType(env.getEnvironmentType())
                            .build();

    return activityService.save(activity);
  }

  public K8sDelegateManifestConfig createDelegateManifestConfig(
      ExecutionContext context, ApplicationManifest appManifest) {
    K8sDelegateManifestConfigBuilder manifestConfigBuilder = K8sDelegateManifestConfig.builder()
                                                                 .manifestStoreTypes(appManifest.getStoreType())
                                                                 .helmCommandFlag(appManifest.getHelmCommandFlag());

    StoreType storeType = appManifest.getStoreType();
    switch (storeType) {
      case Local:
        manifestConfigBuilder.manifestFiles(
            applicationManifestService.getManifestFilesByAppManifestId(appManifest.getAppId(), appManifest.getUuid()));
        break;

      case KustomizeSourceRepo:
        KustomizeConfig kustomizeConfig = appManifest.getKustomizeConfig();
        kustomizeHelper.renderKustomizeConfig(context, kustomizeConfig);
        manifestConfigBuilder.kustomizeConfig(kustomizeConfig);
        prepareRemoteDelegateManifestConfig(context, appManifest, manifestConfigBuilder);
        break;
      case OC_TEMPLATES:
      case Remote:
      case HelmSourceRepo:
        prepareRemoteDelegateManifestConfig(context, appManifest, manifestConfigBuilder);
        break;

      case HelmChartRepo:
        ApplicationManifest appManifestWithChartRepoOverrideApplied =
            applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context);
        appManifest =
            appManifestWithChartRepoOverrideApplied == null ? appManifest : appManifestWithChartRepoOverrideApplied;
        manifestConfigBuilder.helmChartConfigParams(
            helmChartConfigHelperService.getHelmChartConfigTaskParams(context, appManifest));
        break;

      default:
        unhandled(storeType);
    }

    return manifestConfigBuilder.build();
  }

  private void prepareRemoteDelegateManifestConfig(ExecutionContext context, ApplicationManifest appManifest,
      K8sDelegateManifestConfigBuilder manifestConfigBuilder) {
    ApplicationManifest appManifestWithSourceRepoOverrideApplied =
        applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context);
    appManifest =
        appManifestWithSourceRepoOverrideApplied == null ? appManifest : appManifestWithSourceRepoOverrideApplied;
    GitFileConfig gitFileConfig =
        gitFileConfigHelperService.renderGitFileConfig(context, appManifest.getGitFileConfig());
    GitConfig gitConfig = settingsService.fetchGitConfigFromConnectorId(gitFileConfig.getConnectorId());
    notNullCheck("Git config not found", gitConfig);
    gitConfigHelperService.convertToRepoGitConfig(gitConfig, gitFileConfig.getRepoName());

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(gitConfig, appManifest.getAppId(), context.getWorkflowExecutionId());

    if (appManifest.getStoreType() != StoreType.OC_TEMPLATES) {
      // Normalization is done for folders only. This should ideally be done at Delegate side where we know folder/file.
      // Also appending `/` is already taken care by Paths library and we need not do it
      gitFileConfig.setFilePath(normalizeFolderPath(gitFileConfig.getFilePath()));
    }
    manifestConfigBuilder.gitFileConfig(gitFileConfig);
    manifestConfigBuilder.gitConfig(gitConfig);
    manifestConfigBuilder.encryptedDataDetails(encryptionDetails);
  }

  public ExecutionResponse executeGitTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId, String commandName) {
    Application app = appService.get(context.getAppId());
    applicationManifestUtils.populateRemoteGitConfigFilePathList(context, appManifestMap);

    GitFetchFilesTaskParams fetchFilesTaskParams =
        applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap);
    fetchFilesTaskParams.setActivityId(activityId);
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.VALUES);

    applicationManifestUtils.setValuesPathInGitFetchFilesTaskParams(fetchFilesTaskParams);

    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

    String waitId = generateUuid();
    Environment env = K8sStateHelper.fetchEnvFromExecutionContext(context);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(app.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
                                    .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
                                    .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD,
                                        k8sStateHelper.fetchContainerInfrastructureMappingId(context))
                                    .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD,
                                        infraMapping != null ? infraMapping.getServiceId() : null)
                                    .waitId(waitId)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.GIT_FETCH_FILES_TASK.name())
                                              .parameters(new Object[] {fetchFilesTaskParams})
                                              .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                                              .build())
                                    .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
                                    .build();

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTask(delegateTask);

    Map<K8sValuesLocation, Collection<String>> valuesFiles = new HashMap<>();
    K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    if (stateExecutionData != null) {
      valuesFiles.putAll(stateExecutionData.getValuesFiles());
    }

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(K8sStateExecutionData.builder()
                                .activityId(activityId)
                                .commandName(commandName)
                                .currentTaskType(TaskType.GIT_COMMAND)
                                .valuesFiles(valuesFiles)
                                .applicationManifestMap(appManifestMap)
                                .build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  public void updateManifestsArtifactVariableNames(
      String appId, String infraMappingId, Set<String> serviceArtifactVariableNames) {
    InfrastructureMapping infraMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infraMapping == null) {
      throw new InvalidRequestException(
          format("Infra mapping not found for appId %s infraMappingId %s", appId, infraMappingId));
    }

    k8sStateHelper.updateManifestsArtifactVariableNames(
        appId, serviceArtifactVariableNames, infraMapping.getServiceId(), infraMapping.getEnvId());
  }

  public List<String> fetchRenderedValuesFiles(
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap, ExecutionContext context) {
    Map<K8sValuesLocation, Collection<String>> valuesFiles = new HashMap<>();
    K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    if (stateExecutionData != null) {
      valuesFiles.putAll(stateExecutionData.getValuesFiles());
    }

    applicationManifestUtils.populateValuesFilesFromAppManifest(appManifestMap, valuesFiles);

    List<String> result = new ArrayList<>();

    log.info("Found Values at following sources: " + valuesFiles.keySet());

    if (valuesFiles.containsKey(K8sValuesLocation.Service)) {
      addNonEmptyValuesToList(K8sValuesLocation.Service, valuesFiles.get(K8sValuesLocation.Service), result);
    }

    if (valuesFiles.containsKey(K8sValuesLocation.ServiceOverride)) {
      addNonEmptyValuesToList(
          K8sValuesLocation.ServiceOverride, valuesFiles.get(K8sValuesLocation.ServiceOverride), result);
    }

    if (valuesFiles.containsKey(K8sValuesLocation.EnvironmentGlobal)) {
      addNonEmptyValuesToList(
          K8sValuesLocation.EnvironmentGlobal, valuesFiles.get(K8sValuesLocation.EnvironmentGlobal), result);
    }

    if (valuesFiles.containsKey(K8sValuesLocation.Environment)) {
      addNonEmptyValuesToList(K8sValuesLocation.Environment, valuesFiles.get(K8sValuesLocation.Environment), result);
    }

    // OpenShift takes in reverse order
    if (openShiftManagerService.isOpenShiftManifestConfig(context)) {
      Collections.reverse(result);
    }

    return result;
  }

  private void addNonEmptyValuesToList(K8sValuesLocation location, Collection<String> values, List<String> result) {
    for (String value : values) {
      addNonEmptyValueToList(location, value, result);
    }
  }

  private void addNonEmptyValueToList(K8sValuesLocation location, String value, List<String> result) {
    if (isNotBlank(value)) {
      result.add(value);
    } else {
      log.info("Values content is empty in " + location);
    }
  }

  public void saveK8sElement(ExecutionContext context, K8sElement k8sElement) {
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name("k8s")
                                   .output(kryoSerializer.asDeflatedBytes(k8sElement))
                                   .build());
  }

  public ExecutionResponse queueK8sDelegateTask(ExecutionContext context, K8sTaskParameters k8sTaskParameters) {
    Application app = appService.get(context.getAppId());
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    notNullCheck("WorkflowStandardParams should not be null", workflowStandardParams);
    Environment env = workflowStandardParams.getEnv();
    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);
    String serviceTemplateId = serviceTemplateHelper.fetchServiceTemplateId(infraMapping);

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    // NOTE: This is no longer used for multi-artifact. Here for backwards compatibility.
    Artifact artifact = ((DeploymentExecutionContext) context).getArtifactForService(serviceId);
    String artifactStreamId = artifact == null ? null : artifact.getArtifactStreamId();

    K8sClusterConfig k8sClusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(infraMapping, context);
    k8sClusterConfig.setNamespace(context.renderExpression(k8sClusterConfig.getNamespace()));
    KubernetesHelperService.validateNamespace(k8sClusterConfig.getNamespace());

    k8sTaskParameters.setAccountId(app.getAccountId());
    k8sTaskParameters.setAppId(app.getUuid());
    k8sTaskParameters.setK8sClusterConfig(k8sClusterConfig);
    k8sTaskParameters.setWorkflowExecutionId(context.getWorkflowExecutionId());
    k8sTaskParameters.setHelmVersion(serviceResourceService.getHelmVersionWithDefault(context.getAppId(), serviceId));

    long taskTimeoutInMillis = DEFAULT_ASYNC_CALL_TIMEOUT;

    if (k8sTaskParameters.getTimeoutIntervalInMin() != null) {
      long taskTimeoutInMinutes;
      if (k8sTaskParameters.getTimeoutIntervalInMin() < MIN_TASK_TIMEOUT_IN_MINUTES) {
        taskTimeoutInMinutes = MIN_TASK_TIMEOUT_IN_MINUTES;
      } else {
        taskTimeoutInMinutes = k8sTaskParameters.getTimeoutIntervalInMin();
      }

      taskTimeoutInMillis = taskTimeoutInMinutes * 60L * 1000L;
    }

    List tags = new ArrayList();
    tags.addAll(awsCommandHelper.getAwsConfigTagsFromK8sConfig(k8sTaskParameters));

    String waitId = generateUuid();
    int expressionFunctorToken = HashGenerator.generateIntegerHash();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .waitId(waitId)
            .tags(tags)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.K8S_COMMAND_TASK.name())
                      .parameters(new Object[] {k8sTaskParameters})
                      .timeout(taskTimeoutInMillis)
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infraMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, infraMapping.getServiceId())
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, serviceTemplateId)
            .setupAbstraction(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, artifactStreamId)
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    K8sStateExecutionData stateExecutionData =
        K8sStateExecutionData.builder()
            .activityId(k8sTaskParameters.getActivityId())
            .commandName(k8sTaskParameters.getCommandName())
            .namespace(k8sTaskParameters.getK8sClusterConfig().getNamespace())
            .clusterName(k8sTaskParameters.getK8sClusterConfig().getClusterName())
            .cloudProvider(k8sTaskParameters.getK8sClusterConfig().getCloudProviderName())
            .releaseName(k8sTaskParameters.getReleaseName())
            .currentTaskType(TaskType.K8S_COMMAND_TASK)
            .build();

    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    context.resetPreparedCache();
    if (delegateTask.getData().getParameters().length == 1
        && delegateTask.getData().getParameters()[0] instanceof TaskParameters) {
      delegateTask.setWorkflowExecutionId(context.getWorkflowExecutionId());
      ExpressionReflectionUtils.applyExpression(delegateTask.getData().getParameters()[0],
          (secretMode, value) -> context.renderExpression(value, stateExecutionContext));
    }

    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();
    stateExecutionData.setReleaseName(
        expressionEvaluator.substitute(k8sTaskParameters.getReleaseName(), Collections.emptyMap()));

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTask(delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(stateExecutionData)
        .delegateTaskId(delegateTaskId)
        .build();
  }

  public String fetchAppId(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    return workflowStandardParams.getAppId();
  }

  public String fetchActivityId(ExecutionContext context) {
    return ((K8sStateExecutionData) context.getStateExecutionData()).getActivityId();
  }

  public ExecutionResponse executeWrapperWithManifest(
      K8sStateExecutor k8sStateExecutor, ExecutionContext context, long timeoutInMillis) {
    try {
      k8sStateExecutor.validateParameters(context);
      boolean valuesInGit = false;
      boolean valuesInHelmChartRepo = false;
      boolean kustomizeSource = false;
      boolean remoteParams = false;
      boolean ocTemplateSource = false;

      Activity activity;
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap;
      if (openShiftManagerService.isOpenShiftManifestConfig(context)) {
        ocTemplateSource = true;
        appManifestMap = applicationManifestUtils.getOverrideApplicationManifests(context, AppManifestKind.OC_PARAMS);
        if (isNotEmpty(appManifestMap) && isValuesInGit(appManifestMap)) {
          remoteParams = true;
        }
      } else {
        appManifestMap = applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);

        valuesInGit = isValuesInGit(appManifestMap);
        valuesInHelmChartRepo = applicationManifestUtils.isValuesInHelmChartRepo(context);
        kustomizeSource = applicationManifestUtils.isKustomizeSource(context);
      }
      activity =
          createK8sActivity(context, k8sStateExecutor.commandName(), k8sStateExecutor.stateType(), activityService,
              k8sStateExecutor.commandUnitList(
                  valuesInGit || valuesInHelmChartRepo || kustomizeSource || ocTemplateSource));

      if (valuesInHelmChartRepo) {
        return executeHelmValuesFetchTask(context, activity.getUuid(), k8sStateExecutor.commandName(), timeoutInMillis);
      } else if (valuesInGit || remoteParams) {
        return executeGitTask(context, appManifestMap, activity.getUuid(), k8sStateExecutor.commandName());
      } else {
        return k8sStateExecutor.executeK8sTask(context, activity.getUuid());
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  public ExecutionResponse handleAsyncResponseWrapper(
      K8sStateExecutor k8sStateExecutor, ExecutionContext context, Map<String, ResponseData> response) {
    try {
      K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();

      TaskType taskType = k8sStateExecutionData.getCurrentTaskType();
      switch (taskType) {
        case HELM_VALUES_FETCH:
          return handleAsyncResponseForHelmFetchTask(k8sStateExecutor, context, response);

        case GIT_COMMAND:
          return handleAsyncResponseForGitTaskWrapper(k8sStateExecutor, context, response);

        case K8S_COMMAND_TASK:
          return k8sStateExecutor.handleAsyncResponseForK8sTask(context, response);

        default:
          throw new WingsException("Unhandled task type " + taskType);
      }

    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  public InstanceElementListParam fetchInstanceElementListParam(List<K8sPod> podDetailsList) {
    return InstanceElementListParam.builder().instanceElements(fetchInstanceElementList(podDetailsList, false)).build();
  }

  public List<InstanceStatusSummary> fetchInstanceStatusSummaries(
      List<InstanceElement> instanceElementList, ExecutionStatus executionStatus) {
    return instanceElementList.stream()
        .map(instanceElement
            -> anInstanceStatusSummary().withInstanceElement(instanceElement).withStatus(executionStatus).build())
        .collect(Collectors.toList());
  }

  public List<K8sPod> tryGetPodList(
      ContainerInfrastructureMapping containerInfrastructureMapping, String namespace, String releaseName) {
    try {
      return k8sStateHelper.fetchPodList(containerInfrastructureMapping, namespace, releaseName);
    } catch (Exception e) {
      log.info("Failed to fetch PodList for release {}. Exception: {}.", releaseName, e);
    }
    return null;
  }

  List<InstanceElement> fetchInstanceElementList(List<K8sPod> podList, boolean treatAllPodsAsNew) {
    if (isEmpty(podList)) {
      return Collections.emptyList();
    }

    return podList.stream()
        .map(podDetails -> {
          return anInstanceElement()
              .uuid(podDetails.getName())
              .host(HostElement.builder().hostName(podDetails.getName()).ip(podDetails.getPodIP()).build())
              .hostName(podDetails.getName())
              .displayName(podDetails.getName())
              .podName(podDetails.getName())
              .newInstance(treatAllPodsAsNew || podDetails.isNewPod())
              .build();
        })
        .collect(Collectors.toList());
  }

  private boolean isValuesInGit(Map<K8sValuesLocation, ApplicationManifest> appManifestMap) {
    for (Entry<K8sValuesLocation, ApplicationManifest> entry : appManifestMap.entrySet()) {
      ApplicationManifest applicationManifest = entry.getValue();
      if (StoreType.Remote == applicationManifest.getStoreType()
          || StoreType.HelmSourceRepo == applicationManifest.getStoreType()) {
        return true;
      }
    }

    return false;
  }

  private ExecutionResponse handleAsyncResponseForGitTaskWrapper(
      K8sStateExecutor k8sStateExecutor, ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = fetchActivityId(context);
    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getGitCommandStatus() == GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = k8sStateExecutionData.getApplicationManifestMap();
    applicationManifestUtils.renderGitConfigForApplicationManifest(context, appManifestMap);
    Map<K8sValuesLocation, Collection<String>> valuesFiles =
        applicationManifestUtils.getValuesFilesFromGitFetchFilesResponse(appManifestMap, executionResponse);
    k8sStateExecutionData.getValuesFiles().putAll(valuesFiles);

    return k8sStateExecutor.executeK8sTask(context, activityId);
  }

  public String fetchReleaseName(ExecutionContext context, ContainerInfrastructureMapping infraMapping) {
    String releaseName = infraMapping.getReleaseName();

    if (isBlank(releaseName)) {
      releaseName = convertBase64UuidToCanonicalForm(infraMapping.getUuid());
    }
    validateReleaseName(releaseName, context);

    return releaseName;
  }

  private void validateReleaseName(String expression, ExecutionContext context) {
    String releaseName = context.renderExpression(expression);
    if (!ExpressionEvaluator.containsVariablePattern(releaseName)) {
      try {
        new ConfigMapBuilder().withNewMetadata().withName(releaseName).endMetadata().build();
      } catch (Exception e) {
        String maskedReleaseName = renderMaskedExpression(expression, context);
        throw new InvalidArgumentsException(
            Pair.of("Release name",
                "\"" + maskedReleaseName
                    + "\" is an invalid name. Release name may only contain lowercase letters, numbers, and '-'."),
            e, USER);
      }
    }
  }

  private String renderMaskedExpression(String expression, ExecutionContext context) {
    context.resetPreparedCache();
    String renderedExpression =
        context.renderExpression(expression, StateExecutionContext.builder().adoptDelegateDecryption(true).build());
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();
    return expressionEvaluator.substitute(renderedExpression, Collections.emptyMap());
  }

  private HelmValuesFetchTaskParameters fetchHelmValuesFetchTaskParameters(
      ExecutionContext context, String activityId, long timeoutInMillis) {
    ApplicationManifest applicationManifest =
        applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context);
    if (applicationManifest == null || HelmChartRepo != applicationManifest.getStoreType()) {
      throw new InvalidRequestException(
          "Application Manifest not found while preparing helm values fetch task params", USER);
    }

    return HelmValuesFetchTaskParameters.builder()
        .accountId(context.getAccountId())
        .appId(context.getAppId())
        .activityId(activityId)
        .helmChartConfigTaskParams(
            helmChartConfigHelperService.getHelmChartConfigTaskParams(context, applicationManifest))
        .timeoutInMillis(timeoutInMillis)
        .workflowExecutionId(context.getWorkflowExecutionId())
        .helmCommandFlag(applicationManifest.getHelmCommandFlag())
        .mergeCapabilities(featureFlagService.isEnabled(FeatureName.HELM_MERGE_CAPABILITIES, context.getAccountId()))
        .build();
  }

  private ExecutionResponse handleAsyncResponseForHelmFetchTask(
      K8sStateExecutor k8sStateExecutor, ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = fetchActivityId(context);
    HelmValuesFetchTaskResponse executionResponse = (HelmValuesFetchTaskResponse) response.values().iterator().next();
    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    if (isNotBlank(executionResponse.getValuesFileContent())) {
      K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
      k8sStateExecutionData.getValuesFiles().put(
          K8sValuesLocation.Service, singletonList(executionResponse.getValuesFileContent()));
    }

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);

    boolean valuesInGit = isValuesInGit(appManifestMap);
    if (valuesInGit) {
      return executeGitTask(context, appManifestMap, activityId, k8sStateExecutor.commandName());
    } else {
      return k8sStateExecutor.executeK8sTask(context, activityId);
    }
  }

  public ExecutionResponse executeHelmValuesFetchTask(
      ExecutionContext context, String activityId, String commandName, long timeoutInMillis) {
    Application app = appService.get(context.getAppId());
    HelmValuesFetchTaskParameters helmValuesFetchTaskParameters =
        fetchHelmValuesFetchTaskParameters(context, activityId, timeoutInMillis);

    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);
    String serviceTemplateId = serviceTemplateHelper.fetchServiceTemplateId(infraMapping);

    String waitId = generateUuid();
    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    Environment env = K8sStateHelper.fetchEnvFromExecutionContext(context);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(app.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
                                    .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
                                    .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD,
                                        k8sStateHelper.fetchContainerInfrastructureMappingId(context))
                                    .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, serviceTemplateId)
                                    .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD,
                                        infraMapping != null ? infraMapping.getServiceId() : null)
                                    .waitId(waitId)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HELM_VALUES_FETCH.name())
                                              .parameters(new Object[] {helmValuesFetchTaskParameters})
                                              .timeout(TimeUnit.MINUTES.toMillis(10))
                                              .expressionFunctorToken(expressionFunctorToken)
                                              .build())
                                    .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
                                    .build();

    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder()
                                                   .activityId(activityId)
                                                   .commandName(commandName)
                                                   .currentTaskType(TaskType.HELM_VALUES_FETCH)
                                                   .build();

    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    context.resetPreparedCache();
    if (delegateTask.getData().getParameters().length == 1
        && delegateTask.getData().getParameters()[0] instanceof TaskParameters) {
      delegateTask.setWorkflowExecutionId(context.getWorkflowExecutionId());
      ExpressionReflectionUtils.applyExpression(delegateTask.getData().getParameters()[0],
          (secretMode, value) -> context.renderExpression(value, stateExecutionContext));
    }

    appendDelegateTaskDetails(context, delegateTask);
    String delegateTaskId = delegateService.queueTask(delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(waitId))
        .stateExecutionData(stateExecutionData)
        .delegateTaskId(delegateTaskId)
        .build();
  }

  public Set<String> fetchNamespacesFromK8sPodList(List<K8sPod> k8sPodList) {
    if (isEmpty(k8sPodList)) {
      return new HashSet<>();
    }

    return k8sPodList.stream().map(K8sPod::getNamespace).collect(Collectors.toSet());
  }

  public void validateK8sV2TypeServiceUsed(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Service service = serviceResourceService.get(serviceElement.getUuid());

    if (!service.isK8sV2()) {
      throw new InvalidRequestException(format(
          "Service %s used in workflow is of incompatible type. Use Kubernetes V2 type service", service.getName()));
    }
  }

  @Nonnull
  public List<K8sPod> fetchNewPods(@Nullable List<K8sPod> k8sPodList) {
    return emptyIfNull(k8sPodList).stream().filter(K8sPod::isNewPod).collect(Collectors.toList());
  }

  @Nonnull
  List<InstanceDetails> fetchInstanceDetails(@Nullable List<K8sPod> pods, boolean treatAllPodsAsNew) {
    return emptyIfNull(pods)
        .stream()
        .map(pod
            -> InstanceDetails.builder()
                   .instanceType(InstanceType.K8s)
                   .hostName(pod.getName())
                   .newInstance(treatAllPodsAsNew || pod.isNewPod())
                   .k8s(K8s.builder().podName(pod.getName()).ip(pod.getPodIP()).build())
                   .build())
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  void saveInstanceInfoToSweepingOutput(
      ExecutionContext context, List<InstanceElement> instanceElements, List<InstanceDetails> instanceDetails) {
    boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                   .value(InstanceInfoVariables.builder()
                                              .instanceElements(instanceElements)
                                              .instanceDetails(instanceDetails)
                                              .skipVerification(skipVerification)
                                              .build())
                                   .build());
  }

  public Map<K8sValuesLocation, ApplicationManifest> fetchApplicationManifests(ExecutionContext context) {
    boolean isOpenShiftManifestConfig = openShiftManagerService.isOpenShiftManifestConfig(context);
    AppManifestKind appManifestKind = isOpenShiftManifestConfig ? AppManifestKind.OC_PARAMS : AppManifestKind.VALUES;
    return applicationManifestUtils.getApplicationManifests(context, appManifestKind);
  }

  public K8sHelmDeploymentElement fetchK8sHelmDeploymentElement(ExecutionContext context) {
    SweepingOutputInquiry sweepingOutputInquiry =
        context.prepareSweepingOutputInquiryBuilder().name(K8sHelmDeploymentElement.SWEEPING_OUTPUT_NAME).build();

    return sweepingOutputService.findSweepingOutput(sweepingOutputInquiry);
  }

  public void storePreviousHelmDeploymentInfo(ExecutionContext context, ApplicationManifest manifest) {
    if (StoreType.HelmChartRepo != manifest.getStoreType() && StoreType.HelmSourceRepo != manifest.getStoreType()) {
      return;
    }

    if (fetchK8sHelmDeploymentElement(context) == null) {
      String infrastructureMappingId = k8sStateHelper.fetchContainerInfrastructureMappingId(context);
      String appId = context.getAppId();
      HelmChartInfo latestHelmChartInfo = fetchLatestDeployedChartInfo(appId, infrastructureMappingId);
      saveK8sHelmDeploymentElement(
          context, K8sHelmDeploymentElement.builder().previousDeployedHelmChart(latestHelmChartInfo).build());
    }
  }

  private HelmChartInfo fetchLatestDeployedChartInfo(String appId, String infraMappingId) {
    return instanceService.getInstancesForAppAndInframapping(appId, infraMappingId)
        .stream()
        .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
        .map(Instance::getInstanceInfo)
        .filter(K8sPodInfo.class ::isInstance)
        .map(K8sPodInfo.class ::cast)
        .map(K8sPodInfo::getHelmChartInfo)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private void saveK8sHelmDeploymentElement(
      ExecutionContext context, K8sHelmDeploymentElement k8SHelmDeploymentElement) {
    log.info("Storing {} in sweeping output", K8sHelmDeploymentElement.SWEEPING_OUTPUT_NAME);
    // Just ensure that element exists in Sweeping output. The element could be already stored by another running in
    // parallel step
    sweepingOutputService.ensure(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                     .name(K8sHelmDeploymentElement.SWEEPING_OUTPUT_NAME)
                                     .value(k8SHelmDeploymentElement)
                                     .build());
  }

  @Override public abstract ExecutionResponse execute(ExecutionContext context);

  @Override public abstract void handleAbortEvent(ExecutionContext context);

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
