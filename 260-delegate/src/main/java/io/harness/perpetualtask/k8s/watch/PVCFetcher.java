package io.harness.perpetualtask.k8s.watch;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Store;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.util.CallGeneratorParams;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(Module._420_DELEGATE_AGENT)
public class PVCFetcher {
  private final Store<V1PersistentVolumeClaim> store;
  private final CoreV1Api coreV1Api;

  @Inject
  public PVCFetcher(@Assisted ApiClient apiClient, @Assisted SharedInformerFactory sharedInformerFactory) {
    log.info("Creating new PVCFetcher for cluster: {}", apiClient.getBasePath());
    this.coreV1Api = new CoreV1Api(apiClient);
    this.store = sharedInformerFactory
                     .sharedIndexInformerFor(
                         (CallGeneratorParams callGeneratorParams)
                             -> {
                           try {
                             return this.coreV1Api.listPersistentVolumeClaimForAllNamespacesCall(null, null, null, null,
                                 null, null, callGeneratorParams.resourceVersion, callGeneratorParams.timeoutSeconds,
                                 callGeneratorParams.watch, null);
                           } catch (ApiException e) {
                             log.error("Exception occurred creatingCall; code=[{}] headres=[{}] body=[{}]", e.getCode(),
                                 e.getResponseHeaders(), e.getResponseBody(), e);
                             throw e;
                           }
                         },
                         V1PersistentVolumeClaim.class, V1PersistentVolumeClaimList.class)
                     .getIndexer();
  }

  public V1PersistentVolumeClaim getPvcByKey(String namespace, String name) throws ApiException {
    if (this.store.getByKey(namespace + "/" + name) != null) {
      return this.store.getByKey(namespace + "/" + name);
    }

    log.warn("PVC not found in PVCInformerStore, fetching using coreV1Api.");
    return this.coreV1Api.readNamespacedPersistentVolumeClaim(name, namespace, null, null, null);
  }
}
