package software.wings.beans.infrastructure.instance.key.deployment;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "CustomDeploymentFieldKeys")
public class CustomDeploymentKey extends DeploymentKey {
  int instanceFetchScriptHash;
  List<String> tags;
}
