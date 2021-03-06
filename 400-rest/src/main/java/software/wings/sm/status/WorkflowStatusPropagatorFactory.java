package software.wings.sm.status;

import io.harness.beans.ExecutionStatus;

import software.wings.sm.status.handlers.NoopWorkflowPropagator;
import software.wings.sm.status.handlers.WorkflowPausePropagator;
import software.wings.sm.status.handlers.WorkflowResumePropagator;

import com.google.inject.Inject;

public class WorkflowStatusPropagatorFactory {
  @Inject private WorkflowPausePropagator workflowPausePropagator;
  @Inject private NoopWorkflowPropagator noopWorkflowPropagator;
  @Inject private WorkflowResumePropagator workflowResumePropagator;

  public WorkflowStatusPropagator obtainHandler(ExecutionStatus status) {
    switch (status) {
      case PAUSED:
        return workflowPausePropagator;
      case RESUMED:
        return workflowResumePropagator;
      default:
        return noopWorkflowPropagator;
    }
  }
}
