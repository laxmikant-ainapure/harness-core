package io.harness.delegate.beans.ci;

import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIK8BuildTaskParams implements CIBuildSetupTaskParams, ExecutionCapabilityDemander {
  @NotNull private ConnectorDetails k8sConnector;
  @NotNull private CIK8PodParams<CIK8ContainerParams> cik8PodParams;
  @NotNull private List<CIK8ServicePodParams> servicePodParams;
  @Builder.Default private static final Type type = Type.GCP_K8;

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        (KubernetesClusterConfigDTO) k8sConnector.getConnectorConfig();
    return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(kubernetesClusterConfigDTO, maskingEvaluator);
  }
}
