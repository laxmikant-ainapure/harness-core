package software.wings.helpers.ext.gcb.models;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class BuildOperationDetails {
  private String name;
  @JsonProperty("metadata") private OperationMeta operationMeta;
  @JsonProperty("done") private Boolean isDone;
  private OperationError error;
}
