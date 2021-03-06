package software.wings.delegatetasks.azure.appservice.webapp;

import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.SUCCESS_REQUEST;
import static io.harness.azure.model.AzureConstants.UPDATE_DEPLOYMENT_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import lombok.Getter;

@TargetModule(Module._930_DELEGATE_TASKS)
public enum AppServiceDeploymentProgress {
  SAVE_CONFIGURATION(SAVE_EXISTING_CONFIGURATIONS),
  STOP_SLOT(STOP_DEPLOYMENT_SLOT),
  UPDATE_SLOT_CONFIGURATIONS(UPDATE_DEPLOYMENT_SLOT_CONFIGURATION_SETTINGS),
  UPDATE_SLOT_CONTAINER_SETTINGS(UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS),
  START_SLOT(START_DEPLOYMENT_SLOT),
  UPDATE_TRAFFIC_PERCENT(SLOT_TRAFFIC_PERCENTAGE),
  SWAP_SLOT(SLOT_SWAP),
  DEPLOYMENT_COMPLETE(SUCCESS_REQUEST);

  @Getter private final String stepName;

  AppServiceDeploymentProgress(String stepName) {
    this.stepName = stepName;
  }
}
