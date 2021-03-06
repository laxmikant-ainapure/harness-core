package io.harness.cdng.pipeline.stepinfo;

import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.pipeline.steps.HttpStep;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.cdng.visitor.helpers.cdstepinfo.HttpStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.http.HttpBaseStepInfo;
import io.harness.http.HttpHeaderConfig;
import io.harness.http.HttpStepParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.facilitator.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.steps.io.BaseStepParameterInfo;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.beans.LevelNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.HTTP)
@SimpleVisitorHelper(helperClass = HttpStepInfoVisitorHelper.class)
@TypeAlias("httpStepInfo")
public class HttpStepInfo extends HttpBaseStepInfo implements CDStepInfo, Visitable {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String name;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String identifier;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public HttpStepInfo(ParameterField<String> url, ParameterField<String> method, List<HttpHeaderConfig> headers,
      ParameterField<String> requestBody, ParameterField<String> assertion, List<NGVariable> outputVariables,
      String name, String identifier) {
    super(url, method, headers, requestBody, assertion, outputVariables);
    this.name = name;
    this.identifier = identifier;
  }

  @Override
  public String getDisplayName() {
    return name;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return HttpStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public LevelNode getLevelNode() {
    return LevelNode.builder().qualifierName(YamlTypes.HTTP_STEP).build();
  }

  @Override
  public StepParameters getStepParametersWithRollbackInfo(BaseStepParameterInfo baseStepParameterInfo) {
    return HttpStepParameters.infoBuilder()
        .assertion(getAssertion())
        .headers(getHeaders())
        .method(getMethod())
        .outputVariables(getOutputVariables())
        .requestBody(getRequestBody())
        .rollbackInfo(baseStepParameterInfo.getRollbackInfo())
        .timeout(baseStepParameterInfo.getTimeout())
        .url(getUrl())
        .name(baseStepParameterInfo.getName())
        .identifier(baseStepParameterInfo.getIdentifier())
        .skipCondition(baseStepParameterInfo.getSkipCondition())
        .description(baseStepParameterInfo.getSkipCondition())
        .build();
  }
}
