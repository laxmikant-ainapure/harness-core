package software.wings.graphql.schema.type.aggregation;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(Module._380_CG_GRAPHQL)
public class QLRequest {
  QLEntityType entityType;
  List<QLFilter> filters;
  List<QLGroupBy> groupBy;
  QLAggregateFunction aggregationFunction;
  QLTimeSeriesAggregation groupByTime;
}
