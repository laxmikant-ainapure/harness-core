package io.harness.execution.export.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.time.ZonedDateTime;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@JsonInclude(Include.NON_NULL)
public class ExportExecutionsRequestSummary {
  String requestId;
  Status status;
  long totalExecutions;
  ZonedDateTime triggeredAt;

  // For status = QUEUED or status = READY.
  String statusLink;
  String downloadLink;
  ZonedDateTime expiresAt;

  // For status = FAILED.
  String errorMessage;
}
