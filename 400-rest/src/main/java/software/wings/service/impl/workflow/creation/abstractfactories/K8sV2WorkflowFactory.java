package software.wings.service.impl.workflow.creation.abstractfactories;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.exception.InvalidRequestException;

import software.wings.service.impl.workflow.creation.K8V2BlueGreenWorkflowCreator;
import software.wings.service.impl.workflow.creation.K8V2CanaryWorkflowCreator;
import software.wings.service.impl.workflow.creation.K8V2RollingWorkflowCreator;
import software.wings.service.impl.workflow.creation.WorkflowCreator;
import software.wings.service.intfc.workflow.creation.WorkflowCreatorFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
class K8sV2WorkflowFactory implements WorkflowCreatorFactory {
  @Inject private K8V2CanaryWorkflowCreator k8V2CanaryWorkflowCreator;
  @Inject private K8V2RollingWorkflowCreator k8V2RollingWorkflowCreator;
  @Inject private K8V2BlueGreenWorkflowCreator k8V2BlueGreenWorkflowCreator;

  @Override
  public WorkflowCreator getWorkflowCreator(OrchestrationWorkflowType type) {
    switch (type) {
      case CANARY:
      case MULTI_SERVICE:
        return k8V2CanaryWorkflowCreator;
      case BLUE_GREEN:
        return k8V2BlueGreenWorkflowCreator;
      case ROLLING:
        return k8V2RollingWorkflowCreator;
      default:
        throw new InvalidRequestException(String.format("WorkflowType %s not supported for Kubernetes V2", type), USER);
    }
  }
}
