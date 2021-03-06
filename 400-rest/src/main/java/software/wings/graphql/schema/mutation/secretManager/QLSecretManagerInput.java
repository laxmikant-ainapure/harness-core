package software.wings.graphql.schema.mutation.secretManager;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.secrets.QLUsageScope;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TargetModule(Module._380_CG_GRAPHQL)
public abstract class QLSecretManagerInput {
  private boolean isDefault;
  private QLUsageScope usageScope;
}
