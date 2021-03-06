package software.wings.graphql.schema.type.instance;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.artifact.QLArtifact;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for container instance like docker
 * @author rktummala on 08/25/17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(Module._380_CG_GRAPHQL)
public abstract class QLContainerInstance implements QLInstance {
  private String id;
  private QLInstanceType type;
  private String environmentId;
  private String applicationId;
  private String serviceId;
  private QLArtifact artifact;

  private String clusterName;
  private String identifier;
}
