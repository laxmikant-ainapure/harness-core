package software.wings.delegatetasks.validation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.join;

import static java.lang.String.format;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureHelperService;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.ManifestAwareTaskParams;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.helpers.ext.kustomize.KustomizeConstants;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
import org.zeroturnaround.exec.ProcessExecutor;

@Singleton
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class K8sValidationHelper {
  @Inject @Transient private transient GkeClusterService gkeClusterService;
  @Inject @Transient private transient AzureHelperService azureHelperService;
  @Inject @Transient private transient EncryptionService encryptionService;

  @Nullable
  public String getKustomizeCriteria(@Nonnull KustomizeConfig kustomizeConfig) {
    if (isNotEmpty(kustomizeConfig.getPluginRootDir())) {
      return join(":", "KustomizePluginDir", kustomizeConfig.getPluginRootDir());
    }
    return null;
  }

  public boolean kustomizeValidationNeeded(K8sTaskParameters k8sTaskParameters) {
    if (k8sTaskParameters instanceof ManifestAwareTaskParams) {
      return fetchKustomizeConfig((ManifestAwareTaskParams) k8sTaskParameters) != null;
    }
    return false;
  }

  @Nullable
  public KustomizeConfig fetchKustomizeConfig(ManifestAwareTaskParams taskParams) {
    return taskParams.getK8sDelegateManifestConfig() != null
        ? taskParams.getK8sDelegateManifestConfig().getKustomizeConfig()
        : null;
  }

  /**
   * Tests whether kustomize plugin path exists on the machine
   *
   * @param config
   * @return {@code true} if the plugin path field is null/empty or the
   * plugin path actually exists on the machine; {@code false} otherwise
   */
  public boolean doesKustomizePluginDirExist(@Nonnull KustomizeConfig config) {
    String kustomizePluginPath = renderPathUsingEnvVariables(config.getPluginRootDir());
    if (isNotEmpty(kustomizePluginPath)) {
      try {
        kustomizePluginPath = join("/", kustomizePluginPath, KustomizeConstants.KUSTOMIZE_PLUGIN_DIR_SUFFIX);
        return FileIo.checkIfFileExist(kustomizePluginPath);
      } catch (IOException e) {
        return false;
      }
    }
    return true;
  }

  private String renderPathUsingEnvVariables(String kustomizePluginPath) {
    if (isNotEmpty(kustomizePluginPath)) {
      try {
        return executeShellCommand(format("echo %s", kustomizePluginPath));
      } catch (Exception ex) {
        log.error(format("Could not echo kustomizePluginPath %s", kustomizePluginPath));
      }
    }
    return kustomizePluginPath;
  }

  private String getKubernetesMasterUrl(K8sClusterConfig k8sClusterConfig) {
    SettingValue value = k8sClusterConfig.getCloudProvider();
    KubernetesConfig kubernetesConfig;
    String namespace = k8sClusterConfig.getNamespace();
    List<EncryptedDataDetail> edd = k8sClusterConfig.getCloudProviderEncryptionDetails();
    if (value instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(
          (GcpConfig) value, edd, k8sClusterConfig.getGcpKubernetesCluster().getClusterName(), namespace, false);
    } else if (value instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) value;
      kubernetesConfig = azureHelperService.getKubernetesClusterConfig(azureConfig, edd,
          k8sClusterConfig.getAzureKubernetesCluster().getSubscriptionId(),
          k8sClusterConfig.getAzureKubernetesCluster().getResourceGroup(),
          k8sClusterConfig.getAzureKubernetesCluster().getName(), namespace, false);
    } else if (value instanceof KubernetesClusterConfig) {
      return ((KubernetesClusterConfig) value).getMasterUrl();
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Unknown kubernetes cloud provider setting value: " + value.getType());
    }
    return kubernetesConfig.getMasterUrl();
  }

  private String executeShellCommand(String cmd) throws InterruptedException, TimeoutException, IOException {
    return new ProcessExecutor()
        .command("/bin/sh", "-c", cmd)
        .readOutput(true)
        .timeout(5, TimeUnit.SECONDS)
        .execute()
        .outputUTF8()
        .trim();
  }
}
