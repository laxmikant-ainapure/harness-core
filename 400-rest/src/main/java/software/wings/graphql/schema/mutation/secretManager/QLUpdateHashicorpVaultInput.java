package software.wings.graphql.schema.mutation.secretManager;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.secrets.QLUsageScope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(Module._380_CG_GRAPHQL)
public class QLUpdateHashicorpVaultInput {
  String name;
  QLHashicorpVaultAuthDetails authDetails;
  Boolean isReadOnly;
  Long secretEngineRenewalInterval;
  Boolean isDefault;
  QLUsageScope usageScope;
}
