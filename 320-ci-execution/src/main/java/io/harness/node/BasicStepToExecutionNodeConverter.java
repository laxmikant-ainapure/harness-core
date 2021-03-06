package io.harness.node;

import io.harness.beans.steps.CIStepInfo;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.facilitator.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import graph.StepInfoGraph;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a step to execution Node by adding SYNC facilitators and ON_SUCCESS advisers
 */

@Singleton
public class BasicStepToExecutionNodeConverter implements StepToExecutionNodeConverter<CIStepInfo> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public PlanNode convertStep(CIStepInfo step, List<String> nextStepUuids) {
    return PlanNode.builder()
        .name(step.getIdentifier())
        .uuid(step.getIdentifier())
        .stepType(step.getNonYamlInfo().getStepType())
        .identifier(step.getIdentifier())
        .stepParameters(step)
        .facilitatorObtainment(getFacilitatorsFromMetaData())
        .adviserObtainments(getAdviserObtainmentFromMetaData(nextStepUuids))
        .build();
  }

  private FacilitatorObtainment getFacilitatorsFromMetaData() {
    return FacilitatorObtainment.newBuilder()
        .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
        .build();
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(List<String> nextStepUuids) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();

    // TODO Handle parallel execution
    if (!nextStepUuids.isEmpty() && !StepInfoGraph.isNILStepUuId(nextStepUuids.get(0))) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  OnSuccessAdviserParameters.builder().nextNodeId(nextStepUuids.get(0)).build())))
              .build());
    }

    return adviserObtainments;
  }
}
