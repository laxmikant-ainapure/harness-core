package io.harness.impl;

import static io.harness.execution.status.Status.RUNNING;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Inject;

import io.harness.beans.CIPipeline;
import io.harness.category.element.UnitTests;
import io.harness.engine.EngineService;
import io.harness.execution.PlanExecution;
import io.harness.executionplan.CIExecutionPlanCreatorRegistrar;
import io.harness.executionplan.CIExecutionPlanTestHelper;
import io.harness.executionplan.CIExecutionTest;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CIPipelineExecutionServiceImplTest extends CIExecutionTest {
  @Mock private EngineService engineService;
  @Inject CIPipelineExecutionService ciPipelineExecutionService;
  @Inject CIExecutionPlanTestHelper executionPlanTestHelper;
  @Inject private CIExecutionPlanCreatorRegistrar ciExecutionPlanCreatorRegistrar;

  @Before
  public void setUp() {
    ciExecutionPlanCreatorRegistrar.register();
    on(ciPipelineExecutionService).set("engineService", engineService);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void executePipeline() {
    CIPipeline ciPipeline = executionPlanTestHelper.getCIPipeline();

    when(engineService.startExecution(any(), any())).thenReturn(PlanExecution.builder().status(RUNNING).build());

    PlanExecution planExecution = ciPipelineExecutionService.executePipeline(ciPipeline);
    assertThat(planExecution).isNotNull();
    verify(engineService, times(1)).startExecution(any(), any());
  }
}