package software.wings.service.impl.analysis;

import software.wings.metrics.RiskLevel;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DeploymentTimeSeriesAnalysisKeys")
public class DeploymentTimeSeriesAnalysis {
  private String stateExecutionId;
  private String customThresholdRefId;
  private String baseLineExecutionId;
  private String message;
  private RiskLevel riskLevel;
  private int total;
  private List<NewRelicMetricAnalysis> metricAnalyses;
}
