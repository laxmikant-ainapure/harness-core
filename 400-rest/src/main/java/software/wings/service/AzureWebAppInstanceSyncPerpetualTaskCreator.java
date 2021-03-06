package software.wings.service;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;

import static java.lang.String.format;

import io.harness.exception.InvalidArgumentsException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.instancesync.AzureWebAppInstanceSyncPerpetualTaskClientParams;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AzureWebAppInstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AzureWebAppDeploymentKey;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.util.Durations;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AzureWebAppInstanceSyncPerpetualTaskCreator implements InstanceSyncPerpetualTaskCreator {
  private static final String APP_NAME = "appName";
  private static final String SLOT_NAME = "slotName";

  @Inject private InstanceService instanceService;
  @Inject private PerpetualTaskService perpetualTaskService;

  @Override
  public List<String> createPerpetualTasks(InfrastructureMapping infrastructureMapping) {
    Set<String> webAppsDeploymentKeys =
        getWebAppsDeploymentKeys(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
    return createPerpetualTasks(webAppsDeploymentKeys, infrastructureMapping);
  }

  private Set<String> getWebAppsDeploymentKeys(String appId, String infraMappingId) {
    List<Instance> instances = instanceService.getInstancesForAppAndInframapping(appId, infraMappingId);
    return emptyIfNull(instances)
        .stream()
        .map(Instance::getInstanceInfo)
        .filter(AzureWebAppInstanceInfo.class ::isInstance)
        .map(AzureWebAppInstanceInfo.class ::cast)
        .map(instanceInfo -> getWebAppDeploymentKey(instanceInfo.getAppName(), instanceInfo.getSlotName()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  @Override
  public List<String> createPerpetualTasksForNewDeployment(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping) {
    List<AzureWebAppDeploymentKey> newWebAppInstancesDeploymentKeys =
        deploymentSummaries.stream().map(DeploymentSummary::getAzureWebAppDeploymentKey).collect(Collectors.toList());

    Set<String> existingWebAppDeploymentKeys =
        existingPerpetualTasks.stream()
            .filter(Objects::nonNull)
            .map(PerpetualTaskRecord::getClientContext)
            .map(PerpetualTaskClientContext::getClientParams)
            .map(params -> getWebAppDeploymentKey(params.get(APP_NAME), params.get(SLOT_NAME)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Set<String> newWebAppDeploymentKeys = deploymentSummaries.stream()
                                              .map(DeploymentSummary::getAzureWebAppDeploymentKey)
                                              .map(AzureWebAppDeploymentKey::getKey)
                                              .collect(Collectors.toSet());

    Sets.SetView<String> newDeployedWebAppDeploymentKeys =
        Sets.difference(newWebAppDeploymentKeys, existingWebAppDeploymentKeys);

    Set<String> webAppsDeploymentKeys =
        newWebAppInstancesDeploymentKeys.stream()
            .filter(newDeployedWebAppKey -> newDeployedWebAppDeploymentKeys.contains(newDeployedWebAppKey.getKey()))
            .map(AzureWebAppDeploymentKey::getKey)
            .collect(Collectors.toSet());

    return createPerpetualTasks(webAppsDeploymentKeys, infrastructureMapping);
  }

  private List<String> createPerpetualTasks(
      Set<String> webAppsDeploymentKeys, InfrastructureMapping infrastructureMapping) {
    String appId = infrastructureMapping.getAppId();
    String infraMappingId = infrastructureMapping.getUuid();
    String accountId = infrastructureMapping.getAccountId();

    return webAppsDeploymentKeys.stream()
        .map(webAppDeploymentKey
            -> AzureWebAppInstanceSyncPerpetualTaskClientParams.builder()
                   .appId(appId)
                   .infraMappingId(infraMappingId)
                   .appName(getWebAppName(webAppDeploymentKey))
                   .slotName(getWebAppSlotName(webAppDeploymentKey))
                   .build())
        .map(params -> create(accountId, params))
        .collect(Collectors.toList());
  }

  private String create(String accountId, AzureWebAppInstanceSyncPerpetualTaskClientParams clientParams) {
    Map<String, String> paramMap = ImmutableMap.of(HARNESS_APPLICATION_ID, clientParams.getAppId(),
        INFRASTRUCTURE_MAPPING_ID, clientParams.getInfraMappingId(), APP_NAME, clientParams.getAppName(), SLOT_NAME,
        clientParams.getSlotName());

    PerpetualTaskClientContext clientContext = PerpetualTaskClientContext.builder().clientParams(paramMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                         .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                         .build();

    return perpetualTaskService.createTask(
        PerpetualTaskType.AZURE_WEB_APP_INSTANCE_SYNC, accountId, clientContext, schedule, false, "");
  }

  private Optional<String> getWebAppDeploymentKey(String appName, String slotName) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(slotName)) {
      return Optional.empty();
    }
    return Optional.of(format("%s_%s", appName, slotName));
  }

  private String getWebAppSlotName(String webAppDeploymentKey) {
    String[] appAndSlotName = webAppDeploymentKey.split("_");
    if (appAndSlotName.length != 2) {
      throw new InvalidArgumentsException(format("Invalid web app deployment key: [%s]", webAppDeploymentKey));
    }

    return appAndSlotName[1];
  }

  private String getWebAppName(String webAppDeploymentKey) {
    String[] appAndSlotName = webAppDeploymentKey.split("_");
    if (appAndSlotName.length != 2) {
      throw new InvalidArgumentsException(format("Invalid web app deployment key: [%s]", webAppDeploymentKey));
    }

    return appAndSlotName[0];
  }
}
