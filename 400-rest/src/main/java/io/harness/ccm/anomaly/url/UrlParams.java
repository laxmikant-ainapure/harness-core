package io.harness.ccm.anomaly.url;

public enum UrlParams {
  AGGREGATION_TYPE("aggregationType"),
  CHART_TYPE("chartType"),
  UTILIZATION_AGGREGATION("utilizationAggregationType"),
  FILTER_ON("isFilterOn"),
  GROUP_BY("groupBy"),
  FROM_DATE("from"),
  TO_DATE("to"),

  SHOW_UNALLOCATED("showUnallocated"),
  SHOW_OTHERS("showOthers"),
  CURRENT_VIEW("currentView"),

  CLUSTER_ID("clusterList"),
  NAMESPACE("clusterNamespaceList"),

  GCP_DISCOUNTS("includeGCPDiscounts"),
  GCP_PRODUCT("gcpProductList"),
  GCP_PROJECT("gcpProjectList"),
  GCP_SKU("gcpSKUList"),

  AWS_ACCOUNT("awsAccountList"),
  AWS_SERVICE("awsServiceList"),

  CURRENT_TAB("currentTab"),
  FILTER("filter"),
  SELECTED_VIEW("selectedView");

  private String param;
  UrlParams(String param) {
    this.param = param;
  }

  public String getParam() {
    return param;
  }
}
