package software.wings.service.impl.analysis;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.noop;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HQuery.excludeAuthority;
import static software.wings.common.VerificationConstants.DEMO_APPLICAITON_ID;
import static software.wings.common.VerificationConstants.DEMO_FAILURE_TS_STATE_EXECUTION_ID;
import static software.wings.common.VerificationConstants.DEMO_SUCCESS_TS_STATE_EXECUTION_ID;
import static software.wings.common.VerificationConstants.DEMO_WORKFLOW_EXECUTION_ID;
import static software.wings.delegatetasks.AppdynamicsDataCollectionTask.PREDECTIVE_HISTORY_MINUTES;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.utils.Misc.replaceDotWithUnicode;
import static software.wings.utils.Misc.replaceUnicodeWithDot;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.GoogleDataStoreServiceImpl;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.impl.analysis.TimeSeriesMLScores.TimeSeriesMLScoresKeys;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds.TimeSeriesMLTransactionThresholdKeys;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates.TimeSeriesMetricTemplatesKeys;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysisRecordKeys;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysisValue;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricHostAnalysisValue;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord.NewRelicMetricDataRecordKeys;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.LearningEngineService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by rsingh on 9/26/17.
 */
@Singleton
@Slf4j
public class MetricDataAnalysisServiceImpl implements MetricDataAnalysisService {
  public static final int DEFAULT_PAGE_SIZE = 10;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private LearningEngineService learningEngineService;
  @Inject protected SettingsService settingsService;
  @Inject private WorkflowService workflowService;
  @Inject private AppService appService;
  @Inject private DataStoreService dataStoreService;

  @Override
  public String getLastSuccessfulWorkflowExecutionIdWithData(
      StateType stateType, String appId, String workflowId, String serviceId, String infraMappingId, String envId) {
    List<String> successfulExecutions = new ArrayList<>();
    List<WorkflowExecution> executions =
        workflowExecutionService.getLastSuccessfulWorkflowExecutions(appId, workflowId, serviceId);

    // Filter the list of executions by the correct infra mapping ID also.
    for (WorkflowExecution execution : executions) {
      if (execution.getInfraMappingIds().contains(infraMappingId) && execution.getEnvId().equals(envId)) {
        logger.info("Execution {} contains infraMappingID {} and envId {}. So adding to successfulExecutions.",
            execution.getUuid(), infraMappingId, envId);
        successfulExecutions.add(execution.getUuid());
      }
    }

    for (String successfulExecution : successfulExecutions) {
      PageRequest<NewRelicMetricDataRecord> pageRequest =
          aPageRequest()
              .withLimit(UNLIMITED)
              .addFilter(NewRelicMetricDataRecordKeys.workflowExecutionId, Operator.EQ, successfulExecution)
              .build();

      final PageResponse<NewRelicMetricDataRecord> results =
          dataStoreService.list(NewRelicMetricDataRecord.class, pageRequest);
      List<NewRelicMetricDataRecord> rv =
          results.stream()
              .filter(dataRecord
                  -> dataRecord.getStateType().equals(stateType) && dataRecord.getServiceId().equals(serviceId)
                      && !ClusterLevel.H0.equals(dataRecord.getLevel())
                      && !ClusterLevel.HF.equals(dataRecord.getLevel()))
              .collect(Collectors.toList());

      if (isNotEmpty(rv)) {
        return successfulExecution;
      }
    }
    logger.warn(
        "Could not get a successful workflow to find control nodes for workflow {}, service {}", workflowId, serviceId);
    return null;
  }

  private RiskLevel getRiskLevel(int risk) {
    RiskLevel riskLevel;
    switch (risk) {
      case -1:
        riskLevel = RiskLevel.NA;
        break;
      case 0:
        riskLevel = RiskLevel.LOW;
        break;
      case 1:
        riskLevel = RiskLevel.MEDIUM;
        break;
      case 2:
        riskLevel = RiskLevel.HIGH;
        break;
      default:
        throw new IllegalArgumentException("Unknown risk level " + risk);
    }
    return riskLevel;
  }

