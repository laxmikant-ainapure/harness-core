package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.pms.sdk.core.data.Outcome;

import software.wings.sm.StateExecutionData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("scriptStateExecutionData")
public class ScriptStateExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData, Outcome {
  private String name;
  private String activityId;
  private Map<String, String> sweepingOutputEnvVariables;
  private List<String> secretOutputVars;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    setExecutionData(executionDetails);
    return executionDetails;
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    setExecutionData(executionDetails);
    return executionDetails;
  }

  @Override
  public ScriptStateExecutionSummary getStepExecutionSummary() {
    return ScriptStateExecutionSummary.builder()
        .activityId(activityId)
        .sweepingOutputEnvVariables(sweepingOutputEnvVariables)
        .build();
  }

  private void setExecutionData(Map<String, ExecutionDataValue> executionDetails) {
    putNotNull(executionDetails, "activityId",
        ExecutionDataValue.builder().displayName("Activity Id").value(activityId).build());
    putNotNull(executionDetails, "name", ExecutionDataValue.builder().displayName("Name").value(name).build());
    if (isNotEmpty(sweepingOutputEnvVariables)) {
      putNotNull(executionDetails, "sweepingOutputEnvVariables",
          ExecutionDataValue.builder()
              .displayName("Script Output")
              .value(removeNullValuesAndMaskSecrets(sweepingOutputEnvVariables, secretOutputVars))
              .build());
    }
  }

  @Override
  public String getType() {
    return "scriptStateExecutionData";
  }
}
