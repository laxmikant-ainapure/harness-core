package io.harness.governance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "filterType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CustomAppFilter.class, name = "CUSTOM")
  , @JsonSubTypes.Type(value = AllAppFilter.class, name = "ALL")
})
@Data
@NoArgsConstructor
public abstract class ApplicationFilter implements BlackoutWindowFilter {
  private BlackoutWindowFilterType filterType;
  private EnvironmentFilter envSelection;

  @JsonCreator
  public ApplicationFilter(@JsonProperty("filterType") BlackoutWindowFilterType filterType,
      @JsonProperty("envSelection") EnvironmentFilter envSelection) {
    this.envSelection = envSelection;
    this.filterType = filterType;
  }
}
