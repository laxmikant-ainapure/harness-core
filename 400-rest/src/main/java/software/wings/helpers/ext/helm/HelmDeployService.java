package software.wings.helpers.ext.helm;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.helpers.ext.helm.request.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.response.HelmCommandResponse;
import software.wings.helpers.ext.helm.response.HelmListReleasesCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by anubhaw on 4/1/18.
 */
@TargetModule(Module._930_DELEGATE_TASKS)
public interface HelmDeployService {
  /**
   * Deploy helm command response.
   *
   * @param commandRequest       the command request
   * @return the helm command response
   */
  HelmCommandResponse deploy(HelmInstallCommandRequest commandRequest) throws IOException;

  /**
   * Rollback helm command response.
   *
   * @param commandRequest       the command request
   * @return the helm command response
   */
  HelmCommandResponse rollback(HelmRollbackCommandRequest commandRequest);

  /**
   * Ensure helm cli and tiller installed helm command response.
   *
   * @param helmCommandRequest   the helm command request
   * @return the helm command response
   */
  HelmCommandResponse ensureHelmCliAndTillerInstalled(HelmCommandRequest helmCommandRequest);

  /**
   * Last successful release version string.
   *
   * @param helmCommandRequest the helm command request
   * @return the string
   */
  HelmListReleasesCommandResponse listReleases(HelmInstallCommandRequest helmCommandRequest);

  /**
   * Release history helm release history command response.
   *
   * @param helmCommandRequest the helm command request
   * @return the helm release history command response
   */
  HelmReleaseHistoryCommandResponse releaseHistory(HelmReleaseHistoryCommandRequest helmCommandRequest);

  HelmCommandResponse addPublicRepo(HelmCommandRequest commandRequest)
      throws InterruptedException, IOException, TimeoutException;

  /**
   * Render chart templates and return the output.
   *
   * @param helmCommandRequest the helm command request
   * @param namespace the namespace
   * @param chartLocation the chart location
   * @param valueOverrides the value overrides
   * @return the helm release history command response
   */
  HelmCommandResponse renderHelmChart(HelmCommandRequest helmCommandRequest, String namespace, String chartLocation,
      List<String> valueOverrides) throws InterruptedException, TimeoutException, IOException, ExecutionException;

  HelmCommandResponse ensureHelm3Installed(HelmCommandRequest commandRequest);

  HelmCommandResponse ensureHelmInstalled(HelmCommandRequest commandRequest);
}
