package io.harness.cdng.pipeline;

import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.PipelineInfrastructureVisitorHelper;
import io.harness.common.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.LevelNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OneOfField(fields = {"environment", "environmentRef"})
@SimpleVisitorHelper(helperClass = PipelineInfrastructureVisitorHelper.class)
@TypeAlias("pipelineInfrastructure")
public class PipelineInfrastructure implements Visitable {
  private InfrastructureDef infrastructureDefinition;
  @Wither private InfraUseFromStage useFromStage;
  private EnvironmentYaml environment;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> environmentRef;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  public PipelineInfrastructure applyUseFromStage(PipelineInfrastructure infrastructureToUseFrom) {
    return infrastructureToUseFrom.withUseFromStage(this.useFromStage);
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add("infrastructureDefinition", infrastructureDefinition);
    children.add("environment", environment);
    children.add("useFromStage", useFromStage);
    return children;
  }

  @Override
  public LevelNode getLevelNode() {
    return LevelNode.builder().qualifierName(YamlTypes.PIPELINE_INFRASTRUCTURE).build();
  }
}
