package io.harness.yaml.core.failurestrategy.retry;

import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RetryFailureActionConfig implements FailureStrategyActionConfig {
  NGFailureActionType type = NGFailureActionType.RETRY;
  @JsonProperty("spec") RetryFailureSpecConfig specConfig;
}
