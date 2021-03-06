package software.wings.graphql.schema.type.aggregation.deployment;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.Aggregation;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesAggregation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(Module._380_CG_GRAPHQL)
public class QLDeploymentAggregation implements Aggregation {
  private QLDeploymentEntityAggregation entityAggregation;
  private QLTimeSeriesAggregation timeAggregation;
  private QLDeploymentTagAggregation tagAggregation;
}
