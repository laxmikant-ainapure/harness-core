package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.Status;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.tasks.Cd1SetupFields;
import io.harness.tasks.ResponseData;

import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.dynatrace.DynaTraceDataCollectionInfo;
import software.wings.service.impl.dynatrace.DynaTraceTimeSeries;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.service.intfc.verification.CVActivityLogService.Logger;
import software.wings.sm.ExecutionResponse;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Created by rsingh on 3/22/18.
 */
public class DynatraceStateTest extends APMStateVerificationTestBase {
  @Inject private MetricDataAnalysisService metricDataAnalysisService;
  @Mock private DynaTraceService dynaTraceService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  private DynatraceState dynatraceState;
  private List<String> serviceMethods = Lists.newArrayList(UUID.randomUUID().toString(), UUID.randomUUID().toString());

  @Before
  public void setup() throws IllegalAccessException {
    setupCommon();
    MockitoAnnotations.initMocks(this);
    setupCommonMocks();

    AppService appService = mock(AppService.class);
    when(appService.getAccountIdByAppId(anyString())).thenReturn(generateUuid());
    when(appService.get(anyString()))
        .thenReturn(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    dynatraceState = new DynatraceState("DynatraceState");

    dynatraceState.setTimeDuration("15");
    FieldUtils.writeField(dynatraceState, "appService", appService, true);
    FieldUtils.writeField(dynatraceState, "configuration", configuration, true);
    FieldUtils.writeField(dynatraceState, "metricAnalysisService", metricDataAnalysisService, true);
    FieldUtils.writeField(dynatraceState, "settingsService", settingsService, true);
    FieldUtils.writeField(dynatraceState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(dynatraceState, "delegateService", delegateService, true);
    FieldUtils.writeField(dynatraceState, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(dynatraceState, "secretManager", secretManager, true);
    FieldUtils.writeField(dynatraceState, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(dynatraceState, "continuousVerificationService", continuousVerificationService, true);
    FieldUtils.writeField(dynatraceState, "workflowExecutionBaselineService", workflowExecutionBaselineService, true);
    FieldUtils.writeField(dynatraceState, "featureFlagService", mockFeatureFlagService, true);
    FieldUtils.writeField(dynatraceState, "versionInfoManager", versionInfoManager, true);
    FieldUtils.writeField(dynatraceState, "appService", appService, true);
    FieldUtils.writeField(dynatraceState, "accountService", accountService, true);
    FieldUtils.writeField(dynatraceState, "cvActivityLogService", cvActivityLogService, true);
    FieldUtils.writeField(dynatraceState, "dynaTraceService", dynaTraceService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(mock(Logger.class));
    when(mockFeatureFlagService.isEnabled(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDefaultComparsionStrategy() {
    DynatraceState dynatraceState = new DynatraceState("DynatraceState");
    assertThat(dynatraceState.getComparisonStrategy()).isEqualTo(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void compareTestAndControl() {
    DynatraceState dynatraceState = new DynatraceState("DynatraceState");
    for (int i = 1; i <= 7; i++) {
      assertThat(dynatraceState.getLastExecutionNodes(executionContext).get(DynatraceState.CONTROL_HOST_NAME + i))
          .isEqualTo(DEFAULT_GROUP_NAME);
    }

    assertThat(dynatraceState.getCanaryNewHostNames(executionContext))
        .isEqualTo(Collections.singletonMap(DynatraceState.TEST_HOST_NAME, DEFAULT_GROUP_NAME));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTriggerCollection() throws ParseException {
    assertThat(wingsPersistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
    DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder()
                                          .accountId(accountId)
                                          .dynaTraceUrl("dynatrace-url")
                                          .apiToken(UUID.randomUUID().toString().toCharArray())
                                          .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("prometheus-config")
                                            .withValue(dynaTraceConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    dynatraceState.setAnalysisServerConfigId(settingAttribute.getUuid());
    DynatraceState spyState = spy(dynatraceState);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    when(workflowStandardParams.getEnv())
        .thenReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(response.getErrorMessage())
        .isEqualTo(
            "No baseline was set for the workflow. Workflow running with auto baseline. No previous execution found. This will be the baseline run");

    List<DelegateTask> tasks = wingsPersistence.createQuery(DelegateTask.class, excludeAuthority).asList();
    assertThat(tasks).hasSize(1);
    DelegateTask task = tasks.get(0);
    assertThat(task.getData().getTaskType()).isEqualTo(TaskType.DYNA_TRACE_METRIC_DATA_COLLECTION_TASK.name());

    DynaTraceDataCollectionInfo expectedCollectionInfo =
        DynaTraceDataCollectionInfo.builder()
            .dynaTraceConfig(dynaTraceConfig)
            .applicationId(appId)
            .stateExecutionId(stateExecutionId)
            .workflowId(workflowId)
            .workflowExecutionId(workflowExecutionId)
            .serviceId(serviceId)
            .startTime(0)
            .collectionTime(Integer.parseInt(dynatraceState.getTimeDuration()))
            .dynatraceServiceIds(new HashSet<>())
            .timeSeriesDefinitions(Lists.newArrayList(DynaTraceTimeSeries.values()))
            .dataCollectionMinute(0)
            .encryptedDataDetails(
                secretManager.getEncryptionDetails((DynaTraceConfig) settingAttribute.getValue(), null, null))
            .analysisComparisonStrategy(dynatraceState.getComparisonStrategy())
            .build();

    final DynaTraceDataCollectionInfo actualCollectionInfo =
        (DynaTraceDataCollectionInfo) task.getData().getParameters()[0];
    expectedCollectionInfo.setStartTime(actualCollectionInfo.getStartTime());
    assertThat(actualCollectionInfo).isEqualTo(expectedCollectionInfo);
    assertThat(task.getAccountId()).isEqualTo(accountId);
    assertThat(task.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(appId);
    Map<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        cvExecutionMetaData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);
    assertThat(cvExecutionMetaData).isNotNull();
    ContinuousVerificationExecutionMetaData continuousVerificationExecutionMetaData1 =
        cvExecutionMetaData.get(1519171200000L)
            .get("dummy artifact")
            .get("dummy env/dummy workflow")
            .values()
            .iterator()
            .next()
            .get("BASIC")
            .get(0);
    assertThat(accountId).isEqualTo(continuousVerificationExecutionMetaData1.getAccountId());
    assertThat("dummy artifact").isEqualTo(continuousVerificationExecutionMetaData1.getArtifactName());
    assertThat(continuousVerificationExecutionMetaData1.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

    VerificationStateAnalysisExecutionData metricAnalysisExecutionData =
        VerificationStateAnalysisExecutionData.builder().build();
    VerificationDataAnalysisResponse metricDataAnalysisResponse =
        VerificationDataAnalysisResponse.builder().stateExecutionData(metricAnalysisExecutionData).build();
    metricDataAnalysisResponse.setExecutionStatus(ExecutionStatus.FAILED);
    Map<String, ResponseData> responseMap = new HashMap<>();
    responseMap.put("somekey", metricDataAnalysisResponse);
    dynatraceState.handleAsyncResponse(executionContext, responseMap);

    cvExecutionMetaData =
        continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);
    continuousVerificationExecutionMetaData1 = cvExecutionMetaData.get(1519171200000L)
                                                   .get("dummy artifact")
                                                   .get("dummy env/dummy workflow")
                                                   .values()
                                                   .iterator()
                                                   .next()
                                                   .get("BASIC")
                                                   .get(0);
    assertThat(continuousVerificationExecutionMetaData1.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTriggerCollection_withServiceEntityId() {
    when(dynaTraceService.validateDynatraceServiceId(anyString(), anyString())).thenReturn(true);
    dynatraceState.setServiceEntityId("entityID1");
    DelegateTask task = setupAndGetDelegateTaskFromTrigger();
    final DynaTraceDataCollectionInfo actualCollectionInfo =
        (DynaTraceDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(actualCollectionInfo.getDynatraceServiceIds()).containsExactly("entityID1");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTriggerCollection_withServiceEntityIdMultiServiceFFTurnedOff() {
    when(mockFeatureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(dynaTraceService.validateDynatraceServiceId(anyString(), anyString())).thenReturn(true);
    dynatraceState.setServiceEntityId("entityID1,entityID2");
    ExecutionResponse response = executeTrigger();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTriggerCollection_withServiceEntityIdMultiService() {
    when(dynaTraceService.validateDynatraceServiceId(anyString(), anyString())).thenReturn(true);
    dynatraceState.setServiceEntityId("entityID1,entityID2");
    DelegateTask task = setupAndGetDelegateTaskFromTrigger();
    final DynaTraceDataCollectionInfo actualCollectionInfo =
        (DynaTraceDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(actualCollectionInfo.getDynatraceServiceIds()).contains("entityID1", "entityID2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTriggerCollection_withServiceEntityIdMultiServiceWithSpace() {
    when(dynaTraceService.validateDynatraceServiceId(anyString(), anyString())).thenReturn(true);
    dynatraceState.setServiceEntityId("entityID1 , entityID2");
    DelegateTask task = setupAndGetDelegateTaskFromTrigger();
    final DynaTraceDataCollectionInfo actualCollectionInfo =
        (DynaTraceDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(actualCollectionInfo.getDynatraceServiceIds()).contains("entityID1", "entityID2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTriggerCollection_withServiceName() {
    when(dynaTraceService.resolveDynatraceServiceNameToId(anyString(), anyString())).thenReturn("entityID3");
    dynatraceState.setServiceEntityId("serviceName3");
    DelegateTask task = setupAndGetDelegateTaskFromTrigger();
    final DynaTraceDataCollectionInfo actualCollectionInfo =
        (DynaTraceDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(actualCollectionInfo.getDynatraceServiceIds()).containsExactly("entityID3");
  }

  private DelegateTask setupAndGetDelegateTaskFromTrigger() {
    ExecutionResponse response = executeTrigger();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

    List<DelegateTask> tasks = wingsPersistence.createQuery(DelegateTask.class, excludeAuthority).asList();
    assertThat(tasks).hasSize(1);
    return tasks.get(0);
  }

  private ExecutionResponse executeTrigger() {
    DynaTraceConfig dynaTraceConfig = DynaTraceConfig.builder()
                                          .accountId(accountId)
                                          .dynaTraceUrl("dynatrace-url")
                                          .apiToken(UUID.randomUUID().toString().toCharArray())
                                          .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("prometheus-config")
                                            .withValue(dynaTraceConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    dynatraceState.setAnalysisServerConfigId(settingAttribute.getUuid());
    DynatraceState spyState = spy(dynatraceState);
    doReturn(
        AbstractAnalysisState.CVInstanceApiResponse.builder().newNodesTrafficShiftPercent(Optional.empty()).build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    when(workflowStandardParams.getEnv())
        .thenReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    return spyState.execute(executionContext);
  }
}
