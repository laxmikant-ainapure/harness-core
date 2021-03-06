package io.harness.pms.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class OrchestrationFieldType {
  @NonNull String type;
}
