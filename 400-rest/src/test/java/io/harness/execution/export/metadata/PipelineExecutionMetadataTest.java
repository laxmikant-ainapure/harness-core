package io.harness.execution.export.metadata;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;

import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineExecutionMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAccept() {
    MetadataTestUtils.SimpleVisitor simpleVisitor = new MetadataTestUtils.SimpleVisitor();
    PipelineExecutionMetadata.builder()
        .stages(asList(PipelineStageExecutionMetadata.builder()
                           .workflowExecution(WorkflowExecutionMetadata.builder()
                                                  .executionGraph(Collections.singletonList(
                                                      GraphNodeMetadata.builder().id("id1").build()))
                                                  .build())
                           .build(),
            PipelineStageExecutionMetadata.builder()
                .workflowExecution(
                    WorkflowExecutionMetadata.builder()
                        .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("id2").build()))
                        .build())
                .build()))
        .build()
        .accept(simpleVisitor);
    assertThat(simpleVisitor.getVisited()).containsExactly("id1", "id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromWorkflowExecution() {
    assertThat(PipelineExecutionMetadata.fromWorkflowExecution(null)).isNull();
    assertThat(PipelineExecutionMetadata.fromWorkflowExecution(
                   WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build()))
        .isNull();

    Instant now = Instant.now();
    PipelineExecutionMetadata pipelineExecutionMetadata =
        PipelineExecutionMetadata.fromWorkflowExecution(MetadataTestUtils.preparePipelineWorkflowExecution(now));
    MetadataTestUtils.validatePipelineWorkflowExecutionMetadata(pipelineExecutionMetadata, now);
  }
}
