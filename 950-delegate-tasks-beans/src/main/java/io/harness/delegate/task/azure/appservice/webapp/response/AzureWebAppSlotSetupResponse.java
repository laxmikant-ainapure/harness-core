package io.harness.delegate.task.azure.appservice.webapp.response;

import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AzureWebAppSlotSetupResponse implements AzureAppServiceTaskResponse {
  private AzureAppServicePreDeploymentData preDeploymentData;
  private List<AzureAppDeploymentData> azureAppDeploymentData;
  private String errorMsg;
}
