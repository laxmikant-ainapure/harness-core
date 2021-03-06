package io.harness.pms.yaml;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCheckParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    // Stage1 Service Node
    YamlNode serviceNode = stage1Node.getField("spec").getNode().getField("service").getNode();
    YamlField serviceSiblingNode = serviceNode.nextSiblingNodeFromParentObject("infrastructure");
    YamlNode infraNode = stage1Node.getField("spec").getNode().getField("infrastructure").getNode();
    assertThat(serviceSiblingNode.getNode().getUuid()).isEqualTo(infraNode.getUuid());
    YamlNode stepsNode =
        stage1Node.getField("spec").getNode().getField("execution").getNode().getField("steps").getNode();

    YamlNode step1Node = stepsNode.asArray().get(0).getField("step").getNode();
    YamlField step1SiblingNode =
        step1Node.nextSiblingFromParentArray("step", Arrays.asList("step", "stepGroup", "parallel"));
    YamlNode step2Node = stepsNode.asArray().get(1).getField("step").getNode();
    assertThat(step2Node.getIdentifier()).isEqualTo(step1SiblingNode.getNode().getIdentifier());

    // Stage2 Node
    YamlNode stage2Node = stagesNode.getNode().asArray().get(1).getField("stage").getNode();

    YamlField siblingOfStage1 = stage1Node.nextSiblingFromParentArray("stage", Arrays.asList("stage", "parallel"));
    assertThat(siblingOfStage1.getNode().getIdentifier()).isEqualTo(stage2Node.getIdentifier());

    // parallel stages node
    YamlNode parallel1Node = stagesNode.getNode().asArray().get(2).getField("parallel").getNode();

    YamlField siblingOfStage2 = stage2Node.nextSiblingFromParentArray("stage", Arrays.asList("stage", "parallel"));
    assertThat(siblingOfStage2.getNode().asArray().get(0).getField("stage").getNode().getIdentifier())
        .isEqualTo(parallel1Node.asArray().get(0).getField("stage").getNode().getIdentifier());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testgetFullyQualifiedName() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    String stageFQN = YamlUtils.getFullyQualifiedName(stage1Node);
    assertThat(stageFQN).isEqualTo("pipeline.stages.qaStage");
    // Stage1 Service Node
    YamlNode serviceNode = stage1Node.getField("spec").getNode().getField("service").getNode();
    assertThat(YamlUtils.getFullyQualifiedName(serviceNode)).isEqualTo("pipeline.stages.qaStage.service");

    // image Path qualified Name
    YamlNode imagePath = serviceNode.getField("serviceDefinition")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("artifacts")
                             .getNode()
                             .getField("primary")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("imagePath")
                             .getNode();
    assertThat(YamlUtils.getFullyQualifiedName(imagePath))
        .isEqualTo("pipeline.stages.qaStage.service.artifacts.primary.imagePath");

    // infrastructure qualified name
    YamlNode infraNode = stage1Node.getField("spec").getNode().getField("infrastructure").getNode();
    assertThat(YamlUtils.getFullyQualifiedName(infraNode)).isEqualTo("pipeline.stages.qaStage.infrastructure");

    // step qualified name
    YamlNode stepsNode =
        stage1Node.getField("spec").getNode().getField("execution").getNode().getField("steps").getNode();
    YamlNode step1Node = stepsNode.asArray().get(0).getField("step").getNode();
    assertThat(YamlUtils.getFullyQualifiedName(step1Node))
        .isEqualTo("pipeline.stages.qaStage.execution.steps.rolloutDeployment");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testgetQNBetweenTwoFields() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    // Stage1 Service Node
    YamlNode serviceNode = stage1Node.getField("spec").getNode().getField("service").getNode();

    // image Path qualified Name
    YamlNode imagePath = serviceNode.getField("serviceDefinition")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("artifacts")
                             .getNode()
                             .getField("primary")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("imagePath")
                             .getNode();
    assertThat(YamlUtils.getQNBetweenTwoFields(imagePath, "stages", "service")).isEqualTo("stages.service");

    // step qualified name
    YamlNode stepsNode =
        stage1Node.getField("spec").getNode().getField("execution").getNode().getField("steps").getNode();
    YamlNode step1Node = stepsNode.asArray().get(0).getField("step").getNode();
    assertThat(YamlUtils.getQNBetweenTwoFields(step1Node, "execution", "steps")).isEqualTo("execution.steps");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testQNTillGivenFieldName() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    String stageFQN = YamlUtils.getQualifiedNameTillGivenField(stage1Node, "stage");
    assertThat(stageFQN).isEqualTo("qaStage");
    // Stage1 Service Node
    YamlNode serviceNode = stage1Node.getField("spec").getNode().getField("service").getNode();
    assertThat(YamlUtils.getQualifiedNameTillGivenField(serviceNode, "stage")).isEqualTo("qaStage.service");

    // image Path qualified Name
    YamlNode imagePath = serviceNode.getField("serviceDefinition")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("artifacts")
                             .getNode()
                             .getField("primary")
                             .getNode()
                             .getField("spec")
                             .getNode()
                             .getField("imagePath")
                             .getNode();
    assertThat(YamlUtils.getQualifiedNameTillGivenField(imagePath, "service"))
        .isEqualTo("service.artifacts.primary.imagePath");

    // infrastructure qualified name
    YamlNode infraNode = stage1Node.getField("spec").getNode().getField("infrastructure").getNode();
    assertThat(YamlUtils.getQualifiedNameTillGivenField(infraNode, "infrastructure")).isEqualTo("infrastructure");

    // step qualified name
    YamlNode stepsNode =
        stage1Node.getField("spec").getNode().getField("execution").getNode().getField("steps").getNode();
    YamlNode step1Node = stepsNode.asArray().get(0).getField("step").getNode();
    assertThat(YamlUtils.getQualifiedNameTillGivenField(step1Node, "execution"))
        .isEqualTo("execution.steps.rolloutDeployment");
  }
}