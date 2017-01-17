package software.wings.beans;

import static software.wings.beans.Graph.Node.Builder.aNode;

import software.wings.beans.Graph.Node;
import software.wings.common.Constants;
import software.wings.common.UUIDGenerator;
import software.wings.sm.StateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

/**
 * Created by rishi on 12/21/16.
 */
public class WorkflowPhase {
  private String uuid = UUIDGenerator.getUuid();
  private String name;
  private @NotNull String serviceId;
  private @NotNull DeploymentType deploymentType;
  private @NotNull String computerProviderId;
  private String deploymentMasterId;

  private List<PhaseStep> phaseSteps = new ArrayList<>();

  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getComputerProviderId() {
    return computerProviderId;
  }

  public void setComputerProviderId(String computerProviderId) {
    this.computerProviderId = computerProviderId;
  }

  public DeploymentType getDeploymentType() {
    return deploymentType;
  }

  public void setDeploymentType(DeploymentType deploymentType) {
    this.deploymentType = deploymentType;
  }

  public String getDeploymentMasterId() {
    return deploymentMasterId;
  }

  public void setDeploymentMasterId(String deploymentMasterId) {
    this.deploymentMasterId = deploymentMasterId;
  }

  public List<PhaseStep> getPhaseSteps() {
    return phaseSteps;
  }

  public void addPhaseStep(PhaseStep phaseStep) {
    this.phaseSteps.add(phaseStep);
  }

  public void setPhaseSteps(List<PhaseStep> phaseSteps) {
    this.phaseSteps = phaseSteps;
  }

  public Node generatePhaseNode() {
    return aNode()
        .withId(uuid)
        .withName(name)
        .withType(StateType.PHASE.name())
        .addProperty("serviceId", serviceId)
        .addProperty("deploymentType", deploymentType)
        .addProperty("computerProviderId", computerProviderId)
        .addProperty("deploymentMasterId", deploymentMasterId)
        .addProperty(Constants.SUB_WORKFLOW_ID, uuid)
        .build();
  }

  public Map<String, Object> params() {
    Map<String, Object> params = new HashMap<>();
    params.put("serviceId", serviceId);
    params.put("computerProviderId", computerProviderId);
    params.put("deploymentType", deploymentType);
    params.put("deploymentMasterId", deploymentMasterId);
    return params;
  }

  public static final class WorkflowPhaseBuilder {
    private String uuid = UUIDGenerator.getUuid();
    private String name;
    private String serviceId;
    private String computerProviderId;
    private DeploymentType deploymentType;
    private String deploymentMasterId;
    private List<PhaseStep> phaseSteps = new ArrayList<>();

    private WorkflowPhaseBuilder() {}

    public static WorkflowPhaseBuilder aWorkflowPhase() {
      return new WorkflowPhaseBuilder();
    }

    public WorkflowPhaseBuilder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public WorkflowPhaseBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public WorkflowPhaseBuilder withServiceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public WorkflowPhaseBuilder withComputerProviderId(String computerProviderId) {
      this.computerProviderId = computerProviderId;
      return this;
    }

    public WorkflowPhaseBuilder withDeploymentType(DeploymentType deploymentType) {
      this.deploymentType = deploymentType;
      return this;
    }

    public WorkflowPhaseBuilder withDeploymentMasterId(String deploymentMasterId) {
      this.deploymentMasterId = deploymentMasterId;
      return this;
    }

    public WorkflowPhaseBuilder addPhaseStep(PhaseStep phaseStep) {
      this.phaseSteps.add(phaseStep);
      return this;
    }

    public WorkflowPhase build() {
      WorkflowPhase workflowPhase = new WorkflowPhase();
      workflowPhase.setUuid(uuid);
      workflowPhase.setName(name);
      workflowPhase.setServiceId(serviceId);
      workflowPhase.setComputerProviderId(computerProviderId);
      workflowPhase.setDeploymentType(deploymentType);
      workflowPhase.setDeploymentMasterId(deploymentMasterId);
      workflowPhase.setPhaseSteps(phaseSteps);
      return workflowPhase;
    }
  }
}
