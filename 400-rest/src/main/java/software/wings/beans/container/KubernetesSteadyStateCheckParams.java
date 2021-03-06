package software.wings.beans.container;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;

import software.wings.service.impl.ContainerServiceParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class KubernetesSteadyStateCheckParams implements ExecutionCapabilityDemander {
  private String accountId;
  private String appId;
  private String commandName;
  private String activityId;
  private ContainerServiceParams containerServiceParams;
  private Map<String, String> labels;
  private long timeoutMillis;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return containerServiceParams.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
