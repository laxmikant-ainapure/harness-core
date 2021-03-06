package io.harness.pms.plan.execution.beans.dto;

import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.bson.Document;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PipelineExecutionSummary")
public class PipelineExecutionSummaryDTO {
  String pipelineIdentifier;
  String planExecutionId;
  String name;

  ExecutionStatus status;

  List<NGTag> tags;

  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionErrorInfo executionErrorInfo;

  Map<String, Document> moduleInfo;
  Map<String, GraphLayoutNodeDTO> layoutNodeMap;
  List<String> modules;
  String startingNodeId;

  Long startTs;
  Long endTs;
  Long createdAt;

  int runSequence;
  long successfulStagesCount;
  long runningStagesCount;
  long failedStagesCount;
  long totalStagesCount;
}
