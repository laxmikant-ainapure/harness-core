package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortHelperTest extends OrchestrationTestBase {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private InterruptEventPublisher interruptEventPublisher;
  @Inject private MongoTemplate mongoTemplate;
  @Inject @InjectMocks private AbortHelper abortHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueMarkedInstances() {
    String notifyId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.PROCESSING)
                              .build();
    mongoTemplate.save(interrupt);

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
            .status(DISCONTINUING)
            .mode(ExecutionMode.ASYNC)
            .node(PlanNodeProto.newBuilder()
                      .setUuid(generateUuid())
                      .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                      .build())
            .startTs(System.currentTimeMillis())
            .build();

    when(interruptEventPublisher.publishEvent(nodeExecutionId, interrupt, InterruptType.ABORT)).thenReturn(notifyId);
    abortHelper.discontinueMarkedInstance(nodeExecution, interrupt);

    ArgumentCaptor<String> pName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<NotifyCallback> callbackCaptor = ArgumentCaptor.forClass(NotifyCallback.class);
    ArgumentCaptor<String> correlationIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(waitNotifyEngine, times(1))
        .waitForAllOn(pName.capture(), callbackCaptor.capture(), correlationIdCaptor.capture());

    assertThat(callbackCaptor.getValue()).isInstanceOf(AbortInterruptCallback.class);
    assertThat(correlationIdCaptor.getValue()).isEqualTo(notifyId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueMarkedInstancesForSync() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.PROCESSING)
                              .build();
    mongoTemplate.save(interrupt);

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
            .status(ABORTED)
            .mode(ExecutionMode.SYNC)
            .node(PlanNodeProto.newBuilder()
                      .setUuid(generateUuid())
                      .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                      .build())
            .startTs(System.currentTimeMillis())
            .build();

    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(ABORTED), any(), any()))
        .thenReturn(nodeExecution);
    abortHelper.discontinueMarkedInstance(nodeExecution, interrupt);

    verify(interruptEventPublisher, times(0)).publishEvent(any(), any(), any());
    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any());

    verify(engine, times(1)).endTransition(eq(nodeExecution));
  }
}