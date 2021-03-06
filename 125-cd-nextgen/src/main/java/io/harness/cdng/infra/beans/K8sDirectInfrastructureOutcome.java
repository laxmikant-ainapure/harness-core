package io.harness.cdng.infra.beans;

import io.harness.cdng.environment.EnvironmentOutcome;
import io.harness.cdng.infra.yaml.InfrastructureKind;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.KUBERNETES_DIRECT)
@TypeAlias("cdng.infra.beans.K8sDirectInfrastructureOutcome")
public class K8sDirectInfrastructureOutcome implements InfrastructureOutcome {
  String connectorRef;
  String namespace;
  String releaseName;
  EnvironmentOutcome environment;

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  }

  @Override
  public String getType() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  }
}
