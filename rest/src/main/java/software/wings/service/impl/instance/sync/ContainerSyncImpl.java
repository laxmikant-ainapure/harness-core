package software.wings.service.impl.instance.sync;

import static software.wings.beans.DelegateTask.SyncTaskContext.Builder.aContext;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.annotation.Encryptable;
import software.wings.beans.Application;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DelegateTask.SyncTaskContext;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.ContainerDeploymentInfo;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.common.Constants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.exception.WingsException;
import software.wings.security.encryption.EncryptedDataDetail;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.instance.sync.request.ContainerSyncRequest;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.utils.Validator;

import java.util.List;

/**
 * Created by brett on 9/6/17
 */
public class ContainerSyncImpl implements ContainerSync {
  private static final Logger logger = LoggerFactory.getLogger(ContainerSyncImpl.class);

  @Inject private SettingsService settingsService;
  @Inject private AppService appService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProxyFactory delegateProxyFactory;

  @Override
  public ContainerSyncResponse getInstances(ContainerSyncRequest syncRequest) {
    List<ContainerInfo> result = Lists.newArrayList();
    for (ContainerDeploymentInfo containerDeploymentInfo :
        syncRequest.getFilter().getContainerDeploymentInfoCollection()) {
      try {
        InfrastructureMapping infrastructureMapping =
            infraMappingService.get(containerDeploymentInfo.getAppId(), containerDeploymentInfo.getInfraMappingId());
        if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
          String msg =
              "Unsupported infrastructure mapping type for containers :" + infrastructureMapping.getInfraMappingType();
          logger.error(msg);
          throw new WingsException(msg);
        }

        SettingAttribute settingAttribute;
        String clusterName = null;
        String namespace = null;
        String region = null;
        String subscriptionId = null;
        String resourceGroup = null;
        ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
        if (containerInfraMapping instanceof DirectKubernetesInfrastructureMapping) {
          DirectKubernetesInfrastructureMapping directInfraMapping =
              (DirectKubernetesInfrastructureMapping) containerInfraMapping;
          settingAttribute = (directInfraMapping.getComputeProviderType().equals(SettingVariableTypes.DIRECT.name()))
              ? aSettingAttribute().withValue(directInfraMapping.createKubernetesConfig()).build()
              : settingsService.get(directInfraMapping.getComputeProviderSettingId());
          namespace = directInfraMapping.getNamespace();
        } else {
          settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
          clusterName = containerInfraMapping.getClusterName();
          if (containerInfraMapping instanceof GcpKubernetesInfrastructureMapping) {
            namespace = ((GcpKubernetesInfrastructureMapping) containerInfraMapping).getNamespace();
          } else if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
            namespace = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getNamespace();
            subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
            resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
          } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
            region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
          }
        }
        Validator.notNullCheck("SettingAttribute", settingAttribute);

        List<EncryptedDataDetail> encryptionDetails =
            secretManager.getEncryptionDetails((Encryptable) settingAttribute.getValue(),
                infrastructureMapping.getAppId(), containerDeploymentInfo.getWorkflowExecutionId());

        Application app = appService.get(infrastructureMapping.getAppId());

        SyncTaskContext syncTaskContext = aContext().withAccountId(app.getAccountId()).withAppId(app.getUuid()).build();
        syncTaskContext.setTimeout(Constants.DEFAULT_SYNC_CALL_TIMEOUT * 2);
        ContainerServiceParams containerServiceParams =
            ContainerServiceParams.builder()
                .settingAttribute(settingAttribute)
                .containerServiceName(containerDeploymentInfo.getContainerSvcName())
                .encryptionDetails(encryptionDetails)
                .clusterName(clusterName)
                .namespace(namespace)
                .region(region)
                .subscriptionId(subscriptionId)
                .resourceGroup(resourceGroup)
                .build();

        result.addAll(delegateProxyFactory.get(ContainerService.class, syncTaskContext)
                          .getContainerInfos(containerServiceParams));
      } catch (Exception ex) {
        logger.warn("Error while getting instances for container", ex);
      }
    }
    return ContainerSyncResponse.builder().containerInfoList(result).build();
  }

  @Override
  public ContainerSyncResponse getInstances(
      ContainerInfrastructureMapping containerInfraMapping, List<String> containerSvcNameList) {
    List<ContainerInfo> result = Lists.newArrayList();
    containerSvcNameList.stream().forEach(containerSvcName -> {

      try {
        SettingAttribute settingAttribute;
        String clusterName = null;
        String namespace = null;
        String region = null;
        String resourceGroup = null;
        String subscriptionId = null;
        if (containerInfraMapping instanceof DirectKubernetesInfrastructureMapping) {
          DirectKubernetesInfrastructureMapping directInfraMapping =
              (DirectKubernetesInfrastructureMapping) containerInfraMapping;
          settingAttribute = (directInfraMapping.getComputeProviderType().equals(SettingVariableTypes.DIRECT.name()))
              ? aSettingAttribute().withValue(directInfraMapping.createKubernetesConfig()).build()
              : settingsService.get(directInfraMapping.getComputeProviderSettingId());
          namespace = directInfraMapping.getNamespace();
        } else {
          settingAttribute = settingsService.get(containerInfraMapping.getComputeProviderSettingId());
          clusterName = containerInfraMapping.getClusterName();
          if (containerInfraMapping instanceof GcpKubernetesInfrastructureMapping) {
            namespace = ((GcpKubernetesInfrastructureMapping) containerInfraMapping).getNamespace();
          } else if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
            subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
            resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
            namespace = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getNamespace();
          } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
            region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
          }
        }
        Validator.notNullCheck("SettingAttribute", settingAttribute);

        List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
            (Encryptable) settingAttribute.getValue(), containerInfraMapping.getAppId(), null);

        Application app = appService.get(containerInfraMapping.getAppId());

        SyncTaskContext syncTaskContext = aContext().withAccountId(app.getAccountId()).withAppId(app.getUuid()).build();
        syncTaskContext.setTimeout(Constants.DEFAULT_SYNC_CALL_TIMEOUT * 2);
        ContainerServiceParams containerServiceParams = ContainerServiceParams.builder()
                                                            .settingAttribute(settingAttribute)
                                                            .containerServiceName(containerSvcName)
                                                            .encryptionDetails(encryptionDetails)
                                                            .clusterName(clusterName)
                                                            .namespace(namespace)
                                                            .region(region)
                                                            .subscriptionId(subscriptionId)
                                                            .resourceGroup(resourceGroup)
                                                            .build();

        result.addAll(delegateProxyFactory.get(ContainerService.class, syncTaskContext)
                          .getContainerInfos(containerServiceParams));
      } catch (Exception ex) {
        logger.warn("Error while getting instances for container for appId {} and infraMappingId {}",
            containerInfraMapping.getAppId(), containerInfraMapping.getUuid(), ex);
      }
    });
    return ContainerSyncResponse.builder().containerInfoList(result).build();
  }
}
