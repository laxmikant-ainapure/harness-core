package software.wings.graphql.schema.type.aggregation.service;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.Aggregation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(Module._380_CG_GRAPHQL)
public class QLServiceAggregation implements Aggregation {
  private QLServiceEntityAggregation entityAggregation;
  private QLServiceTagAggregation tagAggregation;
}
