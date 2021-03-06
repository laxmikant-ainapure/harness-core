package software.wings.graphql.schema.type.instance;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.artifact.QLArtifact;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(Module._380_CG_GRAPHQL)
public class QLPcfInstance implements QLInstance {
  private String id;
  private QLInstanceType type;
  private String environmentId;
  private String applicationId;
  private String serviceId;
  private QLArtifact artifact;

  private String pcfId;
  private String organization;
  private String space;
  private String pcfApplicationName;
  private String pcfApplicationGuid;
  private String instanceIndex;
  private String identifier;
}
