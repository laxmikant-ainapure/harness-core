package software.wings.sm.states;

import static java.util.stream.Collectors.toList;

import software.wings.api.PhaseElement;
import software.wings.api.PhaseExecutionData;
import software.wings.api.SelectNodeStepExecutionSummary;
import software.wings.beans.ServiceInstance;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.PhaseExecutionSummary;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateType;
import software.wings.sm.StepExecutionSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by rishi on 7/16/17.
 */
public class CanaryUtils {
  static List<ServiceInstance> getHostExclusionList(ExecutionContext context, PhaseElement phaseElement) {
    List<ServiceInstance> hostExclusionList = new ArrayList<>();

    ExecutionContextImpl impl = (ExecutionContextImpl) context;
    List<StateExecutionData> previousPhaseExecutionData =
        impl.getStateExecutionInstance()
            .getStateExecutionMap()
            .values()
            .stream()
            .filter(stateExecutionData
                -> stateExecutionData.getStateType().equals(StateType.PHASE.name())
                    && !stateExecutionData.getStateName().equals(phaseElement.getName()))
            .collect(toList());
    if (previousPhaseExecutionData == null || previousPhaseExecutionData.isEmpty()) {
      return hostExclusionList;
    }

    for (StateExecutionData stateExecutionData : previousPhaseExecutionData) {
      PhaseExecutionData phaseExecutionData = (PhaseExecutionData) stateExecutionData;
      if (!phaseExecutionData.getInfraMappingId().equals(phaseElement.getInfraMappingId())) {
        continue;
      }
      PhaseExecutionSummary phaseExecutionSummary = phaseExecutionData.getPhaseExecutionSummary();
      if (phaseExecutionSummary == null || phaseExecutionSummary.getPhaseStepExecutionSummaryMap() == null) {
        continue;
      }
      for (PhaseStepExecutionSummary phaseStepExecutionSummary :
          phaseExecutionSummary.getPhaseStepExecutionSummaryMap().values()) {
        if (phaseStepExecutionSummary == null || phaseStepExecutionSummary.getStepExecutionSummaryList() == null
            || phaseStepExecutionSummary.getStepExecutionSummaryList().isEmpty()) {
          continue;
        }
        for (StepExecutionSummary stepExecutionSummary : phaseStepExecutionSummary.getStepExecutionSummaryList()) {
          if (stepExecutionSummary instanceof SelectNodeStepExecutionSummary) {
            SelectNodeStepExecutionSummary selectNodeStepExecutionSummary =
                (SelectNodeStepExecutionSummary) stepExecutionSummary;
            if (selectNodeStepExecutionSummary.isExcludeSelectedHostsFromFuturePhases()) {
              hostExclusionList.addAll(
                  ((SelectNodeStepExecutionSummary) stepExecutionSummary).getServiceInstanceList());
            }
          }
        }
      }
    }

    return hostExclusionList;
  }
}
