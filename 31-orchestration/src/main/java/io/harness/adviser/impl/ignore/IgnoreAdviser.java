package io.harness.adviser.impl.ignore;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static java.util.Collections.disjoint;

import com.google.common.base.Preconditions;

import io.harness.adviser.Advise;
import io.harness.adviser.Adviser;
import io.harness.adviser.AdviserType;
import io.harness.adviser.AdvisingEvent;
import io.harness.adviser.impl.success.NextStepAdvise;
import io.harness.annotations.Produces;
import io.harness.annotations.Redesign;
import io.harness.annotations.dev.OwnedBy;
import io.harness.state.io.StateResponse;

@OwnedBy(CDC)
@Redesign
@Produces(Adviser.class)
public class IgnoreAdviser implements Adviser {
  @Override
  public Advise onAdviseEvent(AdvisingEvent advisingEvent) {
    IgnoreAdviserParameters parameters =
        (IgnoreAdviserParameters) Preconditions.checkNotNull(advisingEvent.getAdviserParameters());
    StateResponse stateResponse = advisingEvent.getStateResponse();
    StateResponse.FailureInfo failureInfo = stateResponse.getFailureInfo();
    if (failureInfo == null) {
      return null;
    }
    if (!disjoint(parameters.getApplicableFailureTypes(), failureInfo.getFailureTypes())) {
      return NextStepAdvise.builder().nextNodeId(parameters.getNextNodeId()).build();
    }
    return null;
  }

  @Override
  public AdviserType getType() {
    return AdviserType.builder().type(AdviserType.IGNORE).build();
  }
}
