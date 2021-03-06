package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.interrupts.Interrupt;

@OwnedBy(CDC)
public interface InterruptHandler {
  Interrupt registerInterrupt(Interrupt interrupt);

  Interrupt handleInterrupt(Interrupt interrupt);

  Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId);
}
