package software.wings.helpers.ext.azure.devops;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@OwnedBy(CDC)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class AzureArtifactsProtocolMetadataData {
  long size;
}
