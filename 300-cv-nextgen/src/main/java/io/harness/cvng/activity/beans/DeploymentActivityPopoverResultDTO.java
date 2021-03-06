package io.harness.cvng.activity.beans;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Builder
public class DeploymentActivityPopoverResultDTO {
  String tag;
  String serviceName;
  DeploymentPopoverSummary preProductionDeploymentSummary;
  DeploymentPopoverSummary productionDeploymentSummary;
  DeploymentPopoverSummary postDeploymentSummary;
  @Value
  @Builder
  public static class DeploymentPopoverSummary {
    int total;
    List<VerificationResult> verificationResults;
  }
  @Value
  @Builder
  public static class VerificationResult {
    String jobName;
    ActivityVerificationStatus status;
    Risk risk;
    Long remainingTimeMs;
    int progressPercentage;
    Long startTime;
  }
}