  @Override
  public List<NewRelicMetricHostAnalysisValue> getToolTipForDemo(String stateExecutionId, String workflowExecutionId,
      int analysisMinute, String transactionName, String metricName, String groupName) {
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.createQuery(StateExecutionInstance.class).field("_id").equal(stateExecutionId).get();
    if (stateExecutionInstance == null) {
      logger.error("State execution instance not found for {}", stateExecutionId);
      throw new WingsException(ErrorCode.STATE_EXECUTION_INSTANCE_NOT_FOUND, stateExecutionId);
    }
    SettingAttribute settingAttribute =
        settingsService.get(((VerificationStateAnalysisExecutionData) stateExecutionInstance.fetchStateExecutionData())
                                .getServerConfigId());

    if (settingAttribute.getName().toLowerCase().endsWith("dev")
        || settingAttribute.getName().toLowerCase().endsWith("prod")) {
      if (stateExecutionInstance.getStatus() == ExecutionStatus.SUCCESS) {
        stateExecutionId = DEMO_SUCCESS_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType();
      } else {
        stateExecutionId = DEMO_FAILURE_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType();
      }
    }
    return getToolTip(stateExecutionId, analysisMinute, transactionName, metricName, groupName);
  }

  @Override
  public List<NewRelicMetricHostAnalysisValue> getToolTip(
      String stateExecutionId, int analysisMinute, String transactionName, String metricName, String groupName) {
    /* Ignore analysisMinutue. Leaving it as a parameter since UI sends it.
       Fetch the latest */
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
            .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .filter(MetricAnalysisRecordKeys.groupName,
                isEmpty(groupName) ? NewRelicMetricDataRecord.DEFAULT_GROUP_NAME : groupName)
            .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
            .get();
    if (timeSeriesMLAnalysisRecord == null) {
      return null;
    }
    timeSeriesMLAnalysisRecord.decompressTransactions();

    Map<String, String> txnNameToIdMap = new HashMap<>();

    for (Entry<String, TimeSeriesMLTxnSummary> txnSummaryEntry :
        timeSeriesMLAnalysisRecord.getTransactions().entrySet()) {
      txnNameToIdMap.put(txnSummaryEntry.getValue().getTxn_name(), txnSummaryEntry.getKey());
    }

    if (!txnNameToIdMap.containsKey(transactionName)) {
      return null;
    }

    TimeSeriesMLTxnSummary txnSummary =
        timeSeriesMLAnalysisRecord.getTransactions().get(txnNameToIdMap.get(transactionName));

    Map<String, String> metricNameToIdMap = new HashMap<>();
    for (Entry<String, TimeSeriesMLMetricSummary> mlMetricSummaryEntry : txnSummary.getMetrics().entrySet()) {
      metricNameToIdMap.put(mlMetricSummaryEntry.getValue().getMetric_name(), mlMetricSummaryEntry.getKey());
    }

    if (!metricNameToIdMap.containsKey(metricName)) {
      return null;
    }

    Map<String, TimeSeriesMLHostSummary> timeSeriesMLHostSummaryMap =
        txnSummary.getMetrics().get(metricNameToIdMap.get(metricName)).getResults();
    List<NewRelicMetricHostAnalysisValue> hostAnalysisValues = new ArrayList<>();

    if (timeSeriesMLHostSummaryMap != null) {
      for (Entry<String, TimeSeriesMLHostSummary> mlHostSummaryEntry : timeSeriesMLHostSummaryMap.entrySet()) {
        boolean isPredictiveAnalysis = false;
        if (mlHostSummaryEntry.getValue().getTimeSeriesMlAnalysisType() != null) {
          isPredictiveAnalysis =
              mlHostSummaryEntry.getValue().getTimeSeriesMlAnalysisType().equals(TimeSeriesMlAnalysisType.PREDICTIVE);
        }
        hostAnalysisValues.add(
            NewRelicMetricHostAnalysisValue.builder()
                .testHostName(mlHostSummaryEntry.getKey())
                .controlHostName(mlHostSummaryEntry.getValue().getNn())
                .controlValues(mlHostSummaryEntry.getValue().getControl_data())
                .testValues(mlHostSummaryEntry.getValue().getTest_data())
                .riskLevel(getRiskLevel(mlHostSummaryEntry.getValue().getRisk()))
                .testStartIndex(isPredictiveAnalysis ? PREDECTIVE_HISTORY_MINUTES : -1)
                .anomalies(isPredictiveAnalysis ? mlHostSummaryEntry.getValue().getAnomalies() : null)
                .build());
      }
    }
    return hostAnalysisValues;
  }

  @Override
  public TimeSeriesMLTransactionThresholds getCustomThreshold(String appId, StateType stateType, String serviceId,
      String cvConfigId, String groupName, String transactionName, String metricName)
      throws UnsupportedEncodingException {
    return wingsPersistence.createQuery(TimeSeriesMLTransactionThresholds.class, excludeAuthority)
        .filter(TimeSeriesMLTransactionThresholdKeys.stateType, stateType)
        .filter(TimeSeriesMLTransactionThresholdKeys.serviceId, serviceId)
        .filter(TimeSeriesMLTransactionThresholdKeys.groupName, groupName)
        .filter(TimeSeriesMLTransactionThresholdKeys.transactionName, transactionName)
        .filter(TimeSeriesMLTransactionThresholdKeys.metricName,
            URLDecoder.decode(metricName, StandardCharsets.UTF_8.name()))
        .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
        .get();
  }

