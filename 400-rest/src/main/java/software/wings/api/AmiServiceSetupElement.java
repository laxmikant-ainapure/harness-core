package software.wings.api;

import io.harness.context.ContextElementType;
import io.harness.pms.sdk.core.data.SweepingOutput;

import software.wings.api.AwsAmiInfoVariables.AwsAmiInfoVariablesBuilder;
import software.wings.beans.ResizeStrategy;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("amiServiceSetupElement")
public class AmiServiceSetupElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private int instanceCount;
  private String newAutoScalingGroupName;
  private String oldAutoScalingGroupName;
  private Integer autoScalingSteadyStateTimeout;
  private Integer maxInstances;
  private int desiredInstances;
  private int minInstances;
  private List<String> oldAsgNames;
  private AwsAmiPreDeploymentData preDeploymentData;
  private boolean blueGreen;
  private ResizeStrategy resizeStrategy;
  private List<String> baseScalingPolicyJSONs;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AMI_SERVICE_SETUP;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    Map<String, Object> map = new HashMap<>();
    map.put("newAsgName", newAutoScalingGroupName);
    map.put("oldAsgName", oldAutoScalingGroupName);
    return ImmutableMap.of("ami", map);
  }

  public AwsAmiInfoVariables fetchAmiVariableInfo() {
    AwsAmiInfoVariablesBuilder builder = AwsAmiInfoVariables.builder();
    if (newAutoScalingGroupName != null) {
      builder.newAsgName(newAutoScalingGroupName);
    }
    if (oldAutoScalingGroupName != null) {
      builder.oldAsgName(oldAutoScalingGroupName);
    }
    return builder.build();
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "amiServiceSetupElement";
  }
}
