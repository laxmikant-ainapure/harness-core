package io.harness.redesign.states.email;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.github.reinert.jjschema.Attributes;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class EmailStepParameters implements StepParameters {
  @Attributes(required = true, title = "To") String toAddress;
  @Attributes(title = "CC") String ccAddress;
  @Attributes(required = true, title = "Subject") String subject;
  @Attributes(title = "Body") String body;
  @Attributes(title = "Ignore delivery failure?") boolean ignoreDeliveryFailure;
}
