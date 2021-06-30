package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.unzipManifestFiles;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.RESOURCE_DIR_BASE;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.getChartDirectory;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.helm.HelmConstants.HELM_PATH_PLACEHOLDER;
import static io.harness.helm.HelmConstants.REPO_NAME;
import static io.harness.helm.HelmConstants.VALUES_YAML;
import static io.harness.helm.HelmConstants.WORKING_DIR_BASE;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.k8s.model.HelmVersion;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.chartmuseum.ChartMuseumClient;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmTaskHelper {
  private static final long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();

  @Inject private EncryptionService encryptionService;
  @Inject private ChartMuseumClient chartMuseumClient;
  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private DelegateFileManagerBase delegateFileManagerBase;

  private void fetchChartFiles(HelmChartConfigParams helmChartConfigParams, String destinationDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    HelmRepoConfig helmRepoConfig = helmChartConfigParams.getHelmRepoConfig();

    initHelm(destinationDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);

    if (helmRepoConfig == null) {
      fetchChartFromEmptyHelmRepoConfig(helmChartConfigParams, destinationDirectory, timeoutInMillis, helmCommandFlag);
    } else {
      decryptConnectorConfig(helmChartConfigParams);

      if (helmRepoConfig instanceof AmazonS3HelmRepoConfig || helmRepoConfig instanceof GCSHelmRepoConfig) {
        fetchChartUsingChartMuseumServer(helmChartConfigParams, helmChartConfigParams.getConnectorConfig(),
            destinationDirectory, timeoutInMillis, helmCommandFlag);
      } else if (helmRepoConfig instanceof HttpHelmRepoConfig) {
        fetchChartFromHttpServer(helmChartConfigParams, destinationDirectory, timeoutInMillis, helmCommandFlag);
      }
    }
  }

  public void decryptConnectorConfig(HelmChartConfigParams helmChartConfigParams) {
    encryptionService.decrypt(
        helmChartConfigParams.getHelmRepoConfig(), helmChartConfigParams.getEncryptedDataDetails(), false);

    SettingValue connectorConfig = helmChartConfigParams.getConnectorConfig();
    if (connectorConfig != null) {
      encryptionService.decrypt(
          (EncryptableSetting) connectorConfig, helmChartConfigParams.getConnectorEncryptedDataDetails(), false);
    }
  }

  public void downloadChartFiles(HelmChartConfigParams helmChartConfigParams, String destinationDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());

    fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);
  }

  public void downloadChartFiles(HelmChartSpecification helmChartSpecification, String destinationDirectory,
      HelmCommandRequest helmCommandRequest, long timeoutInMillis, HelmCommandFlag helmCommandFlag) throws Exception {
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());
    HelmChartConfigParams helmChartConfigParams = HelmChartConfigParams.builder()
                                                      .chartName(helmChartSpecification.getChartName())
                                                      .chartVersion(helmChartSpecification.getChartVersion())
                                                      .chartUrl(helmChartSpecification.getChartUrl())
                                                      .helmVersion(helmCommandRequest.getHelmVersion())
                                                      .build();
    if (isNotBlank(helmChartSpecification.getChartUrl())) {
      helmChartConfigParams.setRepoName(helmCommandRequest.getRepoName());
    }

    fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);
  }

  public void downloadAndUnzipCustomSourceManifestFiles(
      String workingDirectory, String zippedManifestFileId, String accountId) throws IOException {
    InputStream inputStream =
        delegateFileManagerBase.downloadByFileId(FileBucket.CUSTOM_MANIFEST, zippedManifestFileId, accountId);
    ZipInputStream zipInputStream = new ZipInputStream(inputStream);

    File destDir = new File(workingDirectory);
    unzipManifestFiles(destDir, zipInputStream);
  }

  public String getValuesYamlFromChart(HelmChartConfigParams helmChartConfigParams, long timeoutInMillis,
      HelmCommandFlag helmCommandFlag) throws Exception {
    String workingDirectory = createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());

    try {
      fetchChartFiles(helmChartConfigParams, workingDirectory, timeoutInMillis, helmCommandFlag);

      // Fetch chart version in case it is not specified in service to display in execution logs
      if (isBlank(helmChartConfigParams.getChartVersion())) {
        try {
          helmChartConfigParams.setChartVersion(getHelmChartInfoFromChartsYamlFile(
              Paths.get(workingDirectory, helmChartConfigParams.getChartName(), CHARTS_YAML_KEY).toString())
                                                    .getVersion());
        } catch (Exception e) {
          log.info("Unable to fetch chart version", e);
        }
      }

      return new String(Files.readAllBytes(Paths.get(
                            getChartDirectory(workingDirectory, helmChartConfigParams.getChartName()), VALUES_YAML)),
          StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.info("values.yaml file not found", ex);
      return null;
    } finally {
      cleanup(workingDirectory);
    }
  }

  private void fetchChartUsingChartMuseumServer(HelmChartConfigParams helmChartConfigParams,
      SettingValue connectorConfig, String chartDirectory, long timeoutInMillis, HelmCommandFlag helmCommandFlag)
      throws Exception {
    ChartMuseumServer chartMuseumServer = null;
    String resourceDirectory = null;

    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartMuseumServer = chartMuseumClient.startChartMuseumServer(helmChartConfigParams.getHelmRepoConfig(),
          connectorConfig, resourceDirectory, helmChartConfigParams.getBasePath());

      helmTaskHelperBase.addChartMuseumRepo(helmChartConfigParams.getRepoName(),
          helmChartConfigParams.getRepoDisplayName(), chartMuseumServer.getPort(), chartDirectory,
          helmChartConfigParams.getHelmVersion(), timeoutInMillis);
      helmTaskHelperBase.fetchChartFromRepo(helmChartConfigParams.getRepoName(),
          helmChartConfigParams.getRepoDisplayName(), helmChartConfigParams.getChartName(),
          helmChartConfigParams.getChartVersion(), chartDirectory, helmChartConfigParams.getHelmVersion(),
          helmCommandFlag, timeoutInMillis);
    } finally {
      if (chartMuseumServer != null) {
        chartMuseumClient.stopChartMuseumServer(chartMuseumServer.getStartedProcess());
      }
      removeRepo(
          helmChartConfigParams.getRepoName(), chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
      cleanup(resourceDirectory);
    }
  }

  public void initHelm(String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) throws IOException {
    helmTaskHelperBase.initHelm(workingDirectory, helmVersion, timeoutInMillis);
  }

  public String createNewDirectoryAtPath(String directoryBase) throws IOException {
    return helmTaskHelperBase.createNewDirectoryAtPath(directoryBase);
  }

  public String createDirectory(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase).normalize().toAbsolutePath().toString();

    createDirectoryIfDoesNotExist(workingDirectory);
    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    return workingDirectory;
  }

  public List<FileData> getFilteredFiles(List<FileData> files, List<String> filesToBeFetched) {
    List<FileData> filteredFiles = new ArrayList<>();

    if (isEmpty(files)) {
      log.info("Files list is empty");
      return filteredFiles;
    }

    Set<String> filesToBeFetchedSet = new HashSet<>(filesToBeFetched);
    for (FileData file : files) {
      if (filesToBeFetchedSet.contains(file.getFilePath())) {
        filteredFiles.add(file);
      }
    }

    return filteredFiles;
  }

  public void cleanup(String workingDirectory) {
    helmTaskHelperBase.cleanup(workingDirectory);
  }

  public void printHelmChartInfoInExecutionLogs(
      HelmChartConfigParams helmChartConfigParams, ExecutionLogCallback executionLogCallback) {
    if (isNotBlank(helmChartConfigParams.getRepoDisplayName())) {
      executionLogCallback.saveExecutionLog("Helm repository: " + helmChartConfigParams.getRepoDisplayName());
    }

    if (isNotBlank(helmChartConfigParams.getBasePath())) {
      executionLogCallback.saveExecutionLog("Base Path: " + helmChartConfigParams.getBasePath());
    }

    if (isNotBlank(helmChartConfigParams.getChartName())) {
      executionLogCallback.saveExecutionLog("Chart name: " + helmChartConfigParams.getChartName());
    }

    if (isNotBlank(helmChartConfigParams.getChartVersion())) {
      executionLogCallback.saveExecutionLog("Chart version: " + helmChartConfigParams.getChartVersion());
    }

    if (isNotBlank(helmChartConfigParams.getChartUrl())) {
      executionLogCallback.saveExecutionLog("Chart url: " + helmChartConfigParams.getChartUrl());
    }

    if (helmChartConfigParams.getHelmVersion() != null) {
      executionLogCallback.saveExecutionLog("Helm version: " + helmChartConfigParams.getHelmVersion());
    }

    if (helmChartConfigParams.getHelmRepoConfig() instanceof AmazonS3HelmRepoConfig) {
      AmazonS3HelmRepoConfig amazonS3HelmRepoConfig =
          (AmazonS3HelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();
      executionLogCallback.saveExecutionLog("Chart bucket: " + amazonS3HelmRepoConfig.getBucketName());
    } else if (helmChartConfigParams.getHelmRepoConfig() instanceof HttpHelmRepoConfig) {
      executionLogCallback.saveExecutionLog(
          "Repo url: " + ((HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig()).getChartRepoUrl());
    }
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    helmTaskHelperBase.addRepo(
        repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion, timeoutInMillis);
  }

  private void fetchChartFromHttpServer(HelmChartConfigParams helmChartConfigParams, String chartDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) {
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();

    helmTaskHelperBase.addRepo(helmChartConfigParams.getRepoName(), helmChartConfigParams.getRepoDisplayName(),
        httpHelmRepoConfig.getChartRepoUrl(), httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(),
        chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
    helmTaskHelperBase.fetchChartFromRepo(helmChartConfigParams.getRepoName(),
        helmChartConfigParams.getRepoDisplayName(), helmChartConfigParams.getChartName(),
        helmChartConfigParams.getChartVersion(), chartDirectory, helmChartConfigParams.getHelmVersion(),
        helmCommandFlag, timeoutInMillis);
  }

  public void addHelmRepo(HelmRepoConfig helmRepoConfig, SettingValue connectorConfig, String repoName,
      String repoDisplayName, String workingDirectory, String basePath, HelmVersion helmVersion) throws Exception {
    ChartMuseumServer chartMuseumServer = null;
    String resourceDirectory = null;
    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartMuseumServer =
          chartMuseumClient.startChartMuseumServer(helmRepoConfig, connectorConfig, resourceDirectory, basePath);

      helmTaskHelperBase.addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), workingDirectory,
          helmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
    } finally {
      if (chartMuseumServer != null) {
        chartMuseumClient.stopChartMuseumServer(chartMuseumServer.getStartedProcess());
      }
      cleanup(resourceDirectory);
    }
  }

  private String getRepoUpdateCommand(String repoName, String workingDirectory, HelmVersion helmVersion) {
    String repoUpdateCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, helmTaskHelperBase.getHelmPath(helmVersion))
            .replace("KUBECONFIG=${KUBECONFIG_PATH}", "")
            .replace(REPO_NAME, repoName);

    return helmTaskHelperBase.applyHelmHomePath(repoUpdateCommand, workingDirectory);
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    helmTaskHelperBase.removeRepo(repoName, workingDirectory, helmVersion, timeoutInMillis);
  }

  public void updateRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    try {
      String repoUpdateCommand = getRepoUpdateCommand(repoName, workingDirectory, helmVersion);
      ProcessResult processResult = helmTaskHelperBase.executeCommand(
          repoUpdateCommand, null, format("update helm repo %s", repoName), timeoutInMillis);

      log.info("Repo update command executed on delegate: {}", repoUpdateCommand);
      if (processResult.getExitValue() != 0) {
        log.warn("Failed to update helm repo {}. {}", repoName, processResult.getOutput().getUTF8());
      }
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getMessage(ex));
    }
  }

  /*
  This method is called in case the helm has empty repository connector and the chartName has <REPO_NAME/CHART_NAME>
  value. In that case, we want to use the default "$HELM_HOME" path. That is why :-
  1.) We are not adding repo if the URL is empty
  2.) Passing null directoryPath in the helmFetchCommand so that it picks up default helm
  Ruckus is one of the customer that is using this mechanism
   */
  private void fetchChartFromEmptyHelmRepoConfig(HelmChartConfigParams helmChartConfigParams, String chartDirectory,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag) {
    try {
      String helmFetchCommand;
      if (isNotBlank(helmChartConfigParams.getChartUrl())) {
        addRepo(helmChartConfigParams.getRepoName(), null, helmChartConfigParams.getChartUrl(), null, null,
            chartDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
        helmFetchCommand = helmTaskHelperBase.getHelmFetchCommand(helmChartConfigParams.getChartName(),
            helmChartConfigParams.getChartVersion(), helmChartConfigParams.getRepoName(), chartDirectory,
            helmChartConfigParams.getHelmVersion(), helmCommandFlag);
      } else {
        helmFetchCommand = helmTaskHelperBase.getHelmFetchCommand(helmChartConfigParams.getChartName(),
            helmChartConfigParams.getChartVersion(), helmChartConfigParams.getRepoName(), null,
            helmChartConfigParams.getHelmVersion(), helmCommandFlag);
      }
      helmTaskHelperBase.executeFetchChartFromRepo(helmChartConfigParams.getChartName(), chartDirectory,
          helmChartConfigParams.getRepoDisplayName(), helmFetchCommand, timeoutInMillis);

    } finally {
      if (isNotBlank(helmChartConfigParams.getChartUrl())) {
        removeRepo(helmChartConfigParams.getRepoName(), chartDirectory, helmChartConfigParams.getHelmVersion(),
            timeoutInMillis);
      }
    }
  }

  /**
   * Method to extract Helm Chart info like Chart version and Chart name from the downloaded Chart files.
   * @param chartYamlPath - Path of the Chart.yaml file
   * @return HelmChartInfo - This contains details about the Helm chart
   * @throws IOException
   */
  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(String chartYamlPath) throws IOException {
    return helmTaskHelperBase.getHelmChartInfoFromChartsYamlFile(chartYamlPath);
  }

  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(HelmInstallCommandRequest request) throws IOException {
    return getHelmChartInfoFromChartsYamlFile(Paths.get(request.getWorkingDir(), CHARTS_YAML_KEY).toString());
  }

  public HelmChartInfo getHelmChartInfoFromChartDirectory(String chartDirectory) throws IOException {
    return getHelmChartInfoFromChartsYamlFile(Paths.get(chartDirectory, CHARTS_YAML_KEY).toString());
  }

  public List<HelmChart> fetchChartVersions(HelmChartCollectionParams helmChartCollectionParams,
      String destinationDirectory, long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    HelmRepoConfig helmRepoConfig = helmChartConfigParams.getHelmRepoConfig();
    String workingDirectory = createDirectory(Paths.get(destinationDirectory).toString());
    initHelm(workingDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
    decryptConnectorConfig(helmChartConfigParams);

    if (helmRepoConfig instanceof HttpHelmRepoConfig) {
      return fetchVersionsFromHttp(helmChartCollectionParams, destinationDirectory, timeoutInMillis, workingDirectory);
    } else {
      return fetchVersionsUsingChartMuseumServer(helmChartCollectionParams, destinationDirectory, timeoutInMillis);
    }
  }

  private List<HelmChart> fetchVersionsFromHttp(HelmChartCollectionParams helmChartCollectionParams,
      String destinationDirectory, long timeoutInMillis, String workingDirectory) throws IOException {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig();
    addRepo(helmChartConfigParams.getRepoName(), helmChartConfigParams.getRepoDisplayName(),
        httpHelmRepoConfig.getChartRepoUrl(), httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(),
        destinationDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);

    updateRepo(
        helmChartConfigParams.getRepoName(), workingDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);

    String commandOutput = executeCommandWithLogOutput(
        fetchHelmChartVersionsCommand(helmChartConfigParams.getHelmVersion(), helmChartConfigParams.getChartName(),
            helmChartConfigParams.getRepoName(), destinationDirectory),
        workingDirectory, "Helm chart fetch versions command failed ");

    if (log.isDebugEnabled()) {
      log.debug("Result of the helm repo search command: {}, chart name: {}", commandOutput,
          helmChartCollectionParams.getHelmChartConfigParams().getChartName());
    }

    return parseHelmVersionFetchOutput(commandOutput, helmChartCollectionParams);
  }

  private List<HelmChart> parseHelmVersionFetchOutput(
      String commandOutput, HelmChartCollectionParams manifestCollectionParams) throws IOException {
    String errorMessage = "No chart with the given name found. Chart might be deleted at source";
    if (isEmpty(commandOutput) || commandOutput.contains("No results found")) {
      throw new InvalidRequestException(errorMessage);
    }

    CSVFormat csvFormat = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').withTrim();
    List<CSVRecord> records = CSVParser.parse(commandOutput, csvFormat).getRecords();
    if (isEmpty(records)) {
      throw new InvalidRequestException(errorMessage);
    }
    List<HelmChart> charts =
        records.stream()
            .filter(record
                -> record.size() > 1
                    && matchesChartName(
                        manifestCollectionParams.getHelmChartConfigParams().getChartName(), record.get(0)))
            .map(record
                -> HelmChart.builder()
                       .appId(manifestCollectionParams.getAppId())
                       .accountId(manifestCollectionParams.getAccountId())
                       .applicationManifestId(manifestCollectionParams.getAppManifestId())
                       .serviceId(manifestCollectionParams.getServiceId())
                       .name(manifestCollectionParams.getHelmChartConfigParams().getChartName())
                       .version(record.get(1))
                       .displayName(
                           manifestCollectionParams.getHelmChartConfigParams().getChartName() + "-" + record.get(1))
                       .appVersion(record.size() > 2 ? record.get(2) : null)
                       .description(record.size() > 3 ? record.get(3) : null)
                       .build())
            .collect(Collectors.toList());

    if (isEmpty(charts)) {
      throw new InvalidRequestException(errorMessage);
    }

    return charts;
  }

  private List<HelmChart> fetchVersionsUsingChartMuseumServer(HelmChartCollectionParams helmChartCollectionParams,
      String chartDirectory, long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();

    String resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);

    ChartMuseumServer chartMuseumServer =
        chartMuseumClient.startChartMuseumServer(helmChartConfigParams.getHelmRepoConfig(),
            helmChartConfigParams.getConnectorConfig(), resourceDirectory, helmChartConfigParams.getBasePath());

    try {
      helmTaskHelperBase.addChartMuseumRepo(helmChartConfigParams.getRepoName(),
          helmChartConfigParams.getRepoDisplayName(), chartMuseumServer.getPort(), chartDirectory,
          helmChartConfigParams.getHelmVersion(), timeoutInMillis);

      String commandOutput = executeCommandWithLogOutput(
          fetchHelmChartVersionsCommand(helmChartConfigParams.getHelmVersion(), helmChartConfigParams.getChartName(),
              helmChartConfigParams.getRepoName(), chartDirectory),
          chartDirectory, "Helm chart fetch versions command failed ");
      return parseHelmVersionFetchOutput(commandOutput, helmChartCollectionParams);
    } finally {
      chartMuseumClient.stopChartMuseumServer(chartMuseumServer.getStartedProcess());
    }
  }

  private String fetchHelmChartVersionsCommand(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory) {
    String helmFetchCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH_ALL_VERSIONS, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, helmTaskHelperBase.getHelmPath(helmVersion))
            .replace("${CHART_NAME}", chartName);

    if (isNotBlank(repoName)) {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME, repoName);
    } else {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME + "/", "");
    }
    return helmTaskHelperBase.applyHelmHomePath(helmFetchCommand, workingDirectory);
  }

  String executeCommandWithLogOutput(String command, String chartDirectory, String errorMessage) {
    StringBuilder sb = new StringBuilder();
    ProcessExecutor processExecutor = createProcessExecutorWithRedirectOutput(command, chartDirectory, sb);

    log.info("Helm command executed on delegate: {}", command);

    try {
      ProcessResult processResult = processExecutor.execute();
      if (processResult.getExitValue() != 0) {
        log.warn("Command failed with following result: {}", sb.toString());
      }
      return sb.toString();
    } catch (IOException e) {
      throw new HelmClientException(format("[IO exception] %s", errorMessage), USER, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientException(format("[Interrupted] %s", errorMessage), USER, e);
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new HelmClientException(format("[Timed out] %s", errorMessage), USER, e);
    }
  }

  ProcessExecutor createProcessExecutorWithRedirectOutput(
      String helmFetchCommand, String chartDirectory, StringBuilder sb) {
    return new ProcessExecutor()
        .commandSplit(helmFetchCommand)
        .directory(new File(chartDirectory))
        .readOutput(true)
        .redirectOutput(new LogOutputStream() {
          @Override
          protected void processLine(String line) {
            sb.append(line).append('\n');
          }
        });
  }

  private boolean matchesChartName(String chartName, String recordName) {
    return Arrays.asList(recordName.split("/")).contains(chartName);
  }

  public void cleanupAfterCollection(HelmChartCollectionParams helmChartCollectionParams, String destinationDirectory,
      long timeoutInMillis) throws Exception {
    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    String workingDirectory = Paths.get(destinationDirectory).toString();

    removeRepo(
        helmChartConfigParams.getRepoName(), workingDirectory, helmChartConfigParams.getHelmVersion(), timeoutInMillis);
    cleanup(workingDirectory);
  }
}
