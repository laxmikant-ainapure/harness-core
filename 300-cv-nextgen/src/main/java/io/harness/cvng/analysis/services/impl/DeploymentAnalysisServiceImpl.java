package io.harness.cvng.analysis.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.BlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.HostSummaryInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.TrafficSplitPercentage;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostInfo;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo.LoadTestAdditionalInfoBuilder;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DeploymentAnalysisServiceImpl implements DeploymentAnalysisService {
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private HostRecordService hostRecordService;
  @Inject private ActivityService activityService;

  @Override
  public Optional<Risk> getLatestRiskScore(String accountId, String verificationJobInstanceId) {
    Optional<Risk> recentHighestTimeSeriesRiskScore =
        deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId);
    Optional<Risk> latestLogRiskScore =
        deploymentLogAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId);
    if (recentHighestTimeSeriesRiskScore.isPresent() && latestLogRiskScore.isPresent()) {
      return Optional.of(CVNGObjectUtils.max(
          recentHighestTimeSeriesRiskScore.get(), latestLogRiskScore.get(), Comparator.naturalOrder()));
    } else if (recentHighestTimeSeriesRiskScore.isPresent()) {
      return recentHighestTimeSeriesRiskScore;
    } else if (latestLogRiskScore.isPresent()) {
      return latestLogRiskScore;
    } else {
      return Optional.empty();
    }
  }

  @Override
  public LoadTestAdditionalInfo getLoadTestAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance) {
    LoadTestAdditionalInfoBuilder loadTestSummaryBuilder = LoadTestAdditionalInfo.builder();
    String currentDeploymentTag =
        activityService.getDeploymentTagFromActivity(accountId, verificationJobInstance.getUuid());
    loadTestSummaryBuilder.currentDeploymentTag(currentDeploymentTag)
        .currentStartTime(verificationJobInstance.getStartTime().toEpochMilli());
    TestVerificationJob testVerificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = testVerificationJob.getBaselineVerificationJobInstanceId();
    if (baselineVerificationJobInstanceId != null) {
      VerificationJobInstance baselineVerificationJobInstance =
          verificationJobInstanceService.getVerificationJobInstance(baselineVerificationJobInstanceId);
      String baselineDeploymentTag =
          activityService.getDeploymentTagFromActivity(accountId, baselineVerificationJobInstance.getUuid());
      loadTestSummaryBuilder.baselineDeploymentTag(baselineDeploymentTag)
          .baselineStartTime(baselineVerificationJobInstance.getStartTime().toEpochMilli());
    }
    return loadTestSummaryBuilder.build();
  }

  @Override
  public CanaryBlueGreenAdditionalInfo getCanaryBlueGreenAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance) {
    List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysis =
        deploymentTimeSeriesAnalysisService.getLatestDeploymentTimeSeriesAnalysis(
            accountId, verificationJobInstance.getUuid());
    List<DeploymentLogAnalysis> deploymentLogAnalysis =
        deploymentLogAnalysisService.getLatestDeploymentLogAnalysis(accountId, verificationJobInstance.getUuid());

    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
    Set<String> oldHosts = new HashSet<>();
    if (preDeploymentTimeRange.isPresent()) {
      Set<String> verificationTaskIds =
          verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstance.getUuid());
      oldHosts = hostRecordService.get(
          verificationTaskIds, preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());
    }

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        verificationJobInstance.getResolvedJob().getType() == VerificationJobType.CANARY
        ? new CanaryAdditionalInfo()
        : new BlueGreenAdditionalInfo();

    updateDeploymentVerificationHostInfoFromAnalyses(
        deploymentTimeSeriesAnalysis, deploymentLogAnalysis, oldHosts, canaryBlueGreenAdditionalInfo);
    updateHostInfoWithAnomalousCount(
        canaryBlueGreenAdditionalInfo, deploymentTimeSeriesAnalysis, deploymentLogAnalysis);

    canaryBlueGreenAdditionalInfo.setTrafficSplitPercentage(
        getTrafficSplitPercentage((CanaryBlueGreenVerificationJob) verificationJobInstance.getResolvedJob()));
    canaryBlueGreenAdditionalInfo.setFieldNames();

    return canaryBlueGreenAdditionalInfo;
  }

  private void populatePrimaryAndCanaryHostInfoForTimeseries(
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysisList, Map<String, HostSummaryInfo> controlMap,
      Map<String, HostSummaryInfo> testMap) {
    for (DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis : deploymentTimeSeriesAnalysisList) {
      for (HostInfo hostInfo : deploymentTimeSeriesAnalysis.getHostSummaries()) {
        // Primary nodes should always be no analysis. (Need to override risk in case it's present in both primary and
        // canary)
        if (hostInfo.isPrimary()) {
          HostSummaryInfo hostSummaryInfo =
              HostSummaryInfo.builder().hostName(hostInfo.getHostName()).risk(Risk.NO_ANALYSIS).build();
          controlMap.put(hostInfo.getHostName(), hostSummaryInfo);
        }

        // In case multiple analysis for a test node (possible when using multiple providers), use the one with higher
        // risk
        if (hostInfo.isCanary()) {
          HostSummaryInfo hostSummaryInfo =
              HostSummaryInfo.builder().hostName(hostInfo.getHostName()).risk(hostInfo.getRisk()).build();
          if (!testMap.keySet().contains(hostInfo.getHostName())
              || testMap.get(hostInfo.getHostName()).getRisk().isLessThanEq(hostSummaryInfo.getRisk())) {
            testMap.put(hostInfo.getHostName(), hostSummaryInfo);
          }
        }
      }
    }
  }

  private void populatePrimaryAndCanaryHostInfoForLogs(List<DeploymentLogAnalysis> deploymentLogAnalysisList,
      Map<String, HostSummaryInfo> controlMap, Map<String, HostSummaryInfo> testMap, Set<String> oldHosts) {
    for (String host : oldHosts) {
      HostSummaryInfo hostSummaryInfo = HostSummaryInfo.builder().hostName(host).risk(Risk.NO_ANALYSIS).build();
      controlMap.put(host, hostSummaryInfo);
    }
    for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalysisList) {
      for (HostSummary hostInfo : deploymentLogAnalysis.getHostSummaries()) {
        // In case multiple analysis for a test node (possible when using multiple providers), use the one with higher
        // risk
        HostSummaryInfo hostSummaryInfo =
            HostSummaryInfo.builder().hostName(hostInfo.getHost()).risk(hostInfo.getResultSummary().getRisk()).build();
        if (!testMap.keySet().contains(hostInfo.getHost())
            || testMap.get(hostInfo.getHost()).getRisk().isLessThanEq(hostSummaryInfo.getRisk())) {
          testMap.put(hostInfo.getHost(), hostSummaryInfo);
        }
      }
    }
  }

  private void updateDeploymentVerificationHostInfoFromAnalyses(
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysisList,
      List<DeploymentLogAnalysis> deploymentLogAnalysisList, Set<String> oldHosts,
      CanaryBlueGreenAdditionalInfo additionalInfo) {
    Map<String, HostSummaryInfo> controlMap = new HashMap<>();
    Map<String, HostSummaryInfo> testMap = new HashMap<>();

    if (isNotEmpty(deploymentTimeSeriesAnalysisList)) {
      populatePrimaryAndCanaryHostInfoForTimeseries(deploymentTimeSeriesAnalysisList, controlMap, testMap);
    }
    if (isNotEmpty(deploymentLogAnalysisList)) {
      populatePrimaryAndCanaryHostInfoForLogs(deploymentLogAnalysisList, controlMap, testMap, oldHosts);
    }

    additionalInfo.setPrimary(new HashSet<>(controlMap.values()));
    additionalInfo.setCanary(new HashSet<>(testMap.values()));
  }

  private void updateHostInfoWithAnomalousCount(CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo,
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysisList,
      List<DeploymentLogAnalysis> deploymentLogAnalysisList) {
    canaryBlueGreenAdditionalInfo.getCanary().forEach(hostSummaryInfo -> {
      int anomalousMetricsCount[] = new int[] {0};
      int anomalousLogClustersCount[] = new int[] {0};
      for (DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis : deploymentTimeSeriesAnalysisList) {
        deploymentTimeSeriesAnalysis.getTransactionMetricSummaries().forEach(transactionMetricHostData
            -> anomalousMetricsCount[0] += transactionMetricHostData.getHostData()
                                               .stream()
                                               .filter(hostData
                                                   -> hostData.getHostName().get().equals(hostSummaryInfo.getHostName())
                                                       && hostData.getRisk().isGreaterThanEq(Risk.MEDIUM))
                                               .count());
      }
      for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalysisList) {
        if (deploymentLogAnalysis.getHostSummaries() != null) {
          deploymentLogAnalysis.getHostSummaries()
              .stream()
              .filter(hostSummary -> hostSummary.getHost().equals(hostSummaryInfo.getHostName()))
              .forEach(hostSummary
                  -> anomalousLogClustersCount[0] +=
                  hostSummary.getResultSummary()
                      .getTestClusterSummaries()
                      .stream()
                      .filter(clusterSummary -> clusterSummary.getRisk().isGreaterThanEq(Risk.MEDIUM))
                      .count());
        }
      }
      hostSummaryInfo.setAnomalousMetricsCount(anomalousMetricsCount[0]);
      hostSummaryInfo.setAnomalousLogClustersCount(anomalousLogClustersCount[0]);
    });
  }

  private TrafficSplitPercentage getTrafficSplitPercentage(
      CanaryBlueGreenVerificationJob canaryBlueGreenVerificationJob) {
    Integer trafficSplitPercentage = canaryBlueGreenVerificationJob.getTrafficSplitPercentage();
    if (trafficSplitPercentage != null) {
      return TrafficSplitPercentage.builder()
          .preDeploymentPercentage(trafficSplitPercentage)
          .postDeploymentPercentage(100 - trafficSplitPercentage)
          .build();
    }
    return null;
  }
}
