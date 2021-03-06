package io.harness.cvng;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.task.k8s.K8sYamlToDelegateDTOMapper;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cvng.K8InfoDataService;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PodList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(Module._420_DELEGATE_AGENT)
public class K8InfoDataServiceImpl implements K8InfoDataService {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Inject private ApiClientFactory apiClientFactory;

  @Override
  public List<String> getNameSpaces(
      DataCollectionConnectorBundle bundle, List<EncryptedDataDetail> encryptedDataDetails, String filter) {
    KubernetesClusterConfigDTO kubernetesClusterConfig = (KubernetesClusterConfigDTO) bundle.getConnectorConfigDTO();
    KubernetesAuthCredentialDTO kubernetesCredentialAuth =
        ((KubernetesClusterDetailsDTO) kubernetesClusterConfig.getCredential().getConfig()).getAuth().getCredentials();
    secretDecryptionService.decrypt(kubernetesCredentialAuth, encryptedDataDetails);
    KubernetesConfig kubernetesConfig =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(kubernetesClusterConfig, null);
    ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    try {
      V1NamespaceList v1NamespaceList =
          coreV1Api.listNamespace(null, Boolean.TRUE, null, null, null, Integer.MAX_VALUE, null, 60, Boolean.FALSE);
      List<String> rv = new ArrayList<>();
      v1NamespaceList.getItems().forEach(v1Namespace -> {
        if (isNotEmpty(filter)
            && !v1Namespace.getMetadata().getName().toLowerCase().contains(filter.trim().toLowerCase())) {
          return;
        }
        rv.add(v1Namespace.getMetadata().getName());
      });
      Collections.sort(rv);
      return rv;
    } catch (ApiException apiException) {
      log.error("failed to fetch namespaces", apiException);
      throw new DataCollectionException(apiException.getResponseBody());
    }
  }

  @Override
  public List<String> getWorkloads(String namespace, DataCollectionConnectorBundle bundle,
      List<EncryptedDataDetail> encryptedDataDetails, String filter) {
    KubernetesClusterConfigDTO kubernetesClusterConfig = (KubernetesClusterConfigDTO) bundle.getConnectorConfigDTO();
    KubernetesAuthCredentialDTO kubernetesCredentialAuth =
        ((KubernetesClusterDetailsDTO) kubernetesClusterConfig.getCredential().getConfig()).getAuth().getCredentials();
    secretDecryptionService.decrypt(kubernetesCredentialAuth, encryptedDataDetails);
    KubernetesConfig kubernetesConfig =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(kubernetesClusterConfig, null);
    ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    try {
      V1PodList podList = coreV1Api.listNamespacedPod(
          namespace, null, Boolean.TRUE, null, null, null, Integer.MAX_VALUE, null, 60, Boolean.FALSE);
      Set<String> rv = new HashSet<>();
      podList.getItems().forEach(viPod -> {
        List<V1OwnerReference> ownerReferences = viPod.getMetadata().getOwnerReferences();
        if (isNotEmpty(ownerReferences)) {
          ownerReferences.forEach(v1OwnerReference -> {
            if (isNotEmpty(filter) && !v1OwnerReference.getName().toLowerCase().contains(filter.trim().toLowerCase())) {
              return;
            }
            if ("ReplicaSet".equals(v1OwnerReference.getKind())) {
              rv.add(v1OwnerReference.getName().substring(0, v1OwnerReference.getName().lastIndexOf("-")));
            }

            if ("StatefulSet".equals(v1OwnerReference.getKind()) || "DaemonSet".equals(v1OwnerReference.getKind())) {
              rv.add(v1OwnerReference.getName());
            }
          });
        }
      });
      List<String> workloads = rv.stream().collect(Collectors.toList());
      Collections.sort(workloads);
      return workloads;
    } catch (ApiException apiException) {
      log.error("failed to fetch pods", apiException);
      throw new DataCollectionException(apiException.getResponseBody());
    }
  }
}
