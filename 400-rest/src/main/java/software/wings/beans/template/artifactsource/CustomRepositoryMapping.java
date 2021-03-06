package software.wings.beans.template.artifactsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class CustomRepositoryMapping {
  private String artifactRoot;
  private String buildNoPath;
  private List<AttributeMapping> artifactAttributes;

  @Value
  @Builder
  public static class AttributeMapping {
    private String relativePath;
    private String mappedAttribute;
  }
}
