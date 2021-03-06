package io.harness.steps.dummy;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.steps.OrchestrationStepTypes;
import io.harness.tasks.ResponseData;

import java.util.Map;

@OwnedBy(CDC)
public class DummySectionStep implements ChildExecutable<DummySectionStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder().setType(OrchestrationStepTypes.DUMMY_SECTION).build();

  @Override
  public Class<DummySectionStepParameters> getStepParametersClass() {
    return DummySectionStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, DummySectionStepParameters dummySectionStepParameters, StepInputPackage inputPackage) {
    return ChildExecutableResponse.newBuilder().setChildNodeId(dummySectionStepParameters.getChildNodeId()).build();
  }

  @Override
  public StepResponse handleChildResponse(Ambiance ambiance, DummySectionStepParameters dummySectionStepParameters,
      Map<String, ResponseData> responseDataMap) {
    StepResponseBuilder responseBuilder = StepResponse.builder().stepOutcome(
        StepResponse.StepOutcome.builder()
            .name("outcomeData")
            .outcome(DummySectionOutcome.builder().map(dummySectionStepParameters.getData()).build())
            .build());
    StepResponseNotifyData stepResponseNotifyData = (StepResponseNotifyData) responseDataMap.values().iterator().next();
    responseBuilder.status(stepResponseNotifyData.getStatus());
    return responseBuilder.build();
  }
}
