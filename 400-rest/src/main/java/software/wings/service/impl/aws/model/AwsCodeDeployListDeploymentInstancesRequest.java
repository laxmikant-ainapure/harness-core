package software.wings.service.impl.aws.model;

import static software.wings.service.impl.aws.model.AwsCodeDeployRequest.AwsCodeDeployRequestType.LIST_DEPLOYMENT_INSTANCES;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsCodeDeployListDeploymentInstancesRequest extends AwsCodeDeployRequest {
  private String deploymentId;

  @Builder
  public AwsCodeDeployListDeploymentInstancesRequest(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String deploymentId) {
    super(awsConfig, encryptionDetails, LIST_DEPLOYMENT_INSTANCES, region);
    this.deploymentId = deploymentId;
  }
}
