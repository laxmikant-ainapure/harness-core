package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.ExecutionInterruptType.PAUSE_ALL;
import static io.harness.interrupts.ExecutionInterruptType.RESUME_ALL;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationService;
import io.harness.engine.PlanRepo;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.InterruptTestHelper;
import io.harness.engine.interrupts.steps.SimpleAsyncStep;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.sdk.core.execution.NodeExecutionEventListener;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.rule.Owner;
import io.harness.testlib.RealMongo;
import io.harness.waiter.OrchestrationNotifyEventListener;

import software.wings.WingsBaseTest;
import software.wings.rules.Listeners;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Listeners({OrchestrationNotifyEventListener.class, NodeExecutionEventListener.class})
public class PauseAndResumeHandlerTest extends WingsBaseTest {
  @Inject private Injector injector;
  @Inject private OrchestrationService orchestrationService;
  @Inject private StepRegistry stepRegistry;
  @Inject private InterruptTestHelper interruptTestHelper;
  @Inject private InterruptService interruptService;
  @Inject private PlanRepo planRepo;

  private static final TriggeredBy embeddedUser =
      TriggeredBy.newBuilder().putExtraInfo("email", PRASHANT).setIdentifier(PRASHANT).setUuid(generateUuid()).build();
  private static final ExecutionTriggerInfo triggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(embeddedUser).build();

  private static final ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                                        .setExecutionUuid(generateUuid())
                                                        .setRunSequence(0)
                                                        .setTriggerInfo(triggerInfo)
                                                        .build();

  @Before
  public void setUp() {
    stepRegistry.register(SimpleAsyncStep.STEP_TYPE, injector.getInstance(SimpleAsyncStep.class));
  }

  @Test
  @RealMongo
  @Owner(developers = PRASHANT, intermittent = true)
  @Category(UnitTests.class)
  public void shouldTestRegisterAndHandleInterrupt() {
    // Execute Plan And wait it to be in RUNNING status
    PlanExecution execution = orchestrationService.startExecution(planRepo.planWithBigWait(), metadata);
    interruptTestHelper.waitForPlanStatus(execution.getUuid(), RUNNING);

    // Issue Pause Interrupt
    Interrupt handledPauseInterrupt = orchestrationService.registerInterrupt(
        InterruptPackage.builder().planExecutionId(execution.getUuid()).interruptType(PAUSE_ALL).build());
    assertThat(handledPauseInterrupt).isNotNull();
    assertThat(handledPauseInterrupt.getState()).isEqualTo(PROCESSING);

    // Wait for Plan To be in PAUSED status
    interruptTestHelper.waitForPlanCompletion(execution.getUuid());

    PlanExecution pausedPlanExecution = interruptTestHelper.fetchPlanExecutionStatus(execution.getUuid());
    assertThat(pausedPlanExecution).isNotNull();
    assertThat(pausedPlanExecution.getStatus()).isEqualTo(PAUSED);

    // Issue Resume Interrupt
    Interrupt handledResumeInterrupt = orchestrationService.registerInterrupt(
        InterruptPackage.builder().planExecutionId(execution.getUuid()).interruptType(RESUME_ALL).build());
    assertThat(handledResumeInterrupt).isNotNull();

    // Wait for Plan To be complete
    interruptTestHelper.waitForPlanCompletion(execution.getUuid());

    List<Interrupt> allInterrupts = interruptService.fetchAllInterrupts(execution.getUuid());

    assertThat(allInterrupts).isNotEmpty();
    assertThat(allInterrupts).hasSize(2);
    assertThat(allInterrupts.stream().map(Interrupt::getState)).containsExactly(PROCESSED_SUCCESSFULLY, PROCESSING);

    PlanExecution resumedExecution = interruptTestHelper.fetchPlanExecutionStatus(execution.getUuid());
    assertThat(resumedExecution).isNotNull();
    assertThat(resumedExecution.getStatus()).isEqualTo(SUCCEEDED);
  }
}
