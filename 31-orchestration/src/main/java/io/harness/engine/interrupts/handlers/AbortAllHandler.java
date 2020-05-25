package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.CollectionUtils.isPresent;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ABORT_ALL_ALREADY;
import static io.harness.exception.WingsException.USER;
import static io.harness.execution.status.NodeExecutionStatus.ABORTED;
import static io.harness.execution.status.NodeExecutionStatus.DISCONTINUING;
import static io.harness.interrupts.ExecutionInterruptType.ABORT_ALL;
import static io.harness.persistence.HQuery.excludeAuthority;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.harness.engine.ExecutionEngine;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.services.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.status.NodeExecutionStatus;
import io.harness.facilitator.modes.Abortable;
import io.harness.facilitator.modes.ExecutableResponse;
import io.harness.facilitator.modes.task.AsyncTaskExecutableResponse;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptKeys;
import io.harness.interrupts.InterruptEffect;
import io.harness.persistence.HPersistence;
import io.harness.plan.ExecutionNode;
import io.harness.registries.state.StepRegistry;
import io.harness.state.Step;
import io.harness.tasks.TaskExecutor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Slf4j
public class AbortAllHandler implements InterruptHandler {
  @Inject @Named("enginePersistence") private HPersistence hPersistence;
  @Inject private InterruptService interruptService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private StepRegistry stepRegistry;
  @Inject private ExecutionEngine executionEngine;
  @Inject private Map<String, TaskExecutor> taskExecutorMap;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    String savedInterruptId = validateAndSave(interrupt);
    Interrupt savedInterrupt =
        hPersistence.createQuery(Interrupt.class).filter(InterruptKeys.uuid, savedInterruptId).get();
    return handleInterrupt(savedInterrupt);
  }

  private String validateAndSave(@Valid @NonNull Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId());
    if (isPresent(interrupts, presentInterrupt -> presentInterrupt.getType() == ABORT_ALL)) {
      throw new InvalidRequestException("Execution already has ABORT_ALL interrupt", ABORT_ALL_ALREADY, USER);
    }
    if (isEmpty(interrupts)) {
      return hPersistence.save(interrupt);
    }

    List<Interrupt> seizedInterrupts = new ArrayList<>();
    interrupts.forEach(savedInterrupt -> seizedInterrupts.add(interruptService.seize(savedInterrupt.getUuid())));
    if (seizedInterrupts.stream().allMatch(Interrupt::isSeized)) {
      return hPersistence.save(interrupt);
    }
    throw new InvalidRequestException("Cannot Validate and save Interrupt", USER);
  }

  @Override
  public Interrupt handleInterrupt(@NonNull @Valid Interrupt interrupt) {
    if (!markAbortingState(interrupt, NodeExecutionStatus.abortableStatuses())) {
      return interrupt;
    }

    List<NodeExecution> discontinuingNodeExecutions =
        nodeExecutionService.fetchNodeExecutionsByStatus(interrupt.getPlanExecutionId(), DISCONTINUING);

    if (isEmpty(discontinuingNodeExecutions)) {
      logger.warn("ABORT_ALL Interrupt being ignored as no running instance found for planExecutionId: {}",
          interrupt.getUuid());
      return interruptService.seize(interrupt.getUuid());
    }

    for (NodeExecution discontinuingNodeExecution : discontinuingNodeExecutions) {
      discontinueMarkedInstance(discontinuingNodeExecution);
    }
    return interruptService.seize(interrupt.getUuid());
  }

  private void discontinueMarkedInstance(NodeExecution nodeExecution) {
    boolean updated = false;
    try {
      ExecutionNode node = nodeExecution.getNode();
      Step currentState = Preconditions.checkNotNull(stepRegistry.obtain(node.getStepType()));
      ExecutableResponse executableResponse = nodeExecution.getExecutableResponse();
      if (executableResponse instanceof AsyncTaskExecutableResponse) {
        AsyncTaskExecutableResponse asyncTaskExecutableResponse = (AsyncTaskExecutableResponse) executableResponse;
        TaskExecutor executor = taskExecutorMap.get(asyncTaskExecutableResponse.getTaskIdentifier());
        executor.abortTask(nodeExecution.getAmbiance(), asyncTaskExecutableResponse.getTaskId());
      }
      if (currentState instanceof Abortable) {
        ((Abortable) currentState)
            .handleAbort(nodeExecution.getAmbiance(), node.getStepParameters(), executableResponse);
      }

      UpdateOperations<NodeExecution> ops = hPersistence.createUpdateOperations(NodeExecution.class);
      ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
      ops.set(NodeExecutionKeys.status, ABORTED);

      Query<NodeExecution> query = hPersistence.createQuery(NodeExecution.class, excludeAuthority)
                                       .filter(NodeExecutionKeys.uuid, nodeExecution.getUuid());
      NodeExecution updatedNodeExecution = hPersistence.findAndModify(query, ops, HPersistence.returnNewOptions);
      if (updatedNodeExecution != null) {
        updated = true;
        executionEngine.endTransition(updatedNodeExecution);
      }
    } catch (Exception e) {
      logger.error("Error in discontinuing", e);
    }
    if (!updated) {
      throw new InvalidRequestException(
          "Abort failed for execution Plan :" + nodeExecution.getAmbiance().getPlanExecutionId());
    }
  }

  private boolean markAbortingState(@NotNull Interrupt interrupt, EnumSet<NodeExecutionStatus> statuses) {
    // Get all that are eligible for discontinuing
    List<NodeExecution> allNodeExecutions =
        nodeExecutionService.fetchNodeExecutionsByStatuses(interrupt.getPlanExecutionId(), statuses);
    if (isEmpty(allNodeExecutions)) {
      logger.warn(
          "No Node Executions could be marked as DISCONTINUING - planExecutionId: {}", interrupt.getPlanExecutionId());
      return false;
    }
    List<String> leafInstanceIds = getAllLeafInstanceIds(interrupt, allNodeExecutions, statuses);
    UpdateOperations<NodeExecution> ops = hPersistence.createUpdateOperations(NodeExecution.class);
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    ops.addToSet(NodeExecutionKeys.interruptHistories,
        InterruptEffect.builder().interruptId(interrupt.getUuid()).tookEffectAt(new Date()).build());
    Query<NodeExecution> query = hPersistence.createQuery(NodeExecution.class, excludeAuthority)
                                     .filter(NodeExecutionKeys.planExecutionId, interrupt.getPlanExecutionId())
                                     .field(NodeExecutionKeys.uuid)
                                     .in(leafInstanceIds);
    // Set the status to DISCONTINUING
    UpdateResults updateResult = hPersistence.update(query, ops);
    if (updateResult == null || updateResult.getWriteResult() == null || updateResult.getWriteResult().getN() == 0) {
      logger.warn(
          "No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", interrupt.getPlanExecutionId());
      return false;
    }
    return true;
  }

  private List<String> getAllLeafInstanceIds(
      Interrupt interrupt, List<NodeExecution> allNodeExecutions, EnumSet<NodeExecutionStatus> statuses) {
    List<String> allInstanceIds = allNodeExecutions.stream().map(NodeExecution::getUuid).collect(toList());
    // Get Parent Ids
    List<String> parentIds = allNodeExecutions.stream()
                                 .filter(NodeExecution::isChildSpawningMode)
                                 .map(NodeExecution::getUuid)
                                 .collect(toList());
    if (isEmpty(parentIds)) {
      return allInstanceIds;
    }

    List<NodeExecution> children = hPersistence.createQuery(NodeExecution.class, excludeAuthority)
                                       .filter(NodeExecutionKeys.planExecutionId, interrupt.getPlanExecutionId())
                                       .field(NodeExecutionKeys.parentId)
                                       .in(parentIds)
                                       .field(NodeExecutionKeys.status)
                                       .in(statuses)
                                       .asList();

    // get distinct parent Ids
    List<String> parentIdsHavingChildren =
        children.stream().map(NodeExecution::getParentId).distinct().collect(toList());

    // parent with no children
    allInstanceIds.removeAll(parentIdsHavingChildren);

    // Mark aborting
    return allInstanceIds;
  }
}
