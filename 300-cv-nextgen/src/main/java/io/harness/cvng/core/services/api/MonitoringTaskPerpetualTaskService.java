package io.harness.cvng.core.services.api;

import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.encryption.Scope;

import java.util.List;

public interface MonitoringTaskPerpetualTaskService extends DeleteEntityByHandler<MonitoringSourcePerpetualTask> {
  void createTask(String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier,
      String monitoringSourceIdentifier);
  void deleteTask(String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier);
  List<MonitoringSourcePerpetualTask> listByConnectorIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, Scope scope);
  void createPerpetualTask(MonitoringSourcePerpetualTask monitoringSourcePerpetualTask);
  void resetLiveMonitoringPerpetualTask(MonitoringSourcePerpetualTask monitoringSourcePerpetualTask);
  String getDataCollectionWorkerId(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String monitoringSourceIdentifier);
}
