package io.harness.cvng.dashboard.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NEMANJA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.beans.AnalyzedLogDataDTO;
import io.harness.cvng.dashboard.beans.LogDataByTag;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogDashboardServiceImplTest extends CvNextGenTestBase {
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private String accountId;

  @Inject private LogDashboardService logDashboardService;
  @Inject private HPersistence hPersistence;

  @Mock private LogAnalysisService mockLogAnalysisService;
  @Mock private CVConfigService mockCvConfigService;
  @Mock private ActivityService mockActivityService;
  @Mock private VerificationTaskService mockVerificationTaskService;

  @Before
  public void setUp() throws Exception {
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    serviceIdentifier = generateUuid();
    envIdentifier = generateUuid();
    accountId = generateUuid();
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(logDashboardService, "logAnalysisService", mockLogAnalysisService, true);
    FieldUtils.writeField(logDashboardService, "cvConfigService", mockCvConfigService, true);
    FieldUtils.writeField(logDashboardService, "activityService", mockActivityService, true);
    FieldUtils.writeField(logDashboardService, "verificationTaskService", mockVerificationTaskService, true);
    when(mockVerificationTaskService.getServiceGuardVerificationTaskId(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgumentAt(1, String.class));

    when(mockVerificationTaskService.create(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgumentAt(1, String.class));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnomalousLogs() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAnomalousLogs(accountId, projectIdentifier, orgIdentifier, serviceIdentifier,
            envIdentifier, CVMonitoringCategory.PERFORMANCE, startTime.toEpochMilli(), endTime.toEpochMilli(), 0, 10);
    verify(mockCvConfigService)
        .getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, CVMonitoringCategory.PERFORMANCE);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getLogData().getTag()));
    });

    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnomalousLogs_validatePagination() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAnomalousLogs(accountId, projectIdentifier, orgIdentifier, serviceIdentifier,
            envIdentifier, CVMonitoringCategory.PERFORMANCE, startTime.toEpochMilli(), endTime.toEpochMilli(), 0, 1);
    verify(mockCvConfigService)
        .getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, CVMonitoringCategory.PERFORMANCE);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    assertThat(pageResponse.getTotalItems()).isEqualTo(4);
    assertThat(pageResponse.getPageSize()).isEqualTo(1);
    assertThat(pageResponse.getTotalPages()).isGreaterThan(1);
    pageResponse.getContent().forEach(analyzedLogDataDTO -> {
      assertThat(Arrays.asList(LogAnalysisTag.UNKNOWN, LogAnalysisTag.UNEXPECTED)
                     .contains(analyzedLogDataDTO.getLogData().getTag()));
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnomalousLogs_noCvConfigForCategory() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, true, startTime, endTime, labelList);
    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(new ArrayList<>());
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAnomalousLogs(accountId, projectIdentifier, orgIdentifier, serviceIdentifier,
            envIdentifier, CVMonitoringCategory.PERFORMANCE, startTime.toEpochMilli(), endTime.toEpochMilli(), 0, 1);
    verify(mockCvConfigService)
        .getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, CVMonitoringCategory.PERFORMANCE);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAllLogs() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    PageResponse<AnalyzedLogDataDTO> pageResponse =
        logDashboardService.getAllLogs(accountId, projectIdentifier, orgIdentifier, serviceIdentifier, envIdentifier,
            CVMonitoringCategory.PERFORMANCE, startTime.toEpochMilli(), endTime.toEpochMilli(), 0, 10);
    verify(mockCvConfigService)
        .getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, CVMonitoringCategory.PERFORMANCE);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAllLogs_noCategory() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockCvConfigService.getConfigsOfProductionEnvironments(
             accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, null))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));

    PageResponse<AnalyzedLogDataDTO> pageResponse = logDashboardService.getAllLogs(accountId, projectIdentifier,
        orgIdentifier, serviceIdentifier, envIdentifier, null, startTime.toEpochMilli(), endTime.toEpochMilli(), 0, 10);

    verify(mockCvConfigService)
        .getConfigsOfProductionEnvironments(
            accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, null);
    assertThat(pageResponse).isNotNull();
    assertThat(pageResponse.getContent()).isNotEmpty();
    boolean containsKnown = false;
    for (AnalyzedLogDataDTO analyzedLogDataDTO : pageResponse.getContent()) {
      if (analyzedLogDataDTO.getLogData().getTag().equals(LogAnalysisTag.KNOWN)) {
        containsKnown = true;
        break;
      }
    }
    assertThat(containsKnown).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLogCountByTag() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);

    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(
             cvConfigId, Arrays.asList(LogAnalysisTag.values()), startTime, endTime))
        .thenReturn(resultList);

    SortedSet<LogDataByTag> timeTagCountMap =
        logDashboardService.getLogCountByTag(accountId, projectIdentifier, orgIdentifier, serviceIdentifier,
            envIdentifier, CVMonitoringCategory.PERFORMANCE, startTime.toEpochMilli(), endTime.toEpochMilli());

    assertThat(timeTagCountMap).isNotEmpty();
    assertThat(timeTagCountMap.size()).isEqualTo(1);
    List<LogDataByTag.CountByTag> countMap = timeTagCountMap.first().getCountByTags();
    assertThat(countMap.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLogCountByTagForActivity() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);

    String activityId = "activityId";
    String verificationJobInstanceId = "verificationJobInstanceId";
    String verificationTaskId = generateUuid();
    Activity activity = DeploymentActivity.builder().deploymentTag("Build23").build();
    activity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
    when(mockActivityService.get(activityId)).thenReturn(activity);

    Set<String> verificationTaskIds = new HashSet<>();
    verificationTaskIds.add(verificationTaskId);

    when(mockVerificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId))
        .thenReturn(verificationTaskIds);
    when(mockVerificationTaskService.getCVConfigId(verificationTaskId)).thenReturn(cvConfigId);

    when(mockLogAnalysisService.getAnalysisResults(
             cvConfigId, Arrays.asList(LogAnalysisTag.values()), startTime, endTime))
        .thenReturn(resultList);

    SortedSet<LogDataByTag> timeTagCountMap = logDashboardService.getLogCountByTagForActivity(
        accountId, projectIdentifier, orgIdentifier, activityId, startTime, endTime);

    assertThat(timeTagCountMap).isNotEmpty();
    assertThat(timeTagCountMap.size()).isEqualTo(1);
    List<LogDataByTag.CountByTag> countMap = timeTagCountMap.first().getCountByTags();
    assertThat(countMap.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLogCountByTag_nothingInRange() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);

    when(mockCvConfigService.getConfigsOfProductionEnvironments(accountId, orgIdentifier, projectIdentifier,
             envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(createCvConfig(cvConfigId, serviceIdentifier)));
    when(mockLogAnalysisService.getAnalysisResults(
             cvConfigId, Arrays.asList(LogAnalysisTag.values()), startTime, endTime))
        .thenReturn(resultList);

    SortedSet<LogDataByTag> timeTagCountMap = logDashboardService.getLogCountByTag(accountId, projectIdentifier,
        orgIdentifier, serviceIdentifier, envIdentifier, CVMonitoringCategory.PERFORMANCE,
        startTime.plus(10, ChronoUnit.MINUTES).toEpochMilli(), startTime.plus(15, ChronoUnit.MINUTES).toEpochMilli());

    assertThat(timeTagCountMap).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLogCountByTagForActivity_nothingInRange() {
    String cvConfigId = generateUuid();
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);

    String activityId = "activityId";
    String verificationJobInstanceId = "verificationJobInstanceId";
    String verificationTaskId = generateUuid();
    Activity activity = DeploymentActivity.builder().deploymentTag("Build23").build();
    activity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
    when(mockActivityService.get(activityId)).thenReturn(activity);

    Set<String> verificationTaskIds = new HashSet<>();
    verificationTaskIds.add(verificationTaskId);

    when(mockVerificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId))
        .thenReturn(verificationTaskIds);
    when(mockVerificationTaskService.getCVConfigId(verificationTaskId)).thenReturn(cvConfigId);

    when(mockLogAnalysisService.getAnalysisResults(
             cvConfigId, Arrays.asList(LogAnalysisTag.values()), startTime, endTime))
        .thenReturn(resultList);

    SortedSet<LogDataByTag> timeTagCountMap =
        logDashboardService.getLogCountByTagForActivity(accountId, projectIdentifier, orgIdentifier, activityId,
            startTime.plus(10, ChronoUnit.MINUTES), startTime.plus(15, ChronoUnit.MINUTES));

    assertThat(timeTagCountMap).isEmpty();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetActivityLogs() {
    Instant startTime = Instant.now().minus(10, ChronoUnit.MINUTES);
    Instant endTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    String activityId = "activityId";
    String cvConfigId = "cvConfigId";
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = "verificationJobInstanceId";
    Activity activity = DeploymentActivity.builder().deploymentTag("Build23").build();
    activity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
    when(mockActivityService.get(activityId)).thenReturn(activity);

    Set<String> verificationTaskIds = new HashSet<>();
    verificationTaskIds.add(verificationTaskId);

    when(mockVerificationTaskService.getVerificationTaskIds(accountId, verificationJobInstanceId))
        .thenReturn(verificationTaskIds);
    when(mockVerificationTaskService.getCVConfigId(verificationTaskId)).thenReturn(cvConfigId);

    List<Long> labelList = Arrays.asList(1234l, 12345l, 123455l, 12334l);
    List<LogAnalysisResult> resultList = buildLogAnalysisResults(cvConfigId, false, startTime, endTime, labelList);
    when(mockLogAnalysisService.getAnalysisResults(anyString(), anyList(), any(), any())).thenReturn(resultList);
    when(mockLogAnalysisService.getAnalysisClusters(cvConfigId, new HashSet<>(labelList)))
        .thenReturn(buildLogAnalysisClusters(labelList));
    PageResponse<AnalyzedLogDataDTO> response =
        logDashboardService.getActivityLogs(activityId, accountId, projectIdentifier, orgIdentifier, envIdentifier,
            serviceIdentifier, startTime.toEpochMilli(), endTime.toEpochMilli(), false, 0, 10);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
  }

  private List<LogAnalysisResult> buildLogAnalysisResults(
      String cvConfigId, boolean anomalousOnly, Instant startTime, Instant endTime, List<Long> labels) {
    List<LogAnalysisResult> returnList = new ArrayList<>();
    Instant analysisTime = startTime;
    while (analysisTime.isBefore(endTime)) {
      List<AnalysisResult> resultList = new ArrayList<>();
      labels.forEach(label -> {
        AnalysisResult result = AnalysisResult.builder()
                                    .count(4)
                                    .label(label)
                                    .tag(anomalousOnly       ? LogAnalysisTag.UNKNOWN
                                            : label % 2 == 0 ? LogAnalysisTag.UNKNOWN
                                                             : LogAnalysisTag.KNOWN)
                                    .build();
        resultList.add(result);
      });

      returnList.add(LogAnalysisResult.builder()
                         .verificationTaskId(cvConfigId)
                         .analysisStartTime(startTime)
                         .analysisEndTime(endTime.minus(1, ChronoUnit.MINUTES))
                         .logAnalysisResults(resultList)
                         .build());
      analysisTime = analysisTime.plus(1, ChronoUnit.MINUTES);
    }
    return returnList;
  }

  private List<LogAnalysisCluster> buildLogAnalysisClusters(List<Long> labels) {
    List<LogAnalysisCluster> clusterList = new ArrayList<>();
    labels.forEach(label -> {
      clusterList.add(LogAnalysisCluster.builder()
                          .isEvicted(false)
                          .label(label)
                          .text("This is a dummy text for label " + label)
                          .frequencyTrend(Lists.newArrayList(Frequency.builder().timestamp(12353453L).count(1).build(),
                              Frequency.builder().timestamp(132312L).count(2).build(),
                              Frequency.builder().timestamp(132213L).count(3).build()))
                          .build());
    });
    return clusterList;
  }
  private CVConfig createCvConfig(String cvConfigId, String serviceIdentifier) {
    SplunkCVConfig splunkCVConfig = new SplunkCVConfig();
    splunkCVConfig.setUuid(cvConfigId);
    splunkCVConfig.setServiceIdentifier(serviceIdentifier);
    return splunkCVConfig;
  }
}
