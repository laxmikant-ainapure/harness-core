package software.wings.beans.infrastructure.instance.info;

import software.wings.helpers.ext.helm.response.HelmChartInfo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author rktummala on 09/05/17
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KubernetesContainerInfo extends ContainerInfo {
  private String controllerType;
  private String controllerName;
  private String serviceName;
  private String podName;
  private String ip;
  private String namespace;

  /*
  Helm Release to which the kubernetes pods belong to
   */
  private String releaseName;

  // only applicable for helm deployments
  private HelmChartInfo helmChartInfo;

  @Builder
  public KubernetesContainerInfo(String clusterName, String controllerType, String controllerName, String serviceName,
      String podName, String ip, String namespace, HelmChartInfo helmChartInfo, String releaseName) {
    super(clusterName);
    this.controllerType = controllerType;
    this.controllerName = controllerName;
    this.serviceName = serviceName;
    this.podName = podName;
    this.ip = ip;
    this.namespace = namespace;
    this.helmChartInfo = helmChartInfo;
    this.releaseName = releaseName;
  }
}
