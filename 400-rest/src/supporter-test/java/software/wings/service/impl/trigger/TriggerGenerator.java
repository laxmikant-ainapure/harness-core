package software.wings.service.impl.trigger;

import static software.wings.beans.trigger.ArtifactTriggerCondition.ArtifactTriggerConditionBuilder;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import io.harness.beans.WorkflowType;

import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerBuilder;
import software.wings.beans.trigger.TriggerCondition;

public class TriggerGenerator {
  public Trigger ensureTrigger(Trigger trigger) {
    final TriggerBuilder triggerBuilder = Trigger.builder();

    if (trigger.getAppId() != null) {
      triggerBuilder.appId(trigger.getAppId());
    } else {
      triggerBuilder.appId(APP_ID);
    }

    if (trigger.getWorkflowType() != null) {
      triggerBuilder.workflowType(trigger.getWorkflowType());
    } else {
      triggerBuilder.workflowType(WorkflowType.PIPELINE);
    }

    if (trigger.getWorkflowId() != null) {
      triggerBuilder.workflowId(trigger.getWorkflowId());
    } else {
      triggerBuilder.workflowId(WORKFLOW_ID);
    }
    if (trigger.getName() != null) {
      triggerBuilder.name(trigger.getName());
    } else {
      triggerBuilder.name("Trigger Test");
    }
    TriggerCondition triggerCondition = trigger.getCondition();
    if (triggerCondition instanceof ArtifactTriggerCondition) {
      ArtifactTriggerCondition artifactCondition = (ArtifactTriggerCondition) triggerCondition;
      final ArtifactTriggerConditionBuilder artifactConditionBuilder = ArtifactTriggerCondition.builder();
      if (artifactCondition.getArtifactStreamId() == null) {
        artifactConditionBuilder.artifactStreamId(ARTIFACT_STREAM_ID);
      } else {
        artifactConditionBuilder.artifactStreamId(artifactCondition.getArtifactStreamId());
      }
      triggerBuilder.condition(artifactConditionBuilder.build());
    }
    triggerBuilder.createdBy(trigger.getCreatedBy());
    return triggerBuilder.build();
  }
}
