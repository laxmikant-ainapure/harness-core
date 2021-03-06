package io.harness.redesign.states.wait;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class WaitStepParameters implements StepParameters {
  long waitDurationSeconds;
}
