package io.harness.pms.sdk.core.adviser.retry;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.execution.utils.StatusUtils.retryableStatuses;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.advisers.NextStepAdvise.Builder;
import io.harness.pms.contracts.advisers.RetryAdvise;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;

@OwnedBy(CDC)
public class RetryAdviser implements Adviser {
  @Inject private KryoSerializer kryoSerializer;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.RETRY.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    RetryAdviserParameters parameters = extractParameters(advisingEvent);
    NodeExecutionProto nodeExecution = advisingEvent.getNodeExecution();

    if (NodeExecutionUtils.retryCount(nodeExecution) < parameters.getRetryCount()) {
      int waitInterval =
          calculateWaitInterval(parameters.getWaitIntervalList(), NodeExecutionUtils.retryCount(nodeExecution));
      return AdviserResponse.newBuilder()
          .setType(AdviseType.RETRY)
          .setRetryAdvise(RetryAdvise.newBuilder()
                              .setRetryNodeExecutionId(nodeExecution.getUuid())
                              .setWaitInterval(waitInterval)
                              .build())
          .build();
    }
    return handlePostRetry(parameters);
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    boolean canAdvise = retryableStatuses().contains(advisingEvent.getToStatus());
    FailureInfo failureInfo = advisingEvent.getNodeExecution().getFailureInfo();
    io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters parameters = extractParameters(advisingEvent);
    if (failureInfo != null && !isEmpty(failureInfo.getFailureTypesList())) {
      return canAdvise
          && !Collections.disjoint(parameters.getApplicableFailureTypes(), failureInfo.getFailureTypesList());
    }
    return canAdvise;
  }

  private AdviserResponse handlePostRetry(io.harness.pms.sdk.core.adviser.retry.RetryAdviserParameters parameters) {
    AdviserResponse.Builder adviserResponseBuilder =
        AdviserResponse.newBuilder().setRepairActionCode(parameters.getRepairActionCodeAfterRetry());
    switch (parameters.getRepairActionCodeAfterRetry()) {
      case MANUAL_INTERVENTION:
        return adviserResponseBuilder
            .setInterventionWaitAdvise(
                InterventionWaitAdvise.newBuilder()
                    .setTimeout(Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).build())
                    .build())
            .setType(AdviseType.INTERVENTION_WAIT)
            .build();
      case END_EXECUTION:
        return adviserResponseBuilder.setEndPlanAdvise(EndPlanAdvise.newBuilder().build())
            .setType(AdviseType.END_PLAN)
            .build();
      case IGNORE:
      case ON_FAIL:
        Builder builder = NextStepAdvise.newBuilder();
        if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
          builder.setNextNodeId(parameters.getNextNodeId());
        }
        return adviserResponseBuilder.setNextStepAdvise(builder.build()).setType(AdviseType.NEXT_STEP).build();
      case MARK_AS_SUCCESS:
        MarkSuccessAdvise.Builder markSuccessBuilder = MarkSuccessAdvise.newBuilder();
        if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
          markSuccessBuilder.setNextNodeId(parameters.getNextNodeId());
        }
        return adviserResponseBuilder.setMarkSuccessAdvise(markSuccessBuilder.build())
            .setType(AdviseType.MARK_SUCCESS)
            .build();
      default:
        throw new IllegalStateException("Unexpected value: " + parameters.getRepairActionCodeAfterRetry());
    }
  }

  private int calculateWaitInterval(List<Integer> waitIntervalList, int retryCount) {
    if (isEmpty(waitIntervalList)) {
      return 0;
    }
    return waitIntervalList.size() <= retryCount ? waitIntervalList.get(waitIntervalList.size() - 1)
                                                 : waitIntervalList.get(retryCount);
  }

  @NotNull
  private RetryAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (RetryAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
