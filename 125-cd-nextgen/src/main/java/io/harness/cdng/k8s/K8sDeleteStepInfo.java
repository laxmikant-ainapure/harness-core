package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.facilitator.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.beans.LevelNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.timeout.TimeoutUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.K8S_DELETE)
@SimpleVisitorHelper(helperClass = K8sDeleteStepInfoVisitorHelper.class)
@TypeAlias("K8sDeleteStepInfo")
public class K8sDeleteStepInfo extends K8sDeleteBaseStepInfo implements CDStepInfo, Visitable {
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public K8sDeleteStepInfo(DeleteResourcesWrapper deleteResources, ParameterField<Boolean> skipDryRun) {
    super(deleteResources, skipDryRun);
  }

  @Override
  public StepType getStepType() {
    return K8sDeleteStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public LevelNode getLevelNode() {
    return LevelNode.builder().qualifierName(YamlTypes.K8S_DELETE).build();
  }

  @Override
  public StepParameters getStepParametersInfo(StepElementConfig stepElementConfig) {
    return K8sDeleteStepParameters.infoBuilder()
        .deleteResources(this.getDeleteResources())
        .skipDryRun(this.getSkipDryRun())
        .timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepElementConfig.getTimeout())))
        .name(stepElementConfig.getName())
        .description(stepElementConfig.getDescription())
        .identifier(stepElementConfig.getIdentifier())
        .skipCondition(stepElementConfig.getSkipCondition())
        .build();
  }
}
