package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppNamesParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppNamesResponse;

import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.appservice.WebApp;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class AzureWebAppListWebAppNamesTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Inject private AzureWebClient azureWebClient;

  @Override
  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    String subscriptionId = azureAppServiceTaskParameters.getSubscriptionId();
    String resourceGroupName =
        ((AzureWebAppListWebAppNamesParameters) azureAppServiceTaskParameters).getResourceGroupName();

    AzureClientContext azureClientContext = new AzureClientContext(azureConfig, subscriptionId, resourceGroupName);

    List<WebApp> webApps = azureWebClient.listWebAppsByResourceGroupName(azureClientContext);

    return AzureWebAppListWebAppNamesResponse.builder().webAppNames(toWebAppNames(webApps)).build();
  }

  @NotNull
  private List<String> toWebAppNames(List<WebApp> webApps) {
    return webApps.stream().map(WebApp::name).collect(Collectors.toList());
  }
}
