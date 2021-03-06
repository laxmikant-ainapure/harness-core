package software.wings.delegatetasks.azure.appservice.webapp;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;

import software.wings.delegatetasks.azure.appservice.AbstractAzureAppServiceTaskHandler;
import software.wings.delegatetasks.azure.appservice.deployment.AzureAppServiceDeploymentService;

import com.google.inject.Inject;

@TargetModule(Module._930_DELEGATE_TASKS)
public abstract class AbstractAzureWebAppTaskHandler extends AbstractAzureAppServiceTaskHandler {
  @Inject protected AzureAppServiceDeploymentService azureAppServiceDeploymentService;

  protected AzureWebClientContext buildAzureWebClientContext(
      AzureAppServiceTaskParameters appServiceTaskParameters, AzureConfig azureConfig) {
    return AzureWebClientContext.builder()
        .azureConfig(azureConfig)
        .appName(appServiceTaskParameters.getAppName())
        .subscriptionId(appServiceTaskParameters.getSubscriptionId())
        .resourceGroupName(appServiceTaskParameters.getResourceGroupName())
        .build();
  }
}
