package software.wings.helpers.ext.pcf.request;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.pcf.PcfManifestsPackage;

import software.wings.beans.PcfConfig;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This class contains all required data for PCFCommandTask.SETUP to perform setup task
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class PcfCommandSetupRequest extends PcfCommandRequest {
  /**
   * releasePrefixName is (appId_serviceId_envId), while creating new version of app,
   * we will add 1 to most recent version deployed,
   * so actual app name will be appId_serviceId_envId__version
   */
  private String releaseNamePrefix;
  private String manifestYaml;
  private List<ArtifactFile> artifactFiles;
  private ArtifactStreamAttributes artifactStreamAttributes;
  private List<String> tempRouteMap;
  private List<String> routeMaps;
  private Map<String, String> serviceVariables;
  private Map<String, String> safeDisplayServiceVariables;
  private Integer maxCount;
  private Integer currentRunningCount;
  private boolean useCurrentCount;
  private boolean blueGreen;
  private Integer olderActiveVersionCountToKeep;
  private PcfManifestsPackage pcfManifestsPackage;
  private String artifactProcessingScript;

  @Builder
  public PcfCommandSetupRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, PcfConfig pcfConfig, String workflowExecutionId,
      String releaseNamePrefix, String manifestYaml, List<ArtifactFile> artifactFiles,
      ArtifactStreamAttributes artifactStreamAttributes, List<String> tempRouteMap, List<String> routeMaps,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      Integer timeoutIntervalInMin, Integer maxCount, Integer currentRunningCount, boolean useCurrentCount,
      boolean blueGreen, Integer olderActiveVersionCountToKeep, boolean useCLIForPcfAppCreation,
      PcfManifestsPackage pcfManifestsPackage, boolean useAppAutoscalar, boolean enforceSslValidation,
      boolean limitPcfThreads, boolean ignorePcfConnectionContextCache, String artifactProcessingScript) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCLIForPcfAppCreation, enforceSslValidation, useAppAutoscalar,
        limitPcfThreads, ignorePcfConnectionContextCache);
    this.releaseNamePrefix = releaseNamePrefix;
    this.manifestYaml = manifestYaml;
    this.artifactFiles = artifactFiles;
    this.artifactStreamAttributes = artifactStreamAttributes;
    this.tempRouteMap = tempRouteMap;
    this.routeMaps = routeMaps;
    this.serviceVariables = serviceVariables;
    this.safeDisplayServiceVariables = safeDisplayServiceVariables;
    this.maxCount = maxCount;
    this.blueGreen = blueGreen;
    this.olderActiveVersionCountToKeep = olderActiveVersionCountToKeep;
    this.currentRunningCount = currentRunningCount;
    this.useCurrentCount = useCurrentCount;
    this.pcfManifestsPackage = pcfManifestsPackage;
    this.artifactProcessingScript = artifactProcessingScript;
  }
}
