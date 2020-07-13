package io.harness.perpetualtask.k8s.watch;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.kubernetes.client.custom.Quantity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

/**
 * Standardize units of cpu & memory resources.
 */
@UtilityClass
public class K8sResourceStandardizer {
  private static final BigDecimal SCALE_TO_NANO = BigDecimal.valueOf(1_000_000_000);

  // Standardize cpu as nanocores (as usage is given in nanocore precision)
  public long getCpuNano(String cpu) {
    return getCpuCores(cpu).multiply(SCALE_TO_NANO).longValue();
  }

  public BigDecimal getCpuCores(String cpu) {
    if (isEmpty(cpu)) {
      return BigDecimal.ZERO;
    }
    return Quantity.fromString(cpu).getNumber();
  }

  // Standardize memory as bytes
  public long getMemoryByte(String mem) {
    if (isEmpty(mem)) {
      return 0L;
    }
    return Quantity.fromString(mem).getNumber().longValue();
  }
}
