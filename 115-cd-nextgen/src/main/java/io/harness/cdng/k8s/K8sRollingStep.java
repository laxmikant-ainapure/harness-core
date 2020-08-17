package io.harness.cdng.k8s;

import static io.harness.cdng.orchestration.StepUtils.prepareDelegateTaskInput;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import io.harness.ambiance.Ambiance;
import io.harness.cdng.common.AmbianceHelper;
import io.harness.cdng.executionplan.CDStepDependencyKey;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.StoreConfig;
import io.harness.cdng.manifest.yaml.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.stepsdependency.utils.CDStepDependencyUtils;
import io.harness.cdng.tasks.manifestFetch.beans.GitFetchFilesConfig;
import io.harness.cdng.tasks.manifestFetch.beans.GitFetchRequest;
import io.harness.delegate.beans.ResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.engine.expressions.EngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.execution.status.Status;
import io.harness.executionplan.stepsdependency.StepDependencyService;
import io.harness.executionplan.stepsdependency.StepDependencySpec;
import io.harness.facilitator.PassThroughData;
import io.harness.facilitator.modes.chain.task.TaskChainExecutable;
import io.harness.facilitator.modes.chain.task.TaskChainResponse;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.state.Step;
import io.harness.state.StepType;
import io.harness.state.io.FailureInfo;
import io.harness.state.io.StepInputPackage;
import io.harness.state.io.StepResponse;
import io.harness.tasks.Task;
import io.harness.validation.Validator;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import software.wings.beans.GitConfig;
import software.wings.beans.TaskType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.sm.states.k8s.K8sRollingDeploy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class K8sRollingStep implements Step, TaskChainExecutable<K8sRollingStepParameters> {
  public static final StepType STEP_TYPE = StepType.builder().type("K8S_ROLLING").build();

  @Inject private EngineExpressionService engineExpressionService;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private StepDependencyService stepDependencyService;

  @Override
  public TaskChainResponse startChainLink(
      Ambiance ambiance, K8sRollingStepParameters k8sRollingStepParameters, StepInputPackage inputPackage) {
    StepDependencySpec serviceSpec =
        k8sRollingStepParameters.getStepDependencySpecs().get(CDStepDependencyKey.SERVICE.name());
    ServiceOutcome serviceOutcome = CDStepDependencyUtils.getService(
        stepDependencyService, serviceSpec, inputPackage, k8sRollingStepParameters, ambiance);

    StepDependencySpec infraSpec =
        k8sRollingStepParameters.getStepDependencySpecs().get(CDStepDependencyKey.INFRASTRUCTURE.name());

    Infrastructure infrastructure = CDStepDependencyUtils.getInfrastructure(
        stepDependencyService, infraSpec, inputPackage, k8sRollingStepParameters, ambiance);

    List<ManifestAttributes> manifests = serviceOutcome.getManifests();
    Validator.notEmptyCheck("Manifests can't be empty", manifests);

    K8sManifest k8sManifest = getK8sManifest(manifests);
    List<ValuesManifest> aggregatedValuesManifests = getAggregatedValuesManifests(manifests);

    if (isEmpty(aggregatedValuesManifests)) {
      return executeK8sTask(k8sManifest, ambiance, k8sRollingStepParameters, Collections.emptyList(), infrastructure);
    }

    if (!isAnyRemoteStore(aggregatedValuesManifests)) {
      List<String> valuesFileContentsForLocalStore = getValuesFileContentsForLocalStore(aggregatedValuesManifests);
      return executeK8sTask(
          k8sManifest, ambiance, k8sRollingStepParameters, valuesFileContentsForLocalStore, infrastructure);
    }

    return executeValuesFetchTask(
        ambiance, k8sRollingStepParameters, infrastructure, k8sManifest, aggregatedValuesManifests);
  }

  private TaskChainResponse executeValuesFetchTask(Ambiance ambiance, K8sRollingStepParameters k8sRollingStepParameters,
      Infrastructure infrastructure, K8sManifest k8sManifest, List<ValuesManifest> aggregatedValuesManifests) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();

    for (ValuesManifest valuesManifest : aggregatedValuesManifests) {
      if (ManifestStoreType.GIT.equals(valuesManifest.getStoreConfigWrapper().getStoreConfig().getKind())) {
        GitStore gitStore = (GitStore) valuesManifest.getStoreConfigWrapper().getStoreConfig();
        String connectorId = gitStore.getConnectorIdentifier();
        GitConfig gitConfig = (GitConfig) k8sStepHelper.getSettingAttribute(connectorId, ambiance).getValue();

        List<EncryptedDataDetail> encryptionDetails = k8sStepHelper.getEncryptedDataDetails(gitConfig);
        gitFetchFilesConfigs.add(GitFetchFilesConfig.builder()
                                     .encryptedDataDetails(encryptionDetails)
                                     .gitConfig(gitConfig)
                                     .gitStore(gitStore)
                                     .identifier(valuesManifest.getIdentifier())
                                     .paths(gitStore.getPaths())
                                     .succeedIfFileNotFound(false)
                                     .build());
      }
    }

    String accountId = AmbianceHelper.getAccountId(ambiance);
    GitFetchRequest gitFetchRequest =
        GitFetchRequest.builder().gitFetchFilesConfigs(gitFetchFilesConfigs).accountId(accountId).build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(k8sRollingStepParameters.getTimeout())
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    final Task delegateTask = prepareDelegateTaskInput(accountId, taskData, ambiance.getSetupAbstractions());

    K8sRollingStepPassThroughData k8sRollingStepPassThroughData = K8sRollingStepPassThroughData.builder()
                                                                      .k8sManifest(k8sManifest)
                                                                      .valuesManifests(aggregatedValuesManifests)
                                                                      .infrastructure(infrastructure)
                                                                      .build();
    return TaskChainResponse.builder()
        .chainEnd(false)
        .task(delegateTask)
        .passThroughData(k8sRollingStepPassThroughData)
        .build();
  }

  private List<String> getValuesFileContentsForLocalStore(List<ValuesManifest> aggregatedValuesManifests) {
    // TODO: implement when local store is available
    return Collections.emptyList();
  }

  private TaskChainResponse executeK8sTask(K8sManifest k8sManifest, Ambiance ambiance,
      K8sRollingStepParameters stepParameters, List<String> valuesFileContents, Infrastructure infrastructure) {
    List<String> renderedValuesList = renderValues(ambiance, valuesFileContents);
    StoreConfig storeConfig = k8sManifest.getStoreConfigWrapper().getStoreConfig();

    String releaseName = k8sStepHelper.getReleaseName(infrastructure);

    final String accountId = AmbianceHelper.getAccountId(ambiance);
    K8sRollingDeployRequest k8sRollingDeployRequest =
        K8sRollingDeployRequest.builder()
            .skipDryRun(stepParameters.isSkipDryRun())
            .inCanaryWorkflow(false)
            .releaseName(releaseName)
            .commandName(K8sRollingDeploy.K8S_ROLLING_DEPLOY_COMMAND_NAME)
            .taskType(K8sTaskType.DEPLOYMENT_ROLLING)
            .localOverrideFeatureFlag(false)
            .timeoutIntervalInMin(stepParameters.getTimeout())
            .valuesYamlList(renderedValuesList)
            .k8sInfraDelegateConfig(k8sStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .manifestDelegateConfig(k8sStepHelper.getManifestDelegateConfig(storeConfig, ambiance))
            .build();

    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {k8sRollingDeployRequest})
                            .taskType(TaskType.K8S_COMMAND_TASK_NG.name())
                            .timeout(stepParameters.getTimeout())
                            .async(true)
                            .build();

    final Task delegateTask = prepareDelegateTaskInput(accountId, taskData, ambiance.getSetupAbstractions());

    return TaskChainResponse.builder().task(delegateTask).chainEnd(true).passThroughData(infrastructure).build();
  }

  private List<String> renderValues(Ambiance ambiance, List<String> valuesFileContents) {
    if (isEmpty(valuesFileContents)) {
      return Collections.emptyList();
    }

    return valuesFileContents.stream()
        .map(valuesFileContent -> engineExpressionService.renderExpression(ambiance, valuesFileContent))
        .collect(Collectors.toList());
  }

  private boolean isAnyRemoteStore(@NotEmpty List<ValuesManifest> aggregatedValuesManifests) {
    return aggregatedValuesManifests.stream().anyMatch(valuesManifest
        -> ManifestStoreType.GIT.equals(valuesManifest.getStoreConfigWrapper().getStoreConfig().getKind()));
  }

  @VisibleForTesting
  List<ValuesManifest> getAggregatedValuesManifests(@NotEmpty List<ManifestAttributes> manifestAttributesList) {
    List<ValuesManifest> aggregateValuesManifests = new ArrayList<>();

    addValuesEntryForK8ManifestIfNeeded(manifestAttributesList, aggregateValuesManifests);

    List<ValuesManifest> serviceValuesManifests =
        manifestAttributesList.stream()
            .filter(manifestAttribute -> ManifestType.VALUES.equals(manifestAttribute.getKind()))
            .map(manifestAttribute -> (ValuesManifest) manifestAttribute)
            .collect(Collectors.toList());

    if (isNotEmpty(serviceValuesManifests)) {
      aggregateValuesManifests.addAll(serviceValuesManifests);
    }
    return aggregateValuesManifests;
  }

  private void addValuesEntryForK8ManifestIfNeeded(
      List<ManifestAttributes> serviceManifests, List<ValuesManifest> aggregateValuesManifests) {
    K8sManifest k8sManifest =
        (K8sManifest) serviceManifests.stream()
            .filter(manifestAttribute -> ManifestType.K8Manifest.equals(manifestAttribute.getKind()))
            .findFirst()
            .orElse(null);

    if (k8sManifest != null && isNotEmpty(k8sManifest.getValuesFilePaths())
        && ManifestStoreType.GIT.equals(k8sManifest.getStoreConfigWrapper().getStoreConfig().getKind())) {
      GitStore gitStore = (GitStore) k8sManifest.getStoreConfigWrapper().getStoreConfig();
      ValuesManifest valuesManifest =
          ValuesManifest.builder()
              .identifier(k8sManifest.getIdentifier())
              .storeConfigWrapper(StoreConfigWrapper.builder().storeConfig(gitStore.cloneInternal()).build())
              .build();

      ((GitStore) valuesManifest.getStoreConfigWrapper().getStoreConfig()).setPaths(k8sManifest.getValuesFilePaths());

      aggregateValuesManifests.add(valuesManifest);
    }
  }

  K8sManifest getK8sManifest(@NotEmpty List<ManifestAttributes> manifestAttributes) {
    List<ManifestAttributes> k8sManifests =
        manifestAttributes.stream()
            .filter(manifestAttribute -> ManifestType.K8Manifest.equals(manifestAttribute.getKind()))
            .collect(Collectors.toList());

    if (isEmpty(k8sManifests)) {
      throw new InvalidRequestException("K8s Manifests are mandatory for k8s Rolling step", WingsException.USER);
    }

    if (k8sManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single K8s manifest", WingsException.USER);
    }
    return (K8sManifest) k8sManifests.get(0);
  }

  @Override
  public TaskChainResponse executeNextLink(Ambiance ambiance, K8sRollingStepParameters k8sRollingStepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, Map<String, ResponseData> responseDataMap) {
    GitCommandExecutionResponse gitTaskResponse =
        (GitCommandExecutionResponse) responseDataMap.values().iterator().next();

    if (gitTaskResponse.getGitCommandStatus() != GitCommandExecutionResponse.GitCommandStatus.SUCCESS) {
      throw new InvalidRequestException(gitTaskResponse.getErrorMessage());
    }
    Map<String, GitFetchFilesResult> gitFetchFilesResultMap =
        ((GitFetchFilesFromMultipleRepoResult) gitTaskResponse.getGitCommandResult()).getFilesFromMultipleRepo();

    K8sRollingStepPassThroughData k8sRollingPassThroughData = (K8sRollingStepPassThroughData) passThroughData;

    K8sManifest k8sManifest = k8sRollingPassThroughData.getK8sManifest();
    List<ValuesManifest> valuesManifests = k8sRollingPassThroughData.getValuesManifests();

    List<String> valuesFileContents = getFileContents(gitFetchFilesResultMap, valuesManifests);

    return executeK8sTask(k8sManifest, ambiance, k8sRollingStepParameters, valuesFileContents,
        k8sRollingPassThroughData.getInfrastructure());
  }

  @NotNull
  List<String> getFileContents(
      Map<String, GitFetchFilesResult> gitFetchFilesResultMap, List<ValuesManifest> valuesManifests) {
    List<String> valuesFileContents = new ArrayList<>();

    for (ValuesManifest valuesManifest : valuesManifests) {
      if (ManifestStoreType.GIT.equals(valuesManifest.getStoreConfigWrapper().getStoreConfig().getKind())) {
        GitFetchFilesResult gitFetchFilesResult = gitFetchFilesResultMap.get(valuesManifest.getIdentifier());
        valuesFileContents.addAll(
            gitFetchFilesResult.getFiles().stream().map(GitFile::getFileContent).collect(Collectors.toList()));
      }
      // TODO: for local store, add files directly
    }
    return valuesFileContents;
  }

  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, K8sRollingStepParameters k8sRollingStepParameters,
      PassThroughData passThroughData, Map<String, ResponseData> responseDataMap) {
    K8sTaskExecutionResponse k8sTaskExecutionResponse =
        (K8sTaskExecutionResponse) responseDataMap.values().iterator().next();

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      Infrastructure infrastructure = (Infrastructure) passThroughData;
      K8sRollingDeployResponse k8sTaskResponse =
          (K8sRollingDeployResponse) k8sTaskExecutionResponse.getK8sTaskResponse();

      K8sRollingOutcome k8sRollingOutcome = K8sRollingOutcome.builder()
                                                .releaseName(k8sStepHelper.getReleaseName(infrastructure))
                                                .releaseNumber(k8sTaskResponse.getReleaseNumber())
                                                .build();

      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.K8S_ROLL_OUT.getName())
                           .outcome(k8sRollingOutcome)
                           .build())
          .build();
    } else {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.builder().errorMessage(k8sTaskExecutionResponse.getErrorMessage()).build())
          .build();
    }
  }
}
