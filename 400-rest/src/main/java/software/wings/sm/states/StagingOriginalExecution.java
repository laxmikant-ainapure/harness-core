package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;

import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@FieldNameConstants(innerTypeName = "StagingOriginalExecutionKeys")
public class StagingOriginalExecution extends State {
  @Inject SweepingOutputService sweepingOutputService;

  @Getter @Setter private String successfulExecutionId;

  public StagingOriginalExecution(String name) {
    super(name, StateType.STAGING_ORIGINAL_EXECUTION.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      String appId = context.getAppId();
      sweepingOutputService.copyOutputsForAnotherWorkflowExecution(
          appId, successfulExecutionId, context.getWorkflowExecutionId());
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .stateExecutionData(context.getStateExecutionData())
          .build();
    } catch (Exception ex) {
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(ex.getMessage())
          .stateExecutionData(context.getStateExecutionData())
          .build();
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Ignoring the Abort Event
  }
}
