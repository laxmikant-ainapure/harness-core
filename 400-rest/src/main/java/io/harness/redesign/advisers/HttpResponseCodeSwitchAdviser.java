package io.harness.redesign.advisers;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.serializer.KryoSerializer;

import software.wings.api.HttpStateExecutionData;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(CDC)
public class HttpResponseCodeSwitchAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE = AdviserType.newBuilder().setType("HTTP_RESPONSE_CODE_SWITCH").build();
  @Inject private OutcomeService outcomeService;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    HttpResponseCodeSwitchAdviserParameters parameters =
        (HttpResponseCodeSwitchAdviserParameters) Preconditions.checkNotNull(
            kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
    // This will be changed to obtain via output type
    Outcome outcome = outcomeService.fetchOutcome(advisingEvent.getNodeExecution()
                                                      .getOutcomeRefsList()
                                                      .stream()
                                                      .filter(oRef -> oRef.getName().equals("http"))
                                                      .findFirst()
                                                      .map(StepOutcomeRef::getInstanceId)
                                                      .orElse(null));

    HttpStateExecutionData httpStateExecutionData = (HttpStateExecutionData) Preconditions.checkNotNull(outcome);

    Map<Integer, String> responseCodeNodeIdMap = parameters.getResponseCodeNodeIdMappings();
    if (responseCodeNodeIdMap.containsKey(httpStateExecutionData.getHttpResponseCode())) {
      return AdviserResponse.newBuilder()
          .setNextStepAdvise(NextStepAdvise.newBuilder()
                                 .setNextNodeId(responseCodeNodeIdMap.get(httpStateExecutionData.getHttpResponseCode()))
                                 .build())
          .setType(AdviseType.NEXT_STEP)
          .build();
    } else {
      throw new InvalidRequestException(
          "Not able to process Response For response code: " + httpStateExecutionData.getHttpResponseCode());
    }
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    return StatusUtils.positiveStatuses().contains(advisingEvent.getToStatus());
  }
}
