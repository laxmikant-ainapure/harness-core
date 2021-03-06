package software.wings.graphql.schema.type.aggregation.billing;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.schema.type.aggregation.QLDataType;

@TargetModule(Module._380_CG_GRAPHQL)
public enum QLBillingDataFilterType {
  Application(BillingDataMetaDataFields.APPID),
  EndTime(BillingDataMetaDataFields.STARTTIME),
  StartTime(BillingDataMetaDataFields.STARTTIME),
  Service(BillingDataMetaDataFields.SERVICEID),
  Environment(BillingDataMetaDataFields.ENVID),
  Cluster(BillingDataMetaDataFields.CLUSTERID),
  CloudServiceName(BillingDataMetaDataFields.CLOUDSERVICENAME),
  LaunchType(BillingDataMetaDataFields.LAUNCHTYPE),
  TaskId(BillingDataMetaDataFields.TASKID),
  InstanceType(BillingDataMetaDataFields.INSTANCETYPE),
  InstanceName(BillingDataMetaDataFields.INSTANCENAME),
  WorkloadName(BillingDataMetaDataFields.WORKLOADNAME),
  Namespace(BillingDataMetaDataFields.NAMESPACE),
  CloudProvider(BillingDataMetaDataFields.CLOUDPROVIDERID),
  NodeInstanceId(BillingDataMetaDataFields.INSTANCEID),
  PodInstanceId(BillingDataMetaDataFields.INSTANCEID),
  ParentInstanceId(BillingDataMetaDataFields.PARENTINSTANCEID),
  LabelSearch(null),
  TagSearch(null),
  Tag(null),
  Label(null),
  EnvironmentType(null),
  AlertTime(null),
  View(null);

  private QLDataType dataType;
  private BillingDataMetaDataFields metaDataFields;
  QLBillingDataFilterType() {}

  QLBillingDataFilterType(BillingDataMetaDataFields metaDataFields) {
    this.metaDataFields = metaDataFields;
  }

  public BillingDataMetaDataFields getMetaDataFields() {
    return metaDataFields;
  }
}
