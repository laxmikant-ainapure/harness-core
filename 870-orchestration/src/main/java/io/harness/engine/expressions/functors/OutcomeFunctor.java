package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class OutcomeFunctor extends LateBindingMap {
  transient PmsOutcomeService pmsOutcomeService;
  transient Ambiance ambiance;

  @Override
  public synchronized Object get(Object key) {
    String resolveJson = pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject((String) key));
    return resolveJson == null ? null : RecastOrchestrationUtils.toDocumentFromJson(resolveJson);
  }
}
