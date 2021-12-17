package io.harness.pms.sdk.core.plan.creation.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncCreatorContext;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanCreationContext implements AsyncCreatorContext {
  YamlField currentField;
  @Singular("globalContext") Map<String, PlanCreationContextValue> globalContext;
  String yaml;

  public static PlanCreationContext cloneWithCurrentField(
      PlanCreationContext planCreationContext, YamlField field, String yaml) {
    return PlanCreationContext.builder()
        .currentField(field)
        .yaml(yaml)
        .globalContext(planCreationContext.getGlobalContext())
        .build();
  }

  public void mergeContextFromPlanCreationResponse(PlanCreationResponse planCreationResponse) {
    if (EmptyPredicate.isEmpty(getGlobalContext())) {
      this.setGlobalContext(new HashMap<>());
    }
    this.getGlobalContext().putAll(planCreationResponse.getContextMap());
  }

  public PlanCreationContextValue getMetadata() {
    return globalContext == null ? null : globalContext.get("metadata");
  }

  @Override
  public ByteString getGitSyncBranchContext() {
    PlanCreationContextValue value = getMetadata();
    if (value == null) {
      return null;
    }
    return getMetadata().getMetadata().getGitSyncBranchContext();
  }

  public List<YamlField> getStepYamlFields() {
    List<YamlNode> yamlNodes =
        Optional
            .of(Preconditions.checkNotNull(getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS))
                    .getNode()
                    .asArray())
            .orElse(Collections.emptyList());
    List<YamlField> stepFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        stepFields.add(stepField);
      } else if (stepGroupField != null) {
        stepFields.add(stepGroupField);
      } else if (parallelStepField != null) {
        stepFields.add(parallelStepField);
      }
    });
    return stepFields;
  }
}
