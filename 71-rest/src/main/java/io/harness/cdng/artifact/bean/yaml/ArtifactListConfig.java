package io.harness.cdng.artifact.bean.yaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.data.Outcome;
import lombok.Builder;
import lombok.Value;

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactListConfig implements Outcome {
  ArtifactConfig primary;

  // TODO(archit): include sidecars after primary is done.
}
