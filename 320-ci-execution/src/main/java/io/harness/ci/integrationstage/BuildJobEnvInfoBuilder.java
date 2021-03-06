package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameterWithDefaultValue;
import static io.harness.common.CICommonPodConstants.MOUNT_PATH;
import static io.harness.common.CICommonPodConstants.STEP_EXEC;
import static io.harness.common.CIExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.common.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS;
import static io.harness.common.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.common.CIExecutionConstants.STEP_PREFIX;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo.ConnectorConversionInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.PublishStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.publish.artifact.Artifact;
import io.harness.beans.yaml.extended.container.ContainerResource;
import io.harness.beans.yaml.extended.container.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.yaml.extended.container.quantity.unit.MemoryQuantityUnit;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.utils.QuantityUtils;
import io.harness.common.CICommonPodConstants;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.stateutils.buildstate.PluginSettingUtils;
import io.harness.stateutils.buildstate.providers.StepContainerUtils;
import io.harness.util.ExceptionUtility;
import io.harness.util.PortFinder;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.TimeoutUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@Singleton
@Slf4j
public class BuildJobEnvInfoBuilder {
  private static final String USERNAME_PREFIX = "USERNAME_";
  private static final String PASSW_PREFIX = "PASSWORD_";
  private static final String ENDPOINT_PREFIX = "ENDPOINT_";
  private static final String ACCESS_KEY_PREFIX = "ACCESS_KEY_";
  private static final String SECRET_KEY_PREFIX = "SECRET_KEY_";
  private static final String SECRET_PATH_PREFIX = "SECRET_PATH_";

