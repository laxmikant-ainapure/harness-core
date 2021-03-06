package io.harness.connector.utils;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.entities.embedded.ceazure.BillingExportDetails;
import io.harness.connector.entities.embedded.ceazure.CEAzureConfig;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureFeatures;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;

@UtilityClass
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class AzureConnectorTestHelper {
  String SUBSCRIPTION_ID = "subscriptionId";
  String TENANT_ID = "tenantId";
  List<CEAzureFeatures> FEATURES_ENABLED = ImmutableList.of(CEAzureFeatures.OPTIMIZATION, CEAzureFeatures.BILLING);

  String CONTAINER_NAME = "containerName";
  String DIRECTORY_NAME = "directoryName";
  String STORAGE_ACCOUNT_NAME = "storageAccountName";

  public ConnectorDTO createConnectorDTO() {
    return CommonTestHelper.createConnectorDTO(ConnectorType.CE_AZURE, createCEAzureConnectorDTO());
  }

  public CEAzureConfig createCEAzureConfig() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(FEATURES_ENABLED)
        .billingExportDetails(createBillingExportDetails())
        .build();
  }

  public CEAzureConfig createCEAzureConfigBillingOnly() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(Collections.singletonList(CEAzureFeatures.BILLING))
        .billingExportDetails(createBillingExportDetails())
        .build();
  }

  public CEAzureConfig createCEAzureConfigOptimizationOnly() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(Collections.singletonList(CEAzureFeatures.OPTIMIZATION))
        .build();
  }

  public BillingExportSpecDTO createBillingExportSpecDTO() {
    return BillingExportSpecDTO.builder()
        .storageAccountName(STORAGE_ACCOUNT_NAME)
        .directoryName(DIRECTORY_NAME)
        .containerName(CONTAINER_NAME)
        .build();
  }

  public BillingExportDetails createBillingExportDetails() {
    return BillingExportDetails.builder()
        .storageAccountName(STORAGE_ACCOUNT_NAME)
        .containerName(CONTAINER_NAME)
        .directoryName(DIRECTORY_NAME)
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTO() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(FEATURES_ENABLED)
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .billingExportSpec(createBillingExportSpecDTO())
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTOBillingOnly() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(Collections.singletonList(CEAzureFeatures.BILLING))
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .billingExportSpec(createBillingExportSpecDTO())
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTOOptimizationOnly() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(Collections.singletonList(CEAzureFeatures.OPTIMIZATION))
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .build();
  }
}
