package software.wings.service.impl.aws.model;

import static software.wings.service.impl.aws.model.AwsCodeDeployRequest.AwsCodeDeployRequestType.LIST_APP_REVISION;

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
public class AwsCodeDeployListAppRevisionRequest extends AwsCodeDeployRequest {
  private String appName;
  private String deploymentGroupName;

  @Builder
  public AwsCodeDeployListAppRevisionRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String appName, String deploymentGroupName) {
    super(awsConfig, encryptionDetails, LIST_APP_REVISION, region);
    this.appName = appName;
    this.deploymentGroupName = deploymentGroupName;
  }
}
