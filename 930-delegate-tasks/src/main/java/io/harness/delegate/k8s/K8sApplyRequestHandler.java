package io.harness.delegate.k8s;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
public class K8sApplyRequestHandler extends K8sRequestHandler {
  @Inject private K8sApplyBaseHandler k8sApplyBaseHandler;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private final K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient) throws Exception {
    if (!(k8sDeployRequest instanceof K8sApplyRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sRollingDeployRequest"));
    }

    K8sApplyRequest k8sApplyRequest = (K8sApplyRequest) k8sDeployRequest;
    k8sApplyHandlerConfig.setReleaseName(k8sApplyRequest.getReleaseName());
    k8sApplyHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    long timeoutInMillis = getTimeoutMillisFromMinutes(k8sDeployRequest.getTimeoutIntervalInMin());

    boolean success = k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        k8sApplyRequest.getManifestDelegateConfig(), k8sApplyHandlerConfig.getManifestFilesDirectory(),
        k8sTaskHelperBase.getLogCallback(
            logStreamingTaskClient, FetchFiles, CollectionUtils.isEmpty(k8sApplyRequest.getValuesYamlList())),
        timeoutInMillis, k8sApplyRequest.getAccountId());
    if (!success) {
      return getGenericFailureResponse(null);
    }

    success = init(
        k8sApplyRequest, k8sDelegateTaskParams, k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true));
    if (!success) {
      return getGenericFailureResponse(null);
    }

    success = k8sApplyBaseHandler.prepare(k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true),
        k8sApplyRequest.isSkipSteadyStateCheck(), k8sApplyHandlerConfig);
    if (!success) {
      return getGenericFailureResponse(null);
    }

    success = k8sTaskHelperBase.applyManifests(k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(),
        k8sDelegateTaskParams, k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true), true);
    if (!success) {
      return getGenericFailureResponse(null);
    }

    success = k8sApplyBaseHandler.steadyStateCheck(k8sApplyRequest.isSkipSteadyStateCheck(),
        k8sApplyRequest.getK8sInfraDelegateConfig().getNamespace(), k8sDelegateTaskParams, timeoutInMillis,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true), k8sApplyHandlerConfig);
    if (!success) {
      return getGenericFailureResponse(null);
    }

    k8sApplyBaseHandler.wrapUp(k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true), k8sApplyHandlerConfig.getClient());

    return K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }

  private boolean init(K8sApplyRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback) {
    logCallback.saveExecutionLog("Initializing..\n");

    k8sApplyHandlerConfig.setKubernetesConfig(
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(request.getK8sInfraDelegateConfig()));

    k8sApplyHandlerConfig.setClient(
        Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));

    try {
      List<String> applyFilePaths = request.getFilePaths()
                                        .stream()
                                        .map(String::trim)
                                        .filter(StringUtils::isNotBlank)
                                        .collect(Collectors.toList());

      if (isEmpty(applyFilePaths)) {
        logCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
        logCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
        return false;
      }

      logCallback.saveExecutionLog(color("Found following files to be applied in the state", White, Bold));
      StringBuilder sb = new StringBuilder(1024);
      applyFilePaths.forEach(each -> sb.append(color(format("- %s", each), Gray)).append(System.lineSeparator()));
      logCallback.saveExecutionLog(sb.toString());

      k8sApplyHandlerConfig.setResources(k8sTaskHelperBase.getResourcesFromManifests(k8sDelegateTaskParams,
          request.getManifestDelegateConfig(), k8sApplyHandlerConfig.getManifestFilesDirectory(), applyFilePaths,
          request.getValuesYamlList(), k8sApplyHandlerConfig.getReleaseName(),
          k8sApplyHandlerConfig.getKubernetesConfig().getNamespace(), logCallback, request.getTimeoutIntervalInMin()));

      logCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
      logCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sApplyHandlerConfig.getResources()));

      if (request.isSkipDryRun()) {
        logCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
        logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      }

      return k8sTaskHelperBase.dryRunManifests(
          k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(), k8sDelegateTaskParams, logCallback);
    } catch (Exception e) {
      log.error("Exception:", e);
      logCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      logCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }
  }
}
