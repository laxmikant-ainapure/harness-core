package software.wings.cloudprovider.aws;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.ClusterConfiguration;

import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by anubhaw on 12/29/16.
 */
@TargetModule(Module._960_API_SERVICES)
public interface AwsClusterService {
  /**
   * Create cluster.
   */
  void createCluster(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, ClusterConfiguration clusterConfiguration,
      LogCallback logCallback);

  /**
   * Resize cluster.
   */
  List<ContainerInfo> resizeCluster(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName, int previousCount,
      int desiredSize, int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback);

  /**
   * Delete service.
   */
  void deleteService(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName);

  /**
   * Gets services.
   */
  List<Service> getServices(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName);

  List<Service> getServices(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceNamePrefix);

  TargetGroup getTargetGroup(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String targetGroupArn);

  /**
   * Create service.
   */
  void createService(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, CreateServiceRequest clusterConfiguration);

  /**
   * Trigger run task
   */
  RunTaskResult triggerRunTask(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, RunTaskRequest runTaskRequest);

  /**
   * Create task task definition.
   */
  TaskDefinition createTask(String region, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, RegisterTaskDefinitionRequest registerTaskDefinitionRequest);

  LinkedHashMap<String, Integer> getActiveServiceCounts(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String containerServiceName);

  Map<String, String> getActiveServiceImages(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String containerServiceName,
      String imagePrefix);

  Optional<Service> getService(String region, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName);
}
