package io.harness.interrupts;

import java.util.EnumSet;

/**
 * Describes different possible events for state.
 *
 * @author Rishi
 */
public enum ExecutionInterruptType {
  /**
   * Abort state event.
   */
  ABORT("Abort execution of the current node"),
  /**
   * Abort all state event.
   */
  ABORT_ALL("Abort execution of all nodes for the current workflow"),
  /**
   * Pause state event.
   */
  PAUSE("Pause execution of the current node"),
  /**
   * Pause state event as state needs more inputs from User.
   */
  PAUSE_FOR_INPUTS("Pause execution of the current node"),
  /**
   * Pause all state event.
   */
  PAUSE_ALL("Pause execution of all nodes for the current workflow"),
  /**
   * Resume state event.
   */
  RESUME("Resume execution of the paused node"),
  /**
   * Resume all state event.
   */
  RESUME_ALL("Resume execution of all paused nodes in the current workflow"),
  /**
   * Retry state event.
   */
  RETRY("Retry the node execution"),
  /**
   * Ignore state event.
   */
  IGNORE("Ignore error and go to next"),
  /**
   * Waiting for Manual Intervention.
   */
  WAITING_FOR_MANUAL_INTERVENTION("Waiting for manual intervention on the current node failure"),
  /**
   * Mark as failed.
   */
  MARK_FAILED("Mark the node as failed"),
  /**
   * Mark as success.
   */

  MARK_SUCCESS("Mark the node as success"),

  ROLLBACK("Rollback"),

  NEXT_STEP("Next Step"),

  END_EXECUTION("End Execution"),

  ROLLBACK_DONE("Rollback Done"),

  MARK_EXPIRED("Mark the node as expired"),

  CONTINUE_WITH_DEFAULTS("Run the same execution Instance with default values"),

  CONTINUE_PIPELINE_STAGE("Run the env state with runtime values");

  private String description;

  ExecutionInterruptType(String description) {
    this.description = description;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  // Not considering ABORT_ALL as its handled straight away
  private static final EnumSet<ExecutionInterruptType> PLAN_LEVEL_INTERRUPTS =
      EnumSet.of(PAUSE_ALL, RESUME_ALL, ROLLBACK);

  public static EnumSet<ExecutionInterruptType> planLevelInterrupts() {
    return PLAN_LEVEL_INTERRUPTS;
  }
}
