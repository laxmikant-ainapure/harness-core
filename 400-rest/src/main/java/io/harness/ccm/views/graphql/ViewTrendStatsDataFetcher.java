package io.harness.ccm.views.graphql;

import static software.wings.graphql.datafetcher.billing.CloudBillingHelper.unified;

import io.harness.ccm.billing.bigquery.BigQueryService;
import io.harness.ccm.views.service.ViewsBillingService;

import software.wings.graphql.datafetcher.AbstractStatsDataFetcherWithAggregationList;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingStatsInfo;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import java.util.List;

public class ViewTrendStatsDataFetcher extends AbstractStatsDataFetcherWithAggregationList<QLCEViewAggregation,
    QLCEViewFilterWrapper, QLCEViewGroupBy, QLCEViewSortCriteria> {
  @Inject ViewsBillingService viewsBillingService;
  @Inject CloudBillingHelper cloudBillingHelper;
  @Inject BigQueryService bigQueryService;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLData fetch(String accountId, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sort) {
    String cloudProviderTableName = cloudBillingHelper.getCloudProviderTableName(accountId, unified);
    BigQuery bigQuery = bigQueryService.get();
    QLCEViewTrendInfo trendStatsData =
        viewsBillingService.getTrendStatsData(bigQuery, filters, aggregateFunction, cloudProviderTableName);
    return QLCEViewTrendStatsData.builder()
        .cost(QLBillingStatsInfo.builder()
                  .statsTrend(trendStatsData.getStatsTrend())
                  .statsLabel(trendStatsData.getStatsLabel())
                  .statsDescription(trendStatsData.getStatsDescription())
                  .statsValue(trendStatsData.getStatsValue())
                  .value(trendStatsData.getValue())
                  .build())
        .build();
  }

  @Override
  protected QLData postFetch(String accountId, List<QLCEViewGroupBy> groupByList,
      List<QLCEViewAggregation> aggregations, List<QLCEViewSortCriteria> sort, QLData qlData) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }

  @Override
  public boolean isCESampleAccountIdAllowed() {
    return false;
  }
}
