package io.harness.engine.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.steps.TestStepParameters;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.logging.impl.NoOpLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NodeExecutionValueTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private JexlEngine engine;
  private Ambiance ambiance;
  NodeExecution nodeExecution1;
  NodeExecution nodeExecution2;
  NodeExecution nodeExecution3;
  NodeExecution nodeExecution4;
  NodeExecution nodeExecution5;
  NodeExecution nodeExecution6;

  @Before
  public void setup() {
    engine = new JexlBuilder().logger(new NoOpLog()).create();
    ambiance = AmbianceTestUtils.buildAmbiance();

    nodeExecution1 = NodeExecution.builder()
                         .uuid(generateUuid())
                         .node(preparePlanNode(false, "a"))
                         .resolvedStepParameters(prepareStepParameters("ao"))
                         .build();
    nodeExecution2 = NodeExecution.builder()
                         .uuid(generateUuid())
                         .node(preparePlanNode(false, "b"))
                         .resolvedStepParameters(prepareStepParameters("bo"))
                         .build();
    nodeExecution3 = NodeExecution.builder()
                         .uuid(generateUuid())
                         .node(preparePlanNode(true, "c"))
                         .resolvedStepParameters(prepareStepParameters("co"))
                         .build();
    nodeExecution4 =
        NodeExecution.builder().uuid(generateUuid()).node(preparePlanNode(false, "d", "di1", "STAGE")).build();
    nodeExecution5 = NodeExecution.builder()
                         .uuid(generateUuid())
                         .node(preparePlanNode(false, "d", "di2"))
                         .resolvedStepParameters(prepareStepParameters("do2"))
                         .build();
    nodeExecution6 = NodeExecution.builder()
                         .uuid(generateUuid())
                         .node(preparePlanNode(false, "e"))
                         .resolvedStepParameters(prepareStepParameters("eo"))
                         .build();

    nodeExecution2.setParentId(nodeExecution1.getUuid());
    nodeExecution3.setParentId(nodeExecution1.getUuid());
    nodeExecution2.setNextId(nodeExecution1.getUuid());
    nodeExecution3.setPreviousId(nodeExecution2.getUuid());
    nodeExecution4.setParentId(nodeExecution3.getUuid());
    nodeExecution5.setParentId(nodeExecution3.getUuid());
    nodeExecution4.setNextId(nodeExecution5.getUuid());
    nodeExecution5.setPreviousId(nodeExecution4.getUuid());
    nodeExecution6.setParentId(nodeExecution4.getUuid());

    when(nodeExecutionService.get(nodeExecution1.getUuid())).thenReturn(nodeExecution1);
    when(nodeExecutionService.get(nodeExecution2.getUuid())).thenReturn(nodeExecution2);
    when(nodeExecutionService.get(nodeExecution3.getUuid())).thenReturn(nodeExecution3);
    when(nodeExecutionService.get(nodeExecution4.getUuid())).thenReturn(nodeExecution4);
    when(nodeExecutionService.get(nodeExecution5.getUuid())).thenReturn(nodeExecution5);
    when(nodeExecutionService.get(nodeExecution6.getUuid())).thenReturn(nodeExecution6);

    String planExecutionId = ambiance.getPlanExecutionId();
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, null))
        .thenReturn(Collections.singletonList(nodeExecution1));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution1.getUuid()))
        .thenReturn(asList(nodeExecution2, nodeExecution3));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution3.getUuid()))
        .thenReturn(asList(nodeExecution4, nodeExecution5));
    when(nodeExecutionService.fetchChildrenNodeExecutions(planExecutionId, nodeExecution4.getUuid()))
        .thenReturn(Collections.singletonList(nodeExecution6));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionChildFunctor() {
    Ambiance newAmbiance =
        AmbianceUtils.cloneForChild(ambiance, Level.newBuilder().setRuntimeId(nodeExecution1.getUuid()).build());
    NodeExecutionChildFunctor functor =
        NodeExecutionChildFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .build();
    NodeExecutionMap nodeExecutionMap = (NodeExecutionMap) functor.bind();
    assertThat(engine.getProperty(nodeExecutionMap, "param")).isEqualTo("ao");
    assertThat(engine.getProperty(nodeExecutionMap, "b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(nodeExecutionMap, "d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(nodeExecutionMap, "d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(nodeExecutionMap, "d[0].e.param")).isEqualTo("eo");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionAncestorFunctor() {
    Ambiance newAmbiance =
        AmbianceUtils.cloneForChild(ambiance, Level.newBuilder().setRuntimeId(nodeExecution6.getUuid()).build());
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();
    assertThat(engine.getProperty(functor, "stage.param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "stage.e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "a.b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(functor, "a.d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "a.d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(functor, "a.d[0].e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "d.param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "d.e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "e.param")).isEqualTo("eo");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionQualifiedFunctor() {
    NodeExecutionQualifiedFunctor functor =
        NodeExecutionQualifiedFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, ambiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(ambiance)
            .build();
    NodeExecutionMap nodeExecutionMap = (NodeExecutionMap) functor.bind();
    assertThat(engine.getProperty(nodeExecutionMap, "a.b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[0].e.param")).isEqualTo("eo");
  }

  private PlanNodeProto preparePlanNode(boolean skipExpressionChain, String identifier) {
    return preparePlanNode(skipExpressionChain, identifier, identifier + "i");
  }

  private PlanNodeProto preparePlanNode(boolean skipExpressionChain, String identifier, String paramValue) {
    return preparePlanNode(skipExpressionChain, identifier, paramValue, null);
  }

  private PlanNodeProto preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    PlanNodeProto.Builder builder =
        PlanNodeProto.newBuilder()
            .setUuid(generateUuid())
            .setName(identifier + "n")
            .setStepType(StepType.newBuilder().setType("DUMMY").build())
            .setIdentifier(identifier)
            .setSkipExpressionChain(skipExpressionChain)
            .setStepParameters(RecastOrchestrationUtils.toDocumentJson(prepareStepParameters(paramValue)));

    if (!isEmpty(groupName)) {
      builder.setGroup(groupName);
    }
    return builder.build();
  }

  private StepParameters prepareStepParameters(String paramValue) {
    return TestStepParameters.builder().param(paramValue).build();
  }
}
