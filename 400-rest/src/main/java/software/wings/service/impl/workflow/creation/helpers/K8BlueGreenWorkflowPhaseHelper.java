package software.wings.service.impl.workflow.creation.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.k8s.model.K8sExpressions.primaryServiceNameExpression;
import static io.harness.k8s.model.K8sExpressions.stageServiceNameExpression;

import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.sm.StateType.K8S_BLUE_GREEN_DEPLOY;
import static software.wings.sm.StateType.KUBERNETES_SWAP_SERVICE_SELECTORS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.common.WorkflowConstants;
import software.wings.service.impl.workflow.WorkflowServiceHelper;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@OwnedBy(CDC)
public class K8BlueGreenWorkflowPhaseHelper extends K8AbstractWorkflowHelper {
  @Override
  public List<PhaseStep> getWorkflowPhaseSteps() {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    phaseSteps.add(getDeployPhaseStep());
    phaseSteps.add(getVerifyPhaseStep());
    phaseSteps.add(getRoutUpdatePhaseStep());
    phaseSteps.add(getWrapUpStep());
    return phaseSteps;
  }

  @Override
  public List<PhaseStep> getRollbackPhaseSteps() {
    List<PhaseStep> rollbackPhaseSteps = new ArrayList<>();
    rollbackPhaseSteps.add(getRollbackRouteUpdatePhaseStep());
    rollbackPhaseSteps.add(getRollbackWrapUpPhaseStep());
    return rollbackPhaseSteps;
  }

  // Workflow Phase Steps
  private PhaseStep getWrapUpStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.WRAP_UP).build();
  }

  private PhaseStep getRoutUpdatePhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.ROUTE_UPDATE)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                     .name(WorkflowServiceHelper.KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                     .properties(ImmutableMap.<String, Object>builder()
                                     .put("service1", primaryServiceNameExpression)
                                     .put("service2", stageServiceNameExpression)
                                     .build())
                     .build())
        .build();
  }

  private PhaseStep getDeployPhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.DEPLOY)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(K8S_BLUE_GREEN_DEPLOY.name())
                     .name(WorkflowConstants.K8S_STAGE_DEPLOY)
                     .properties(new HashMap<>())
                     .build())
        .build();
  }

  private PhaseStep getVerifyPhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, "Verify").build();
  }

  // Rollback Phase Steps
  private PhaseStep getRollbackWrapUpPhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.WRAP_UP)
        .withPhaseStepNameForRollback(WorkflowServiceHelper.WRAP_UP)
        .withRollback(true)
        .build();
  }

  private PhaseStep getRollbackRouteUpdatePhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.ROUTE_UPDATE)
        .withPhaseStepNameForRollback(WorkflowServiceHelper.ROUTE_UPDATE)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                     .name(WorkflowServiceHelper.KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                     .properties(ImmutableMap.<String, Object>builder()
                                     .put("service1", primaryServiceNameExpression)
                                     .put("service2", stageServiceNameExpression)
                                     .build())
                     .rollback(true)
                     .build())
        .withStatusForRollback(ExecutionStatus.SUCCESS)
        .withRollback(true)
        .build();
  }
}
