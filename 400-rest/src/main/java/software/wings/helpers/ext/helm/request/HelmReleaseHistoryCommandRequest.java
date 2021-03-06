package software.wings.helpers.ext.helm.request;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmCommandFlag;
import software.wings.service.impl.ContainerServiceParams;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by anubhaw on 4/2/18.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class HelmReleaseHistoryCommandRequest extends HelmCommandRequest {
  public HelmReleaseHistoryCommandRequest(boolean mergeCapabilities) {
    super(HelmCommandType.RELEASE_HISTORY, mergeCapabilities);
  }

  @Builder
  public HelmReleaseHistoryCommandRequest(String accountId, String appId, String kubeConfigLocation, String commandName,
      String activityId, ContainerServiceParams containerServiceParams, String releaseName, GitConfig gitConfig,
      List<EncryptedDataDetail> encryptedDataDetails, LogCallback executionLogCallback, String commandFlags,
      HelmCommandFlag helmCommandFlag, HelmVersion helmVersion, String ocPath, String workingDir,
      List<String> variableOverridesYamlFiles, GitFileConfig gitFileConfig, boolean k8SteadyStateCheckEnabled,
      boolean mergeCapabilities) {
    super(HelmCommandType.RELEASE_HISTORY, accountId, appId, kubeConfigLocation, commandName, activityId,
        containerServiceParams, releaseName, null, null, gitConfig, encryptedDataDetails, executionLogCallback,
        commandFlags, helmCommandFlag, null, helmVersion, ocPath, workingDir, variableOverridesYamlFiles, gitFileConfig,
        k8SteadyStateCheckEnabled, mergeCapabilities);
  }
}
