package io.harness.ccm.setup.service.support.impl;

import io.harness.ccm.setup.service.support.intfc.AzureCEConfigValidationService;
import io.harness.exception.InvalidArgumentsException;

import software.wings.app.MainConfiguration;
import software.wings.beans.ce.CEAzureConfig;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.aad.msal4j.MsalServiceException;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Slf4j
@Singleton
public class AzureCEConfigValidationServiceImpl implements AzureCEConfigValidationService {
  private static final String AZURE_STORAGE_SUFFIX = "blob.core.windows.net";
  private static final String AZURE_STORAGE_URL_FORMAT = "https://%s.%s";
  private static final String validationFailureKey = "Validation Failed";

  @Inject private MainConfiguration configuration;

  @Override
  public void verifyCrossAccountAttributes(CEAzureConfig ceAzureConfig) {
    String storageAccountName = ceAzureConfig.getStorageAccountName();
    String tenantId = ceAzureConfig.getTenantId();
    String containerName = ceAzureConfig.getContainerName();
    String directoryName = ceAzureConfig.getDirectoryName();

    // Create a BlobServiceClient object which will be used to create a container client
    String endpoint = String.format(AZURE_STORAGE_URL_FORMAT, storageAccountName, AZURE_STORAGE_SUFFIX);
    System.out.println("Verifying cross account attributes");
    log.info(endpoint);

    ClientSecretCredential clientSecretCredential =
        new ClientSecretCredentialBuilder()
            .clientId(configuration.getCeSetUpConfig().getAzureAppClientId())
            .clientSecret(configuration.getCeSetUpConfig().getAzureAppClientSecret())
            .tenantId(tenantId)
            .build();

    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(endpoint).credential(clientSecretCredential).buildClient();

    BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);

    try {
      validateIfContainerIsPresent(blobContainerClient, directoryName);
    } catch (BlobStorageException ex) {
      log.error("Exception while validating storage account details", ex);
      if (ex.getErrorCode().toString().equals("ContainerNotFound")) {
        throw new InvalidArgumentsException(
            ImmutablePair.of(validationFailureKey, "The specified container does not exist"));
      } else if (ex.getErrorCode().toString().equals("AuthorizationPermissionMismatch")) {
        throw new InvalidArgumentsException(
            ImmutablePair.of(validationFailureKey, "Authorization permission mismatch"));
      } else {
        throw new InvalidArgumentsException(ImmutablePair.of(validationFailureKey, ex.getErrorCode().toString()));
      }
    } catch (MsalServiceException ex) {
      log.error("Exception while validating tenantId", ex);
      throw new InvalidArgumentsException(
          ImmutablePair.of(validationFailureKey, "The specified tenantId does not exist"));
    } catch (Exception ex) {
      if (ex.getCause() instanceof UnknownHostException) {
        log.error("Exception while validating storage account", ex);
        throw new InvalidArgumentsException(
            ImmutablePair.of(validationFailureKey, "The specified storage account does not exist"));
      }
      log.error("Exception while validating billing export details", ex);
      throw new InvalidArgumentsException(ImmutablePair.of(validationFailureKey, ex.getMessage()));
    }
  }

  public void validateIfContainerIsPresent(BlobContainerClient blobContainerClient, String directoryName)
      throws Exception {
    System.out.println("\nListing blobs...");
    // List the blob(s) in the container.
    for (BlobItem blobItem : blobContainerClient.listBlobsByHierarchy(directoryName)) {
      System.out.println("\t" + blobItem.getName());
      return;
    }
    throw new Exception("The specified directory does not exist");
  }
}
