package io.harness.cvng.client;

import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;

import java.util.List;

public interface VerificationManagerService {
  String createDataCollectionTask(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle);

  void resetDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier, String perpetualTaskId,
      DataCollectionConnectorBundle bundle);

  void deletePerpetualTask(String accountId, String perpetualTaskId);
  void deletePerpetualTasks(String accountId, List<String> perpetualTaskIds);
  String getDataCollectionResponse(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionRequest request);
  List<String> getKubernetesNamespaces(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String filter);
  List<String> getKubernetesWorkloads(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String namespace, String filter);
}
