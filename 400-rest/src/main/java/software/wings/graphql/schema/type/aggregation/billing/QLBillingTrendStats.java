package software.wings.graphql.schema.type.aggregation.billing;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@Scope(ResourceType.USER)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(Module._380_CG_GRAPHQL)
public class QLBillingTrendStats implements QLData {
  QLBillingStatsInfo totalCost;
  QLBillingStatsInfo costTrend;
  QLBillingStatsInfo forecastCost;
  QLBillingStatsInfo idleCost;
  QLBillingStatsInfo utilizedCost;
  QLBillingStatsInfo unallocatedCost;
  QLBillingStatsInfo systemCost;
  QLBillingStatsInfo efficiencyScore;
}
