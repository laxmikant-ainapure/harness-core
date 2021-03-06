package software.wings.graphql.datafetcher.cloudefficiencyevents;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.datafetcher.cloudefficiencyevents.CEEventsQueryMetaData.CEEventsMetaDataFields;

@TargetModule(Module._380_CG_GRAPHQL)
public enum QLEventsDataFilterType {
  Application(CEEventsMetaDataFields.APPID),
  StartTime(CEEventsMetaDataFields.STARTTIME),
  EndTime(CEEventsMetaDataFields.STARTTIME),
  Service(CEEventsMetaDataFields.SERVICEID),
  Environment(CEEventsMetaDataFields.ENVID),
  Cluster(CEEventsMetaDataFields.CLUSTERID),
  CloudServiceName(CEEventsMetaDataFields.CLOUDSERVICENAME),
  TaskId(CEEventsMetaDataFields.TASKID),
  WorkloadName(CEEventsMetaDataFields.WORKLOADNAME),
  WorkloadType(CEEventsMetaDataFields.WORKLOADTYPE),
  Namespace(CEEventsMetaDataFields.NAMESPACE),
  BillingAmount(CEEventsMetaDataFields.BILLINGAMOUNT);

  private CEEventsMetaDataFields metaDataFields;
  QLEventsDataFilterType() {}

  QLEventsDataFilterType(CEEventsMetaDataFields metaDataFields) {
    this.metaDataFields = metaDataFields;
  }

  public CEEventsMetaDataFields getMetaDataFields() {
    return metaDataFields;
  }
}
