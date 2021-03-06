package io.harness.pms.sdk.core.adviser.abort;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.WithFailureTypes;

import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@TypeAlias("onAbortAdviserParameters")
public class OnAbortAdviserParameters implements WithFailureTypes {
  @Builder.Default Set<FailureType> applicableFailureTypes = EnumSet.allOf(FailureType.class);
}
