package io.harness.steps.approval.step.jira;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.steps.approval.step.jira.beans.CriteriaSpec;
import io.harness.steps.approval.step.jira.beans.CriteriaSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("criteriaSpecWrapper")
public class CriteriaSpecWrapper {
  @NotNull @JsonProperty("type") CriteriaSpecType type;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CriteriaSpec criteriaSpec;

  @Builder
  public CriteriaSpecWrapper(CriteriaSpec criteriaSpec) {
    this.criteriaSpec = criteriaSpec;
  }
}
