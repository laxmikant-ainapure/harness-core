package io.harness.pms.execution.utils;

import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.ASYNC_WAITING;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.EXPIRED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.PAUSING;
import static io.harness.pms.contracts.execution.Status.QUEUED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.execution.Status.SUSPENDED;
import static io.harness.pms.contracts.execution.Status.TASK_WAITING;
import static io.harness.pms.contracts.execution.Status.TIMED_WAITING;

import io.harness.pms.contracts.execution.Status;

import java.util.EnumSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class StatusUtils {
  // Status Groups
  private final EnumSet<Status> FINALIZABLE_STATUSES = EnumSet.of(QUEUED, RUNNING, PAUSED, ASYNC_WAITING,
      INTERVENTION_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING, PAUSING);

  private final EnumSet<Status> POSITIVE_STATUSES = EnumSet.of(SUCCEEDED, SKIPPED, SUSPENDED);

  private final EnumSet<Status> BROKE_STATUSES = EnumSet.of(FAILED, ERRORED);

  private final EnumSet<Status> RESUMABLE_STATUSES =
      EnumSet.of(QUEUED, RUNNING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, INTERVENTION_WAITING);

  private final EnumSet<Status> FLOWING_STATUSES =
      EnumSet.of(RUNNING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING);

  private final EnumSet<Status> FINAL_STATUSES =
      EnumSet.of(SKIPPED, ABORTED, ERRORED, FAILED, EXPIRED, SUSPENDED, SUCCEEDED);

  private final EnumSet<Status> RETRYABLE_STATUSES = EnumSet.of(INTERVENTION_WAITING, FAILED, ERRORED, EXPIRED);

  public EnumSet<Status> finalizableStatuses() {
    return FINALIZABLE_STATUSES;
  }

  public EnumSet<Status> positiveStatuses() {
    return POSITIVE_STATUSES;
  }

  public EnumSet<Status> brokeStatuses() {
    return BROKE_STATUSES;
  }

  public EnumSet<Status> resumableStatuses() {
    return RESUMABLE_STATUSES;
  }

  public EnumSet<Status> flowingStatuses() {
    return FLOWING_STATUSES;
  }

  public EnumSet<Status> retryableStatuses() {
    return RETRYABLE_STATUSES;
  }

  public EnumSet<Status> finalStatuses() {
    return FINAL_STATUSES;
  }

  public EnumSet<Status> nodeAllowedStartSet(Status status) {
    switch (status) {
      case RUNNING:
        return EnumSet.of(QUEUED, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, INTERVENTION_WAITING, PAUSED);
      case INTERVENTION_WAITING:
        return BROKE_STATUSES;
      case TIMED_WAITING:
      case ASYNC_WAITING:
      case TASK_WAITING:
      case PAUSING:
      case SKIPPED:
        return EnumSet.of(QUEUED, RUNNING);
      case PAUSED:
        return EnumSet.of(QUEUED, RUNNING, PAUSING);
      case DISCONTINUING:
        return EnumSet.of(
            QUEUED, RUNNING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, INTERVENTION_WAITING, PAUSED, PAUSING);
      case QUEUED:
        return EnumSet.of(PAUSED, PAUSING);
      case ABORTED:
      case ERRORED:
      case SUSPENDED:
      case FAILED:
      case EXPIRED:
        return FINALIZABLE_STATUSES;
      case SUCCEEDED:
        return EnumSet.allOf(Status.class);
      default:
        throw new IllegalStateException("Unexpected value: " + status);
    }
  }

  public EnumSet<Status> planAllowedStartSet(Status status) {
    if (status == INTERVENTION_WAITING) {
      return EnumSet.of(RUNNING);
    }
    return nodeAllowedStartSet(status);
  }

  public boolean isFinalStatus(Status status) {
    return FINAL_STATUSES.contains(status);
  }
}
