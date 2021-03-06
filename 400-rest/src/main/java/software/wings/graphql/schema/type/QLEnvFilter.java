package software.wings.graphql.schema.type;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLEnvFilterKeys")
@TargetModule(Module._380_CG_GRAPHQL)
public class QLEnvFilter {
  private Set<QLEnvFilterType> filterTypes;
  private Set<String> envIds;
}
