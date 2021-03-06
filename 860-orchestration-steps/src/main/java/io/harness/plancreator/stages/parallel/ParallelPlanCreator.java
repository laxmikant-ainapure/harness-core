package io.harness.plancreator.stages.parallel;

import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.facilitator.chilidren.ChildrenFacilitator;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepOutcomeGroup;
import io.harness.steps.common.NGForkStep;
import io.harness.steps.fork.ForkStepParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ParallelPlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("parallel", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    List<YamlField> dependencyNodeIdsList = getDependencyNodeIdsList(ctx);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    for (YamlField yamlField : dependencyNodeIdsList) {
      Map<String, YamlField> yamlFieldMap = new HashMap<>();
      yamlFieldMap.put(yamlField.getNode().getUuid(), yamlField);
      responseMap.put(yamlField.getNode().getUuid(), PlanCreationResponse.builder().dependencies(yamlFieldMap).build());
    }

    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    YamlNode currentNode = config.getNode();
    return PlanNode.builder()
        .uuid(currentNode.getUuid())
        .name(YAMLFieldNameConstants.PARALLEL)
        .identifier(YAMLFieldNameConstants.PARALLEL + currentNode.getUuid())
        .stepType(NGForkStep.STEP_TYPE)
        .stepParameters(ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build())
        .facilitatorObtainment(FacilitatorObtainment.newBuilder().setType(ChildrenFacilitator.FACILITATOR_TYPE).build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(config))
        .skipExpressionChain(true)
        .build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, YamlField config) {
    List<String> possibleSiblings = new ArrayList<>();
    possibleSiblings.add(YAMLFieldNameConstants.STAGE);
    possibleSiblings.add(YAMLFieldNameConstants.PARALLEL);
    YamlField nextSibling =
        ctx.getCurrentField().getNode().nextSiblingFromParentArray(YAMLFieldNameConstants.PARALLEL, possibleSiblings);

    List<YamlField> children = getStageChildFields(ctx);
    if (children.isEmpty()) {
      return GraphLayoutResponse.builder().build();
    }
    List<String> childrenUuids =
        children.stream().map(YamlField::getNode).map(YamlNode::getUuid).collect(Collectors.toList());
    EdgeLayoutList.Builder stagesEdgesBuilder = EdgeLayoutList.newBuilder().addAllCurrentNodeChildren(childrenUuids);
    if (nextSibling != null) {
      stagesEdgesBuilder.addNextIds(nextSibling.getNode().getUuid());
    }
    Map<String, GraphLayoutNode> layoutNodeMap = children.stream().collect(Collectors.toMap(stageField
        -> stageField.getNode().getUuid(),
        stageField
        -> GraphLayoutNode.newBuilder()
               .setNodeUUID(stageField.getNode().getUuid())
               .setNodeGroup(StepOutcomeGroup.STAGE.name())
               .setNodeType(stageField.getNode().getType())
               .setNodeIdentifier(stageField.getNode().getIdentifier())
               .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
               .build()));
    GraphLayoutNode parallelNode = GraphLayoutNode.newBuilder()
                                       .setNodeUUID(config.getNode().getUuid())
                                       .setNodeType(YAMLFieldNameConstants.PARALLEL)
                                       .setNodeGroup(StepOutcomeGroup.STAGE.name())
                                       .setNodeIdentifier(YAMLFieldNameConstants.PARALLEL + config.getNode().getUuid())
                                       .setEdgeLayoutList(stagesEdgesBuilder.build())
                                       .build();
    layoutNodeMap.put(config.getNode().getUuid(), parallelNode);
    return GraphLayoutResponse.builder().layoutNodes(layoutNodeMap).build();
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (currentField != null && currentField.getNode() != null) {
      YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(currentField.getName(),
          Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.STEP, YAMLFieldNameConstants.STEP_GROUP,
              YAMLFieldNameConstants.PARALLEL));
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        adviserObtainments.add(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    OnSuccessAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
                .build());
      }
    }
    return adviserObtainments;
  }

  @VisibleForTesting
  List<YamlField> getDependencyNodeIdsList(PlanCreationContext planCreationContext) {
    List<YamlField> childYamlFields = getStageChildFields(planCreationContext);
    if (childYamlFields.isEmpty()) {
      List<YamlNode> yamlNodes =
          Optional.of(planCreationContext.getCurrentField().getNode().asArray()).orElse(Collections.emptyList());

      yamlNodes.forEach(yamlNode -> {
        YamlField stageField = yamlNode.getField(YAMLFieldNameConstants.STEP);
        YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
        if (stageField != null) {
          childYamlFields.add(stageField);
        } else if (stepGroupField != null) {
          childYamlFields.add(stepGroupField);
        }
      });
    }
    return childYamlFields;
  }

  private List<YamlField> getStageChildFields(PlanCreationContext planCreationContext) {
    return Optional.of(planCreationContext.getCurrentField().getNode().asArray())
        .orElse(Collections.emptyList())
        .stream()
        .map(el -> el.getField(YAMLFieldNameConstants.STAGE))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