  @Override
  public boolean saveCustomThreshold(String appId, StateType stateType, String serviceId, String cvConfigId,
      String transactionName, String groupName, TimeSeriesMetricDefinition metricDefinition) {
    final Query<TimeSeriesMLTransactionThresholds> query =
        wingsPersistence.createQuery(TimeSeriesMLTransactionThresholds.class)
            .filter("appId", appId)
            .filter(TimeSeriesMLTransactionThresholdKeys.serviceId, serviceId)
            .filter(TimeSeriesMLTransactionThresholdKeys.stateType, stateType)
            .filter(TimeSeriesMLTransactionThresholdKeys.groupName, groupName)
            .filter(TimeSeriesMLTransactionThresholdKeys.transactionName, transactionName)
            .filter(TimeSeriesMLTransactionThresholdKeys.metricName, metricDefinition.getMetricName())
            .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId);

    UpdateOperations<TimeSeriesMLTransactionThresholds> updateOperations =
        wingsPersistence.createUpdateOperations(TimeSeriesMLTransactionThresholds.class)
            .set(TimeSeriesMLTransactionThresholdKeys.thresholds, metricDefinition)
            .inc(TimeSeriesMLTransactionThresholdKeys.version);

    final TimeSeriesMLTransactionThresholds savedThreshold =
        wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());

    if (savedThreshold == null) {
      TimeSeriesMLTransactionThresholds timeSeriesMLTransactionThresholds =
          TimeSeriesMLTransactionThresholds.builder()
              .stateType(stateType)
              .groupName(groupName)
              .serviceId(serviceId)
              .cvConfigId(cvConfigId)
              .transactionName(transactionName)
              .metricName(metricDefinition.getMetricName())
              .thresholds(metricDefinition)
              .build();
      timeSeriesMLTransactionThresholds.setAppId(appId);
      wingsPersistence.save(timeSeriesMLTransactionThresholds);
    }
    return true;
  }

  @Override
  public boolean deleteCustomThreshold(String appId, StateType stateType, String serviceId, String cvConfigId,
      String groupName, String transactionName, String metricName) throws UnsupportedEncodingException {
    return wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLTransactionThresholds.class)
                                       .filter("appId", appId)
                                       .filter(TimeSeriesMLTransactionThresholdKeys.serviceId, serviceId)
                                       .filter(TimeSeriesMLTransactionThresholdKeys.stateType, stateType)
                                       .filter(TimeSeriesMLTransactionThresholdKeys.groupName, groupName)
                                       .filter(TimeSeriesMLTransactionThresholdKeys.transactionName, transactionName)
                                       .filter(TimeSeriesMLTransactionThresholdKeys.metricName,
                                           URLDecoder.decode(metricName, StandardCharsets.UTF_8.name()))
                                       .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId));
  }

  @Override
  public DeploymentTimeSeriesAnalysis getMetricsAnalysisForDemo(
      String stateExecutionId, Optional<Integer> offset, Optional<Integer> pageSize) {
    logger.info("Creating analysis summary for demo {}", stateExecutionId);
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.createQuery(StateExecutionInstance.class).field("_id").equal(stateExecutionId).get();
    if (stateExecutionInstance == null) {
      logger.error("State execution instance not found for {}", stateExecutionId);
      throw new WingsException(ErrorCode.STATE_EXECUTION_INSTANCE_NOT_FOUND, stateExecutionId);
    }

    SettingAttribute settingAttribute =
        settingsService.get(((VerificationStateAnalysisExecutionData) stateExecutionInstance.fetchStateExecutionData())
                                .getServerConfigId());

    if (settingAttribute.getName().toLowerCase().endsWith("dev")
        || settingAttribute.getName().toLowerCase().endsWith("prod")) {
      if (stateExecutionInstance.getStatus() == ExecutionStatus.SUCCESS) {
        return getMetricsAnalysis(
            DEMO_SUCCESS_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType(), offset, pageSize);
      } else {
        return getMetricsAnalysis(
            DEMO_FAILURE_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType(), offset, pageSize);
      }
    }
    return getMetricsAnalysis(stateExecutionId, offset, pageSize);
  }

  public Set<NewRelicMetricAnalysisRecord> getMetricsAnalysisForDemo(
      final String appId, final String stateExecutionId, final String workflowExecutionId) {
    logger.info("Creating analysis summary for demo {}", stateExecutionId);
    StateExecutionInstance stateExecutionInstance =
        wingsPersistence.createQuery(StateExecutionInstance.class).field("_id").equal(stateExecutionId).get();
    if (stateExecutionInstance == null) {
      logger.error("State execution instance not found for {}", stateExecutionId);
      throw new VerificationOperationException(ErrorCode.STATE_EXECUTION_INSTANCE_NOT_FOUND, stateExecutionId);
    }

    SettingAttribute settingAttribute =
        settingsService.get(((VerificationStateAnalysisExecutionData) stateExecutionInstance.fetchStateExecutionData())
                                .getServerConfigId());

    if (settingAttribute.getName().toLowerCase().endsWith("dev")
        || settingAttribute.getName().toLowerCase().endsWith("prod")) {
      if (stateExecutionInstance.getStatus() == ExecutionStatus.SUCCESS) {
        return getMetricsAnalysis(DEMO_APPLICAITON_ID,
            DEMO_SUCCESS_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType(), DEMO_WORKFLOW_EXECUTION_ID);
      } else {
        return getMetricsAnalysis(DEMO_APPLICAITON_ID,
            DEMO_FAILURE_TS_STATE_EXECUTION_ID + stateExecutionInstance.getStateType(), DEMO_WORKFLOW_EXECUTION_ID);
      }
    }
    return getMetricsAnalysis(appId, stateExecutionId, workflowExecutionId);
  }

  @Override
  public DeploymentTimeSeriesAnalysis getMetricsAnalysis(
      String stateExecutionId, Optional<Integer> offset, Optional<Integer> pageSize) {
    final TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
            .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
            .get();

    final NewRelicMetricAnalysisRecord newRelicMetricAnalysisRecord = timeSeriesMLAnalysisRecord == null
        ? wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class, excludeAuthority)
              .filter(NewRelicMetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
              .order(Sort.descending(NewRelicMetricAnalysisRecordKeys.analysisMinute))
              .get()
        : null;
    if (timeSeriesMLAnalysisRecord == null && newRelicMetricAnalysisRecord == null) {
      logger.info("No analysis found for {}", stateExecutionId);
      return null;
    }

    int txnOffset = offset.isPresent() ? offset.get() : 0;
    int txnPageSize = pageSize.isPresent() ? pageSize.get() : 10;

    final DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder()
            .stateExecutionId(stateExecutionId)
            .baseLineExecutionId(timeSeriesMLAnalysisRecord != null
                    ? timeSeriesMLAnalysisRecord.getBaseLineExecutionId()
                    : newRelicMetricAnalysisRecord.getBaseLineExecutionId())
            .metricAnalyses(new ArrayList<>())
            .build();

    List<NewRelicMetricAnalysis> metricAnalyses = new ArrayList<>();
    if (timeSeriesMLAnalysisRecord != null) {
      timeSeriesMLAnalysisRecord.decompressTransactions();
      if (timeSeriesMLAnalysisRecord.getTransactions() != null) {
        for (TimeSeriesMLTxnSummary txnSummary : timeSeriesMLAnalysisRecord.getTransactions().values()) {
          List<NewRelicMetricAnalysisValue> metricsList = new ArrayList<>();
          RiskLevel globalRisk = RiskLevel.NA;
          for (TimeSeriesMLMetricSummary mlMetricSummary : txnSummary.getMetrics().values()) {
            RiskLevel riskLevel = getRiskLevel(mlMetricSummary.getMax_risk());

            if (riskLevel.compareTo(globalRisk) < 0) {
              globalRisk = riskLevel;
            }
            List<NewRelicMetricHostAnalysisValue> hostAnalysisValues = new ArrayList<>();
            if (mlMetricSummary.getResults() != null) {
              for (Entry<String, TimeSeriesMLHostSummary> mlHostSummaryEntry :
                  mlMetricSummary.getResults().entrySet()) {
                hostAnalysisValues.add(NewRelicMetricHostAnalysisValue.builder()
                                           .testHostName(mlHostSummaryEntry.getKey())
                                           .controlHostName(mlHostSummaryEntry.getValue().getNn())
                                           .controlValues(mlHostSummaryEntry.getValue().getControl_data())
                                           .testValues(mlHostSummaryEntry.getValue().getTest_data())
                                           .riskLevel(getRiskLevel(mlHostSummaryEntry.getValue().getRisk()))
                                           .testStartIndex(-1)
                                           .anomalies(mlHostSummaryEntry.getValue().getAnomalies())
                                           .build());
              }
            }
            metricsList.add(NewRelicMetricAnalysisValue.builder()
                                .name(mlMetricSummary.getMetric_name())
                                .type(mlMetricSummary.getMetric_type())
                                .alertType(mlMetricSummary.getAlert_type())
                                .riskLevel(riskLevel)
                                .controlValue(mlMetricSummary.getControl_avg())
                                .testValue(mlMetricSummary.getTest_avg())
                                .hostAnalysisValues(hostAnalysisValues)
                                .build());
          }

          metricAnalyses.add(NewRelicMetricAnalysis.builder()
                                 .metricName(txnSummary.getTxn_name())
                                 .metricValues(metricsList)
                                 .riskLevel(globalRisk)
                                 .build());
        }
      }
    }

    if (newRelicMetricAnalysisRecord != null && isNotEmpty(newRelicMetricAnalysisRecord.getMetricAnalyses())) {
      for (NewRelicMetricAnalysis newRelicMetricAnalysis : newRelicMetricAnalysisRecord.getMetricAnalyses()) {
        String tag = isEmpty(newRelicMetricAnalysis.getTag()) ? ContinuousVerificationServiceImpl.HARNESS_DEFAULT_TAG
                                                              : newRelicMetricAnalysis.getTag();

        newRelicMetricAnalysis.setTag(tag);
        metricAnalyses.add(newRelicMetricAnalysis);
      }
    }

    setOverAllRisk(deploymentTimeSeriesAnalysis, metricAnalyses);
    deploymentTimeSeriesAnalysis.setTotal(metricAnalyses.size());

    Collections.sort(metricAnalyses);
    for (int i = txnOffset; i < metricAnalyses.size() && i < txnOffset + txnPageSize; i++) {
      deploymentTimeSeriesAnalysis.getMetricAnalyses().add(metricAnalyses.get(i));
    }

    return deploymentTimeSeriesAnalysis;
  }

  private void setOverAllRisk(
      DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis, List<NewRelicMetricAnalysis> metricAnalyses) {
    AtomicInteger highRisk = new AtomicInteger();
    AtomicInteger mediumRisk = new AtomicInteger();
    metricAnalyses.forEach(metricAnalysis -> {
      switch (metricAnalysis.getRiskLevel()) {
        case HIGH:
          highRisk.incrementAndGet();
          break;
        case MEDIUM:
          mediumRisk.incrementAndGet();
          break;
        case NA:
          noop();
          break;
        case LOW:
          noop();
          break;
        default:
          unhandled(metricAnalysis.getRiskLevel());
      }
    });

    if (highRisk.get() == 0 && mediumRisk.get() == 0) {
      deploymentTimeSeriesAnalysis.setMessage("No problems found");
    } else {
      StringBuffer message = new StringBuffer(20);
      if (highRisk.get() > 0) {
        message.append(highRisk + " high risk " + (highRisk.get() > 1 ? "transactions" : "transaction") + " found. ");
      }

      if (mediumRisk.get() > 0) {
        message.append(
            mediumRisk + " medium risk " + (mediumRisk.get() > 1 ? "transactions" : "transaction") + " found.");
      }

      deploymentTimeSeriesAnalysis.setMessage(message.toString());
    }

    if (highRisk.get() > 0) {
      deploymentTimeSeriesAnalysis.setRiskLevel(RiskLevel.HIGH);
    } else if (mediumRisk.get() > 0) {
      deploymentTimeSeriesAnalysis.setRiskLevel(RiskLevel.MEDIUM);
    } else {
      deploymentTimeSeriesAnalysis.setRiskLevel(RiskLevel.LOW);
    }
  }

  @Override
  public Set<NewRelicMetricAnalysisRecord> getMetricsAnalysis(
      final String appId, final String stateExecutionId, final String workflowExecutionId) {
    Set<NewRelicMetricAnalysisRecord> analysisRecords = new TreeSet<>();
    List<TimeSeriesMLAnalysisRecord> allAnalysisRecords =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
            .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
            .order(Sort.descending(TimeSeriesMLAnalysisRecord.CREATED_AT_KEY))
            .asList();

    Map<String, TimeSeriesMLAnalysisRecord> groupVsAnalysisRecord = new HashMap<>();
    allAnalysisRecords.forEach(analysisRecord -> {
      analysisRecord.decompressTransactions();
      if (!groupVsAnalysisRecord.containsKey(analysisRecord.getGroupName())) {
        groupVsAnalysisRecord.put(analysisRecord.getGroupName(), analysisRecord);
      }
    });
    Collection<TimeSeriesMLAnalysisRecord> timeSeriesMLAnalysisRecords = groupVsAnalysisRecord.values();
    timeSeriesMLAnalysisRecords.forEach(timeSeriesMLAnalysisRecord -> {
      NewRelicMetricAnalysisRecord metricAnalysisRecord =
          NewRelicMetricAnalysisRecord.builder()
              .appId(timeSeriesMLAnalysisRecord.getAppId())
              .stateType(timeSeriesMLAnalysisRecord.getStateType())
              .analysisMinute(timeSeriesMLAnalysisRecord.getAnalysisMinute())
              .stateExecutionId(timeSeriesMLAnalysisRecord.getStateExecutionId())
              .workflowExecutionId(timeSeriesMLAnalysisRecord.getWorkflowExecutionId())
              .baseLineExecutionId(timeSeriesMLAnalysisRecord.getBaseLineExecutionId())
              .showTimeSeries(true)
              .groupName(timeSeriesMLAnalysisRecord.getGroupName())
              .message(timeSeriesMLAnalysisRecord.getMessage())
              .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
              .build();
      analysisRecords.add(metricAnalysisRecord);
      if (timeSeriesMLAnalysisRecord.getTransactions() != null) {
        List<NewRelicMetricAnalysis> metricAnalysisList = new ArrayList<>();
        for (TimeSeriesMLTxnSummary txnSummary : timeSeriesMLAnalysisRecord.getTransactions().values()) {
          List<NewRelicMetricAnalysisValue> metricsList = new ArrayList<>();
          RiskLevel globalRisk = RiskLevel.NA;
          for (TimeSeriesMLMetricSummary mlMetricSummary : txnSummary.getMetrics().values()) {
            RiskLevel riskLevel = getRiskLevel(mlMetricSummary.getMax_risk());

            if (riskLevel.compareTo(globalRisk) < 0) {
              globalRisk = riskLevel;
            }
            metricsList.add(NewRelicMetricAnalysisValue.builder()
                                .name(mlMetricSummary.getMetric_name())
                                .type(mlMetricSummary.getMetric_type())
                                .alertType(mlMetricSummary.getAlert_type())
                                .riskLevel(riskLevel)
                                .controlValue(mlMetricSummary.getControl_avg())
                                .testValue(mlMetricSummary.getTest_avg())
                                .build());
          }
          metricAnalysisList.add(NewRelicMetricAnalysis.builder()
                                     .metricName(txnSummary.getTxn_name())
                                     .tag(txnSummary.getTxn_tag())
                                     .metricValues(metricsList)
                                     .riskLevel(globalRisk)
                                     .build());
        }
        metricAnalysisRecord.setMetricAnalyses(metricAnalysisList);
      }
    });
    List<NewRelicMetricAnalysisRecord> allMetricAnalyisRecords =
        wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class)
            .filter("appId", appId)
            .filter("stateExecutionId", stateExecutionId)
            .filter("workflowExecutionId", workflowExecutionId)
            .order(Sort.descending(NewRelicMetricAnalysisRecord.CREATED_AT_KEY))
            .asList();

    Map<String, NewRelicMetricAnalysisRecord> groupVsMetricAnalysisRecord = new HashMap<>();
    allMetricAnalyisRecords.forEach(analysisRecord -> {
      if (!groupVsMetricAnalysisRecord.containsKey(analysisRecord.getGroupName())) {
        groupVsMetricAnalysisRecord.put(analysisRecord.getGroupName(), analysisRecord);
      }
    });
    analysisRecords.addAll(groupVsMetricAnalysisRecord.values());

    if (isEmpty(analysisRecords)) {
      analysisRecords.add(NewRelicMetricAnalysisRecord.builder()
                              .showTimeSeries(false)
                              .stateType(StateType.APP_DYNAMICS)
                              .riskLevel(RiskLevel.NA)
                              .message("No data available")
                              .build());
    }

    Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups = getMetricGroups(appId, stateExecutionId);
    analysisRecords.forEach(analysisRecord -> {
      TimeSeriesMlAnalysisGroupInfo mlAnalysisGroupInfo = metricGroups.get(analysisRecord.getGroupName());
      analysisRecord.setDependencyPath(mlAnalysisGroupInfo == null ? null : mlAnalysisGroupInfo.getDependencyPath());
      analysisRecord.setMlAnalysisType(
          mlAnalysisGroupInfo == null ? TimeSeriesMlAnalysisType.COMPARATIVE : mlAnalysisGroupInfo.getMlAnalysisType());
      if (analysisRecord.getMetricAnalyses() != null) {
        int highRisk = 0;
        int mediumRisk = 0;
        for (NewRelicMetricAnalysis metricAnalysis : analysisRecord.getMetricAnalyses()) {
          final RiskLevel riskLevel = metricAnalysis.getRiskLevel();
          switch (riskLevel) {
            case HIGH:
              highRisk++;
              break;
            case MEDIUM:
              mediumRisk++;
              break;
            case NA:
              noop();
              break;
            case LOW:
              noop();
              break;
            default:
              unhandled(riskLevel);
          }
        }

        if (highRisk == 0 && mediumRisk == 0) {
          analysisRecord.setMessage("No problems found");
        } else {
          StringBuffer message = new StringBuffer(20);
          if (highRisk > 0) {
            message.append(highRisk + " high risk " + (highRisk > 1 ? "transactions" : "transaction") + " found. ");
          }

          if (mediumRisk > 0) {
            message.append(
                mediumRisk + " medium risk " + (mediumRisk > 1 ? "transactions" : "transaction") + " found.");
          }

          analysisRecord.setMessage(message.toString());
        }

        if (highRisk > 0) {
          analysisRecord.setRiskLevel(RiskLevel.HIGH);
        } else if (mediumRisk > 0) {
          analysisRecord.setRiskLevel(RiskLevel.MEDIUM);
        } else {
          analysisRecord.setRiskLevel(RiskLevel.LOW);
        }

        Collections.sort(analysisRecord.getMetricAnalyses());
      } else {
        analysisRecord.setRiskLevel(RiskLevel.NA);
      }

      if (analysisRecord.getStateType() == StateType.DYNA_TRACE && !isEmpty(analysisRecord.getMetricAnalyses())) {
        for (NewRelicMetricAnalysis analysis : analysisRecord.getMetricAnalyses()) {
          String metricName = analysis.getMetricName();
          String[] split = metricName.split(":");
          if (split == null || split.length == 1) {
            analysis.setDisplayName(metricName);
            analysis.setFullMetricName(metricName);
            continue;
          }
          String btName = split[0];
          String fullBTName = btName + " (" + metricName.substring(btName.length() + 1) + ")";
          analysis.setDisplayName(btName);
          analysis.setFullMetricName(fullBTName);
        }
      }
    });

    populateProgress(analysisRecords);
    return analysisRecords;
  }

  private void populateProgress(Set<NewRelicMetricAnalysisRecord> analysisRecords) {
    if (isEmpty(analysisRecords)) {
      return;
    }

    analysisRecords.forEach(analysisRecord -> {
      AnalysisContext analysisContext =
          wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
              .filter(AnalysisContextKeys.stateExecutionId, analysisRecord.getStateExecutionId())
              .get();
      if (analysisContext == null) {
        return;
      }
      int numOfAnalysis = (int) wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
                              .filter(MetricAnalysisRecordKeys.stateExecutionId, analysisRecord.getStateExecutionId())
                              .filter(MetricAnalysisRecordKeys.groupName, analysisRecord.getGroupName())
                              .count();

      int duration = analysisContext.getTimeDuration();
      analysisRecord.setProgress((numOfAnalysis * 100) / duration);
    });
  }

  @Override
  public boolean isStateValid(String appId, String stateExecutionId) {
    return workflowService.isStateValid(appId, stateExecutionId);
  }

  @Override
  public void saveMetricTemplates(String appId, StateType stateType, String stateExecutionId, String cvConfigId,
      Map<String, TimeSeriesMetricDefinition> metricTemplates) {
    TimeSeriesMetricTemplates metricTemplate = TimeSeriesMetricTemplates.builder()
                                                   .stateType(stateType)
                                                   .stateExecutionId(stateExecutionId)
                                                   .metricTemplates(metricTemplates)
                                                   .cvConfigId(cvConfigId)
                                                   .build();
    metricTemplate.setAppId(appId);
    wingsPersistence.save(metricTemplate);
  }

  @Override
  public void saveMetricGroups(
      String appId, StateType stateType, String stateExecutionId, Map<String, TimeSeriesMlAnalysisGroupInfo> groups) {
    Map<String, TimeSeriesMlAnalysisGroupInfo> toSave = new HashMap<>();
    groups.forEach((groupName, timeSeriesMlAnalysisGroupInfo) -> {
      groupName = replaceDotWithUnicode(groupName);
      timeSeriesMlAnalysisGroupInfo.setGroupName(groupName);
      toSave.put(groupName, timeSeriesMlAnalysisGroupInfo);
    });
    logger.info("Creating groups for appId {}, stateType {}, groups {}", appId, stateType, toSave);
    wingsPersistence.save(TimeSeriesMetricGroup.builder()
                              .appId(appId)
                              .stateType(stateType)
                              .stateExecutionId(stateExecutionId)
                              .groups(toSave)
                              .build());
  }

  @Override
  public Map<String, TimeSeriesMlAnalysisGroupInfo> getMetricGroups(String appId, String stateExecutionId) {
    TimeSeriesMetricGroup timeSeriesMetricGroup = wingsPersistence.createQuery(TimeSeriesMetricGroup.class)
                                                      .field("stateExecutionId")
                                                      .equal(stateExecutionId)
                                                      .field("appId")
                                                      .equal(appId)
                                                      .get();

    if (timeSeriesMetricGroup != null) {
      Map<String, TimeSeriesMlAnalysisGroupInfo> toReturn = new HashMap<>();
      timeSeriesMetricGroup.getGroups().forEach((groupName, timeSeriesMlAnalysisGroupInfo) -> {
        groupName = replaceUnicodeWithDot(groupName);
        timeSeriesMlAnalysisGroupInfo.setGroupName(groupName);
        toReturn.put(groupName, timeSeriesMlAnalysisGroupInfo);
      });

      return toReturn;
    }

    return new ImmutableMap.Builder<String, TimeSeriesMlAnalysisGroupInfo>()
        .put(DEFAULT_GROUP_NAME,
            TimeSeriesMlAnalysisGroupInfo.builder()
                .groupName(DEFAULT_GROUP_NAME)
                .dependencyPath(DEFAULT_GROUP_NAME)
                .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                .build())
        .build();
  }

  @Override
  public void cleanUpForMetricRetry(String stateExecutionId) {
    // delete the metric templates
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMetricTemplates.class)
                                .filter(TimeSeriesMetricTemplatesKeys.stateExecutionId, stateExecutionId));

    // delete new relic metric records
    wingsPersistence.delete(wingsPersistence.createQuery(NewRelicMetricDataRecord.class)
                                .filter(NewRelicMetricDataRecordKeys.stateExecutionId, stateExecutionId));

    // delete new relic analysis records
    wingsPersistence.delete(
        wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).filter("stateExecutionId", stateExecutionId));

    // delete time series analysis records
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                                .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId));

    // delete time series scores records
    wingsPersistence.delete(wingsPersistence.createQuery(TimeSeriesMLScores.class)
                                .filter(TimeSeriesMLScoresKeys.stateExecutionId, stateExecutionId));

    // delete cv dashboard execution data
    wingsPersistence.delete(
        wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class)
            .filter(ContinuousVerificationExecutionMetaDataKeys.stateExecutionId, stateExecutionId));

    // delete learning engine tasks
    wingsPersistence.delete(
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class).filter("state_execution_id", stateExecutionId));

    // delete the metric groups
    wingsPersistence.delete(
        wingsPersistence.createQuery(TimeSeriesMetricGroup.class).filter("stateExecutionId", stateExecutionId));

    // delete verification service tasks
    wingsPersistence.delete(wingsPersistence.createQuery(AnalysisContext.class)
                                .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId));
  }

  @Override
  public void saveRawDataToGoogleDataStore(
      String accountId, String stateExecutionId, ExecutionStatus executionStatus, String serviceId) {
    if (dataStoreService instanceof GoogleDataStoreServiceImpl) {
      try {
        Query<TimeSeriesMLAnalysisRecord> query =
            wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
                .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId);

        Map<String, Map<String, TimeSeriesRawData>> rawDataMap = new HashMap<>();
        try (HIterator<TimeSeriesMLAnalysisRecord> records = new HIterator<>(query.fetch())) {
          for (TimeSeriesMLAnalysisRecord record : records) {
            TimeSeriesRawData.populateRawDataFromAnalysisRecords(
                record, accountId, executionStatus, rawDataMap, serviceId);
          }
        }

        List<TimeSeriesRawData> rawDataList = new ArrayList<>();
        rawDataMap.values().forEach(metricMap -> rawDataList.addAll(metricMap.values()));

        dataStoreService.save(TimeSeriesRawData.class, rawDataList, true);
        logger.info("Saved {} raw data time series records to GoogleDataStore", rawDataList.size());
      } catch (Exception e) {
        logger.error("Exception while saving time series raw data to Google DataStore", e);
      }
    }
  }
}