  private static final String PLUGIN_USERNAME = "PLUGIN_USERNAME";
  private static final String PLUGIN_PASSW = "PLUGIN_PASSWORD";
  private static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  private static final String PLUGIN_ACCESS_KEY = "PLUGIN_ACCESS_KEY";
  private static final String PLUGIN_SECRET_KEY = "PLUGIN_SECRET_KEY";
  private static final String PLUGIN_JSON_KEY = "PLUGIN_JSON_KEY";
  private static final String PLUGIN_URL = "PLUGIN_URL";

  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  public BuildJobEnvInfo getCIBuildJobEnvInfo(StageElementConfig stageElementConfig, CIExecutionArgs ciExecutionArgs,
      List<ExecutionWrapperConfig> steps, boolean isFirstPod, String podName) {
    log.info("Creating CI Build Job info for integration stage {} and pod name {}", stageElementConfig.getIdentifier(),
        podName);
    // TODO Only kubernetes is supported currently
    IntegrationStageConfig integrationStage = (IntegrationStageConfig) stageElementConfig.getStageType();
    if (integrationStage.getInfrastructure() == null) {
      throw new CIStageExecutionException("Input infrastructure is not set");
    }

    Infrastructure infrastructure = integrationStage.getInfrastructure();
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT
        || infrastructure.getType() == Type.USE_FROM_STAGE) {
      return K8BuildJobEnvInfo.builder()
          .podsSetupInfo(getCIPodsSetupInfo(stageElementConfig, ciExecutionArgs, steps, isFirstPod, podName))
          .workDir(CICommonPodConstants.STEP_EXEC_WORKING_DIR)
          .publishArtifactStepIds(getPublishArtifactStepIds(stageElementConfig))
          .stepConnectorRefs(getStepConnectorRefs(stageElementConfig))
          .build();
    } else {
      throw new IllegalArgumentException("Input infrastructure type is not of type kubernetes");
    }
  }

  private K8BuildJobEnvInfo.PodsSetupInfo getCIPodsSetupInfo(StageElementConfig stageElementConfig,
      CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps, boolean isFirstPod, String podName) {
    List<PodSetupInfo> pods = new ArrayList<>();

    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    String workDirPath = getWorkingDirectoryPath(CICommonPodConstants.STEP_EXEC_WORKING_DIR);
    List<ContainerDefinitionInfo> serviceContainerDefinitionInfos =
        CIServiceBuilder.createServicesContainerDefinition(stageElementConfig, portFinder, ciExecutionServiceConfig);
    List<ContainerDefinitionInfo> stepContainerDefinitionInfos =
        createStepsContainerDefinition(steps, stageElementConfig, ciExecutionArgs, portFinder);

    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    containerDefinitionInfos.addAll(serviceContainerDefinitionInfos);
    containerDefinitionInfos.addAll(stepContainerDefinitionInfos);

    Integer stageMemoryRequest = getStageMemoryRequest(steps);
    Integer stageCpuRequest = getStageCpuRequest(steps);
    List<String> serviceIdList = CIServiceBuilder.getServiceIdList(stageElementConfig);
    List<Integer> serviceGrpcPortList = CIServiceBuilder.getServiceGrpcPortList(stageElementConfig);

    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    if (integrationStageConfig.getSharedPaths().isExpression()) {
      ExceptionUtility.throwUnresolvedExpressionException(integrationStageConfig.getSharedPaths().getExpressionValue(),
          "sharedPath", "stage with identifier: " + stageElementConfig.getIdentifier());
    }
    Map<String, String> volumeToMountPath =
        getVolumeToMountPath((List<String>) integrationStageConfig.getSharedPaths().fetchFinalValue());
    pods.add(PodSetupInfo.builder()
                 .podSetupParams(
                     PodSetupInfo.PodSetupParams.builder().containerDefinitionInfos(containerDefinitionInfos).build())
                 .name(podName)
                 .pvcParamsList(createPVCParams(isFirstPod, podName, volumeToMountPath))
                 .volumeToMountPath(volumeToMountPath)
                 .stageCpuRequest(stageCpuRequest)
                 .stageMemoryRequest(stageMemoryRequest)
                 .serviceIdList(serviceIdList)
                 .serviceGrpcPortList(serviceGrpcPortList)
                 .workDirPath(workDirPath)
                 .build());
    return K8BuildJobEnvInfo.PodsSetupInfo.builder().podSetupInfoList(pods).build();
  }

  private List<PVCParams> createPVCParams(boolean isFirstPod, String podName, Map<String, String> volumeToMountPath) {
    List<PVCParams> pvcParamsList = new ArrayList<>();

    for (String volumeName : volumeToMountPath.keySet()) {
      String claimName = String.format("%s-%s", podName, volumeName);
      pvcParamsList.add(PVCParams.builder()
                            .claimName(claimName)
                            .volumeName(volumeName)
                            .isPresent(!isFirstPod)
                            .sizeMib(ciExecutionServiceConfig.getPvcDefaultStorageSize())
                            .storageClass(PVC_DEFAULT_STORAGE_CLASS)
                            .build());
    }
    return pvcParamsList;
  }

  private Map<String, String> getVolumeToMountPath(List<String> sharedPaths) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(STEP_EXEC, MOUNT_PATH);

    if (sharedPaths != null) {
      int index = 0;
      for (String path : sharedPaths) {
        if (isEmpty(path)) {
          continue;
        }

        String volumeName = String.format("%s%d", SHARED_VOLUME_PREFIX, index);
        volumeToMountPath.put(volumeName, path);
        index++;
      }
    }
    return volumeToMountPath;
  }

  private List<ContainerDefinitionInfo> createStepsContainerDefinition(List<ExecutionWrapperConfig> steps,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    if (steps == null) {
      return containerDefinitionInfos;
    }

    int stepIndex = 0;
    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        stepIndex++;
        if (stepElementConfig.getTimeout() != null && stepElementConfig.getTimeout().isExpression()) {
          throw new InvalidRequestException(
              "Timeout field must be resolved in step: " + stepElementConfig.getIdentifier());
        }

        ContainerDefinitionInfo containerDefinitionInfo =
            createStepContainerDefinition(stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }

            stepIndex++;
            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            ContainerDefinitionInfo containerDefinitionInfo = createStepContainerDefinition(
                stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex);
            if (containerDefinitionInfo != null) {
              containerDefinitionInfos.add(containerDefinitionInfo);
            }
          }
        }
      }
    }
    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo createStepContainerDefinition(StepElementConfig stepElement,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return null;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    long timeout = TimeoutUtils.getTimeoutInSeconds(stepElement.getTimeout(), ciStepInfo.getDefaultTimeout());
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return createRunStepContainerDefinition((RunStepInfo) ciStepInfo, integrationStage, ciExecutionArgs, portFinder,
            stepIndex, stepElement.getIdentifier(), stepElement.getName());
      case DOCKER:
      case ECR:
      case GCR:
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_GCS:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_S3:
      case UPLOAD_GCS:
        return createPluginCompatibleStepContainerDefinition((PluginCompatibleStep) ciStepInfo, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(), stepElement.getType(), timeout);
      case PLUGIN:
        return createPluginStepContainerDefinition((PluginStepInfo) ciStepInfo, ciExecutionArgs, portFinder, stepIndex,
            stepElement.getIdentifier(), stepElement.getName());
      case TEST_INTELLIGENCE:
        return createRunTestsStepContainerDefinition((RunTestsStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier());
      default:
        return null;
    }
  }

  private ContainerDefinitionInfo createPluginCompatibleStepContainerDefinition(PluginCompatibleStep stepInfo,
      CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex, String identifier, String stepName,
      String stepType, long timeout) {
    Integer port = portFinder.getNextPort();

    String containerName = String.format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    envVarMap.putAll(PluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, identifier, timeout));

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(getImageInfo(resolveStringParameter("containerImage", stepInfo.getStepType().getType(),
                    identifier, stepInfo.getContainerImage(), true)))
                .build())
        .containerResourceParams(getStepContainerResource(stepInfo.getResources(), stepType, identifier))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(stepName)
        .build();
  }

  private ContainerDefinitionInfo createRunStepContainerDefinition(RunStepInfo runStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name) {
    Integer port = portFinder.getNextPort();

    String containerName = String.format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> envvars =
        resolveMapParameter("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(
                    getImageInfo(resolveStringParameter("Image", "Run", identifier, runStepInfo.getImage(), true)))
                .connectorIdentifier(
                    resolveStringParameter("connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), true))
                .build())
        .containerResourceParams(getStepContainerResource(runStepInfo.getResources(), "Run", identifier))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.RUN)
        .stepName(name)
        .build();
  }

  private ContainerDefinitionInfo createRunTestsStepContainerDefinition(RunTestsStepInfo runTestsStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier) {
    Integer port = portFinder.getNextPort();

    String containerName = String.format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(getImageInfo(runTestsStepInfo.getImage()))
                                   .connectorIdentifier(runTestsStepInfo.getConnector())
                                   .build())
        .containerResourceParams(getStepContainerResource(runTestsStepInfo.getResources(), "RunTests", identifier))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.TEST_INTELLIGENCE)
        .build();
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(PluginStepInfo pluginStepInfo,
      CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex, String identifier, String name) {
    Integer port = portFinder.getNextPort();

    String containerName = String.format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> settings =
        resolveMapParameter("settings", "Plugin", identifier, pluginStepInfo.getSettings(), false);
    if (!isEmpty(settings)) {
      for (Map.Entry<String, String> entry : settings.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVarMap.put(key, entry.getValue());
      }
    }
    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand())
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .stepIdentifier(identifier)
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(getImageInfo(resolveStringParameter(
                                       "Image", "Plugin", identifier, pluginStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "Plugin", identifier, pluginStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(getStepContainerResource(pluginStepInfo.getResources(), "Plugin", identifier))
        .ports(Collections.singletonList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepName(name)
        .build();
  }

  private ContainerResourceParams getStepContainerResource(ContainerResource resource, String stepType, String stepId) {
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(getContainerCpuLimit(resource, stepType, stepId))
        .resourceLimitMemoryMiB(getContainerMemoryLimit(resource, stepType, stepId))
        .build();
  }

  private List<SecretNGVariable> getSecretVariables(StageElementConfig stageElementConfig) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);
    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyList();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(variable -> variable.getType() == NGVariableType.SECRET)
        .map(customVariable -> (SecretNGVariable) customVariable)
        .collect(Collectors.toList());
  }

  private Set<String> getPublishArtifactStepIds(StageElementConfig stageElementConfig) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    List<ExecutionWrapperConfig> executionWrappers = integrationStageConfig.getExecution().getSteps();
    if (isEmpty(executionWrappers)) {
      return Collections.emptySet();
    }

    Set<String> set = new HashSet<>();
    for (ExecutionWrapperConfig executionWrapperConfig : executionWrappers) {
      if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapperConfig);

        for (ExecutionWrapperConfig executionWrapper : parallelStepElementConfig.getSections()) {
          if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
            StepElementConfig stepElementConfig = getStepElementConfig(executionWrapper);

            if (stepElementConfig.getStepSpecType().getStepType() == PublishStepInfo.typeInfo.getStepType()) {
              set.add(stageElementConfig.getIdentifier());
            }
          }
        }
      } else if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StepElementConfig stepElementConfig = getStepElementConfig(executionWrapperConfig);
        if (stepElementConfig.getStepSpecType().getStepType() == PublishStepInfo.typeInfo.getStepType()) {
          set.add(stageElementConfig.getIdentifier());
        }
      }
    }
    return set;
  }

  private ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  private StepElementConfig getStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), StepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  private Map<String, ConnectorConversionInfo> getStepConnectorRefs(StageElementConfig stageElementConfig) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    List<ExecutionWrapperConfig> executionWrappers = integrationStageConfig.getExecution().getSteps();
    if (isEmpty(executionWrappers)) {
      return Collections.emptyMap();
    }

    Map<String, ConnectorConversionInfo> map = new HashMap<>();
    for (ExecutionWrapperConfig executionWrapperConfig : executionWrappers) {
      if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapperConfig);
        for (ExecutionWrapperConfig executionWrapper : parallelStepElementConfig.getSections()) {
          if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
            StepElementConfig stepElementConfig = getStepElementConfig(executionWrapper);
            map.putAll(getStepConnectorConversionInfo(stepElementConfig));
          }
        }
      } else if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StepElementConfig stepElementConfig = getStepElementConfig(executionWrapperConfig);
        map.putAll(getStepConnectorConversionInfo(stepElementConfig));
      }
    }
    return map;
  }

  private Map<String, ConnectorConversionInfo> getStepConnectorConversionInfo(StepElementConfig stepElement) {
    Map<String, ConnectorConversionInfo> map = new HashMap<>();
    if (stepElement.getStepSpecType() instanceof PublishStepInfo) {
      List<Artifact> publishArtifacts = ((PublishStepInfo) stepElement.getStepSpecType()).getPublishArtifacts();
      for (Artifact artifact : publishArtifacts) {
        String connectorRef = resolveStringParameter("connectorRef", stepElement.getType(), stepElement.getIdentifier(),
            artifact.getConnector().getConnectorRef(), true);
        String connectorId = IdentifierRefHelper.getIdentifier(connectorRef);
        String stepId = stepElement.getIdentifier();
        switch (artifact.getConnector().getType()) {
          case ECR:
          case S3:
            map.put(stepId,
                ConnectorConversionInfo.builder()
                    .connectorRef(connectorRef)
                    .envToSecretEntry(EnvVariableEnum.AWS_ACCESS_KEY, ACCESS_KEY_PREFIX + connectorId)
                    .envToSecretEntry(EnvVariableEnum.AWS_SECRET_KEY, SECRET_KEY_PREFIX + connectorId)
                    .build());
            break;
          case GCR:
            map.put(stepId,
                ConnectorConversionInfo.builder()
                    .connectorRef(connectorRef)
                    .envToSecretEntry(EnvVariableEnum.GCP_KEY_AS_FILE, SECRET_PATH_PREFIX + connectorId)
                    .build());
            break;
          case ARTIFACTORY:
            map.put(stepId,
                ConnectorConversionInfo.builder()
                    .connectorRef(connectorRef)
                    .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_ENDPOINT, ENDPOINT_PREFIX + connectorId)
                    .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_USERNAME, USERNAME_PREFIX + connectorId)
                    .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_PASSWORD, PASSW_PREFIX + connectorId)
                    .build());
            break;
          case DOCKERHUB:
            map.put(stepId,
                ConnectorConversionInfo.builder()
                    .connectorRef(connectorRef)
                    .envToSecretEntry(EnvVariableEnum.DOCKER_REGISTRY, ENDPOINT_PREFIX + connectorId)
                    .envToSecretEntry(EnvVariableEnum.DOCKER_USERNAME, USERNAME_PREFIX + connectorId)
                    .envToSecretEntry(EnvVariableEnum.DOCKER_PASSWORD, PASSW_PREFIX + connectorId)
                    .build());
            break;
          default:
            throw new IllegalStateException("Unexpected value: " + stepElement.getType());
        }
      }
    } else if (stepElement.getStepSpecType() instanceof PluginCompatibleStep) {
      PluginCompatibleStep step = (PluginCompatibleStep) stepElement.getStepSpecType();
      switch (stepElement.getType()) {
        case "BuildAndPushECR":
        case "RestoreCacheS3":
        case "SaveCacheS3":
        case "S3Upload":
          map.put(stepElement.getIdentifier(),
              ConnectorConversionInfo.builder()
                  .connectorRef(resolveStringParameter(
                      "connectorRef", stepElement.getType(), stepElement.getIdentifier(), step.getConnectorRef(), true))
                  .envToSecretEntry(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY)
                  .envToSecretEntry(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY)
                  .build());
          break;
        case "BuildAndPushGCR":
        case "GCSUpload":
        case "SaveCacheGCS":
        case "RestoreCacheGCS":
          map.put(stepElement.getIdentifier(),
              ConnectorConversionInfo.builder()
                  .connectorRef(resolveStringParameter(
                      "connectorRef", stepElement.getType(), stepElement.getIdentifier(), step.getConnectorRef(), true))
                  .envToSecretEntry(EnvVariableEnum.GCP_KEY, PLUGIN_JSON_KEY)
                  .build());

          break;
        case "BuildAndPushDockerRegistry":
          map.put(stepElement.getIdentifier(),
              ConnectorConversionInfo.builder()
                  .connectorRef(resolveStringParameter(
                      "connectorRef", stepElement.getType(), stepElement.getIdentifier(), step.getConnectorRef(), true))
                  .envToSecretEntry(EnvVariableEnum.DOCKER_USERNAME, PLUGIN_USERNAME)
                  .envToSecretEntry(EnvVariableEnum.DOCKER_PASSWORD, PLUGIN_PASSW)
                  .envToSecretEntry(EnvVariableEnum.DOCKER_REGISTRY, PLUGIN_REGISTRY)
                  .build());
          break;
        case "ArtifactoryUpload":
          map.put(stepElement.getIdentifier(),
              ConnectorConversionInfo.builder()
                  .connectorRef(resolveStringParameter(
                      "connectorRef", stepElement.getType(), stepElement.getIdentifier(), step.getConnectorRef(), true))
                  .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_ENDPOINT, PLUGIN_URL)
                  .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_USERNAME, PLUGIN_USERNAME)
                  .envToSecretEntry(EnvVariableEnum.ARTIFACTORY_PASSWORD, PLUGIN_PASSW)
                  .build());
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + stepElement.getType());
      }
    }
    return map;
  }

  private Map<String, String> getEnvVariables(StageElementConfig stageElementConfig) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyMap();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(customVariables -> customVariables.getType() == NGVariableType.STRING)
        .map(customVariable -> (StringNGVariable) customVariable)
        .collect(Collectors.toMap(ngVariable
            -> ngVariable.getName(),
            ngVariable
            -> resolveStringParameterWithDefaultValue("variableValue", "stage", stageElementConfig.getIdentifier(),
                ngVariable.getValue(), false, ngVariable.getDefaultValue())));
  }

  private ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 2) {
        throw new InvalidRequestException("Image should not contain multiple tags");
      }
      if (subTokens.length == 2) {
        name = subTokens[0];
        tag = subTokens[1];
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  private Integer getContainerMemoryLimit(ContainerResource resource, String stepType, String stepId) {
    Integer memoryLimit = ciExecutionServiceConfig.getDefaultMemoryLimit();
    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity =
          resolveStringParameter("memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getMemoryQuantityValueInUnit(memoryLimitMemoryQuantity, MemoryQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId) {
    Integer cpuLimit = ciExecutionServiceConfig.getDefaultCPULimit();
    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity = resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private Integer getStageMemoryRequest(List<ExecutionWrapperConfig> steps) {
    Integer stageMemoryRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step);
      stageMemoryRequest = Math.max(stageMemoryRequest, executionWrapperMemoryRequest);
    }
    return stageMemoryRequest;
  }

  private Integer getExecutionWrapperMemoryRequest(ExecutionWrapperConfig executionWrapper) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperMemoryRequest = getStepMemoryLimit(stepElementConfig);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallel.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper);
        }
      }
    }
    return executionWrapperMemoryRequest;
  }

  private Integer getStepMemoryLimit(StepElementConfig stepElement) {
    Integer zeroMemory = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroMemory;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerMemoryLimit(
            ((RunStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case PLUGIN:
        return getContainerMemoryLimit(
            ((PluginStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case TEST_INTELLIGENCE:
        return getContainerMemoryLimit(
            ((RunTestsStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
        return getContainerMemoryLimit(
            ((PluginCompatibleStep) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      default:
        return zeroMemory;
    }
  }

  private Integer getStageCpuRequest(List<ExecutionWrapperConfig> steps) {
    Integer stageCpuRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step);
      stageCpuRequest = Math.max(stageCpuRequest, executionWrapperCpuRequest);
    }
    return stageCpuRequest;
  }

  private Integer getExecutionWrapperCpuRequest(ExecutionWrapperConfig executionWrapper) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperCpuRequest = getStepCpuLimit(stepElementConfig);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper);
        }
      }
    }
    return executionWrapperCpuRequest;
  }

  private Integer getStepCpuLimit(StepElementConfig stepElement) {
    Integer zeroCpu = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroCpu;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerCpuLimit(
            ((RunStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case PLUGIN:
        return getContainerCpuLimit(
            ((PluginStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case TEST_INTELLIGENCE:
        return getContainerCpuLimit(
            ((RunTestsStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
        return getContainerCpuLimit(
            ((PluginCompatibleStep) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier());
      default:
        return zeroCpu;
    }
  }

  private String getWorkingDirectoryPath(String workingDirectory) {
    return String.format("/%s/%s", STEP_EXEC, workingDirectory);
  }
}
