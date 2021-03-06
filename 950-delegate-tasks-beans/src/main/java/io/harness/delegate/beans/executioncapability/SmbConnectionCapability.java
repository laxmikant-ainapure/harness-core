package io.harness.delegate.beans.executioncapability;

import java.time.Duration;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class SmbConnectionCapability implements ExecutionCapability {
  @NonNull String smbUrl;
  @Builder.Default CapabilityType capabilityType = CapabilityType.SMB;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return smbUrl;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }
}
