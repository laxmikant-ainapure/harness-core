package io.harness.delegate.k8s.beans;

import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class K8sCanaryHandlerConfig {
  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  private ReleaseHistory releaseHistory;
  private Release currentRelease;
  private KubernetesResource canaryWorkload;
  private List<KubernetesResource> resources;
  private Integer targetInstances;
  private String releaseName;
  private String manifestFilesDirectory;
}
