package io.harness.pms.plan.execution.handlers;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.SyncOrchestrationEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Singleton
public class ExecutionInfoUpdateEventHandler implements SyncOrchestrationEventHandler {
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PlanExecutionService planExecutionService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    Optional<PipelineEntity> pipelineEntity = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);
    if (!pipelineEntity.isPresent()) {
      return;
    }
    Status status = planExecutionService.get(ambiance.getPlanExecutionId()).getStatus();
    ExecutionSummaryInfo executionSummaryInfo = pipelineEntity.get().getExecutionSummaryInfo();
    executionSummaryInfo.setLastExecutionStatus(ExecutionStatus.getExecutionStatus(status));
    if (StatusUtils.brokeStatuses().contains(status)) {
      Map<String, Integer> errors = executionSummaryInfo.getNumOfErrors();
      Date date = new Date();
      SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
      String strDate = formatter.format(date);
      if (errors.containsKey(strDate)) {
        errors.put(strDate, errors.get(strDate) + 1);
      } else {
        errors.put(strDate, 1);
      }
    }
    pmsPipelineService.saveExecutionInfo(accountId, orgId, projectId, pipelineId, executionSummaryInfo);
  }
}
