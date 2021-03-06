package io.harness.plancreator.steps;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.common.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.intfc.WithSkipCondition;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@TypeAlias("stepElementConfig")
public class StepElementConfig implements WithSkipCondition {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;
  @EntityIdentifier String identifier;
  @EntityName String name;
  String description;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<Timeout> timeout;
  List<FailureStrategyConfig> failureStrategies;

  String type;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  StepSpecType stepSpecType;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> skipCondition;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  ParameterField<List<String>> delegateSelectors;

  @Builder
  public StepElementConfig(String uuid, String identifier, String name, String description,
      ParameterField<Timeout> timeout, List<FailureStrategyConfig> failureStrategies, String type,
      StepSpecType stepSpecType, ParameterField<String> skipCondition, ParameterField<List<String>> delegateSelectors) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.timeout = timeout;
    this.failureStrategies = failureStrategies;
    this.type = type;
    this.stepSpecType = stepSpecType;
    this.skipCondition = skipCondition;
    this.delegateSelectors = delegateSelectors;
  }
}
