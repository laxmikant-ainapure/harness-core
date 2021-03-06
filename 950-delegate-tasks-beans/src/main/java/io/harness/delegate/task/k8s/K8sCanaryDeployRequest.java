package io.harness.delegate.task.k8s;

import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.beans.NGInstanceUnitType;
import io.harness.expression.Expression;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class K8sCanaryDeployRequest implements K8sDeployRequest {
  NGInstanceUnitType instanceUnitType;
  Integer instances;
  Integer maxInstances;
  @Expression(DISALLOW_SECRETS) String releaseName;
  @Expression(ALLOW_SECRETS) List<String> valuesYamlList;
  boolean skipDryRun;
  K8sTaskType taskType;
  String commandName;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;
  ManifestDelegateConfig manifestDelegateConfig;
  Integer timeoutIntervalInMin;
  String accountId;
}
