package io.harness.batch.processing.service.intfc;

import io.harness.batch.processing.ccm.AzureStorageSyncRecord;

public interface AzureStorageSyncService {
  void syncContainer(AzureStorageSyncRecord azureStorageSyncRecord);
}
