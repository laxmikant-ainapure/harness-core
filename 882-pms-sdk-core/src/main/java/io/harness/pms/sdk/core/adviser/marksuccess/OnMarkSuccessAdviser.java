package io.harness.pms.sdk.core.adviser.marksuccess;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise.Builder;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import javax.validation.constraints.NotNull;

public class OnMarkSuccessAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.MARK_SUCCESS.name()).build();

  @Inject KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnMarkSuccessAdviserParameters parameters = extractParameters(advisingEvent);
    Builder builder = MarkSuccessAdvise.newBuilder();
    if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
      builder.setNextNodeId(parameters.getNextNodeId());
    }
    return AdviserResponse.newBuilder().setMarkSuccessAdvise(builder.build()).setType(AdviseType.MARK_SUCCESS).build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnMarkSuccessAdviserParameters adviserParameters = extractParameters(advisingEvent);
    FailureInfo failureInfo = advisingEvent.getNodeExecution().getFailureInfo();
    if (failureInfo != null && !isEmpty(failureInfo.getFailureTypesValueList())) {
      return !Collections.disjoint(adviserParameters.getApplicableFailureTypes(), failureInfo.getFailureTypesList());
    }
    return true;
  }

  @NotNull
  private OnMarkSuccessAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnMarkSuccessAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
