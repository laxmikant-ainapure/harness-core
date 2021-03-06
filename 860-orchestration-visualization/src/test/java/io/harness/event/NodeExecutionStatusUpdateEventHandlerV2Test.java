package io.harness.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.data.OutcomeInstance;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionMapper;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.LevelUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.testlib.RealMongo;
import io.harness.utils.DummyVisualizationOutcome;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.assertj.core.util.Maps;
import org.awaitility.Awaitility;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Test class for {@link NodeExecutionStatusUpdateEventHandlerV2}
 */
public class NodeExecutionStatusUpdateEventHandlerV2Test extends OrchestrationVisualizationTestBase {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private SpringMongoStore mongoStore;

  @Inject private NodeExecutionService nodeExecutionService;
  @Inject @Spy private GraphGenerationService graphGenerationService;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NodeExecutionStatusUpdateEventHandlerV2 eventHandlerV2;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldDoNothingIfRuntimeIdIsNull() {
    String planExecutionId = generateUuid();
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(planExecutionId)
                                                 .addAllLevels(Collections.singletonList(Level.newBuilder().build()))
                                                 .build())
                                   .nodeExecutionProto(NodeExecutionProto.newBuilder().build())
                                   .eventType(NODE_EXECUTION_STATUS_UPDATE)
                                   .build();
    eventHandlerV2.handleEvent(event);

    verify(graphGenerationService, never()).getCachedOrchestrationGraph(planExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldAddRootNodeIdToTheGraphAndAddVertex() {
    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    NodeExecution dummyStart = NodeExecution.builder()
                                   .uuid(generateUuid())
                                   .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build())
                                   .mode(ExecutionMode.SYNC)
                                   .node(PlanNodeProto.newBuilder()
                                             .setUuid(generateUuid())
                                             .setName("name")
                                             .setStepType(StepType.newBuilder().setType("DUMMY").build())
                                             .setIdentifier("identifier1")
                                             .build())
                                   .status(Status.QUEUED)
                                   .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph = OrchestrationGraph.builder()
                                         .cacheKey(planExecution.getUuid())
                                         .cacheParams(null)
                                         .cacheContextOrder(System.currentTimeMillis())
                                         .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                            .graphVertexMap(new HashMap<>())
                                                            .adjacencyMap(new HashMap<>())
                                                            .build())
                                         .planExecutionId(planExecution.getUuid())
                                         .rootNodeIds(new ArrayList<>())
                                         .startTs(planExecution.getStartTs())
                                         .endTs(planExecution.getEndTs())
                                         .status(planExecution.getStatus())
                                         .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    // creating event
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(planExecution.getUuid())
                                                 .addAllLevels(Collections.singletonList(
                                                     Level.newBuilder().setRuntimeId(dummyStart.getUuid()).build()))
                                                 .build())
                                   .nodeExecutionProto(NodeExecutionMapper.toNodeExecutionProto(dummyStart))
                                   .eventType(NODE_EXECUTION_STATUS_UPDATE)
                                   .build();
    eventHandlerV2.handleEvent(event);

    Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
      OrchestrationGraph graphInternal = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
      return !graphInternal.getRootNodeIds().isEmpty();
    });

    OrchestrationGraph updatedGraph = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());
    assertThat(updatedGraph.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdateExistingVertexInGraphAndAddOutcomes() {
    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    NodeExecution dummyStart = NodeExecution.builder()
                                   .uuid(generateUuid())
                                   .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build())
                                   .mode(ExecutionMode.SYNC)
                                   .status(SUCCEEDED)
                                   .node(PlanNodeProto.newBuilder()
                                             .setUuid(generateUuid())
                                             .setName("name")
                                             .setStepType(StepType.newBuilder().setType("DUMMY").build())
                                             .setIdentifier("identifier1")
                                             .build())
                                   .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph =
        OrchestrationGraph.builder()
            .cacheKey(planExecution.getUuid())
            .cacheParams(null)
            .cacheContextOrder(System.currentTimeMillis())
            .adjacencyList(
                OrchestrationAdjacencyListInternal.builder()
                    .graphVertexMap(
                        Maps.newHashMap(dummyStart.getUuid(), convertNodeExecutionWithStatusSucceeded(dummyStart)))
                    .adjacencyMap(Maps.newHashMap(dummyStart.getUuid(),
                        EdgeListInternal.builder().edges(new ArrayList<>()).nextIds(new ArrayList<>()).build()))
                    .build())
            .planExecutionId(planExecution.getUuid())
            .rootNodeIds(Lists.newArrayList(dummyStart.getUuid()))
            .startTs(planExecution.getStartTs())
            .endTs(planExecution.getEndTs())
            .status(planExecution.getStatus())
            .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    // creating outcome
    DummyVisualizationOutcome dummyVisualizationOutcome = new DummyVisualizationOutcome("outcome");
    Document doc = RecastOrchestrationUtils.toDocument(dummyVisualizationOutcome);
    OutcomeInstance outcome =
        OutcomeInstance.builder()
            .planExecutionId(planExecution.getUuid())
            .producedBy(LevelUtils.buildLevelFromPlanNode(dummyStart.getUuid(), dummyStart.getNode()))
            .createdAt(System.currentTimeMillis())
            .outcome(doc)
            .build();
    mongoTemplate.insert(outcome);

    // creating event
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(planExecution.getUuid())
                                                 .addAllLevels(Collections.singletonList(
                                                     Level.newBuilder().setRuntimeId(dummyStart.getUuid()).build()))
                                                 .build())
                                   .nodeExecutionProto(NodeExecutionMapper.toNodeExecutionProto(dummyStart))
                                   .eventType(NODE_EXECUTION_STATUS_UPDATE)
                                   .build();
    eventHandlerV2.handleEvent(event);

    Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
      OrchestrationGraph graphInternal = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
      return graphInternal.getAdjacencyList().getGraphVertexMap().get(dummyStart.getUuid()).getStatus() == SUCCEEDED;
    });

    OrchestrationGraph updatedGraph = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());

    Map<String, GraphVertex> graphVertexMap = updatedGraph.getAdjacencyList().getGraphVertexMap();
    assertThat(graphVertexMap.size()).isEqualTo(1);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getStatus()).isEqualTo(SUCCEEDED);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getOutcomeDocuments())
        .containsExactlyInAnyOrder(RecastOrchestrationUtils.toDocument(dummyVisualizationOutcome));
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  private GraphVertex convertNodeExecutionWithStatusSucceeded(NodeExecution nodeExecution) {
    return GraphVertex.builder()
        .uuid(nodeExecution.getUuid())
        .planNodeId(nodeExecution.getNode().getUuid())
        .name(nodeExecution.getNode().getName())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .initialWaitDuration(nodeExecution.getInitialWaitDuration())
        .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
        .stepType(nodeExecution.getNode().getStepType().getType())
        .status(SUCCEEDED)
        .failureInfo(nodeExecution.getFailureInfo())
        .stepParameters(
            nodeExecution.getResolvedStepParameters() == null ? null : nodeExecution.getResolvedStepParameters())
        .mode(nodeExecution.getMode())
        .interruptHistories(nodeExecution.getInterruptHistories())
        .retryIds(nodeExecution.getRetryIds())
        .build();
  }
}
