package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class RepeatStateHelper {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WorkflowExecutionService workflowExecutionService;

  public List<ContextElement> filterElementsWithArtifactFromLastDeployment(
      ExecutionContextImpl context, List<ContextElement> repeatElements) {
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
    if (workflowExecution == null) {
      throw new InvalidRequestException("Execution No longer Exists : " + context.getWorkflowExecutionId());
    }
    List<ContextElement> filteredElements = new ArrayList<>();
    Artifact rollbackArtifact = workflowExecution.getArtifacts().get(0);
    for (ContextElement contextElement : repeatElements) {
      if (ContextElementType.INSTANCE == contextElement.getElementType()) {
        Artifact previousArtifact = serviceResourceService.findPreviousArtifact(
            context.getAppId(), context.getWorkflowExecutionId(), contextElement);
        if (previousArtifact == null || rollbackArtifact == null
            || (rollbackArtifact.getUuid().equals(previousArtifact.getUuid()))) {
          filteredElements.add(contextElement);
        }
      } else {
        filteredElements.add(contextElement);
      }
    }
    return filteredElements;
  }
}
