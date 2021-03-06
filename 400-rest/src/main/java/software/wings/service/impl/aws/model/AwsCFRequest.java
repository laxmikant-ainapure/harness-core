package software.wings.service.impl.aws.model;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsCFRequest extends AwsRequest {
  public enum AwsCFRequestType { GET_TEMPLATE_PARAMETERS }

  @NotNull private String region;
  @NotNull private AwsCFRequestType requestType;

  public AwsCFRequest(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, AwsCFRequestType requestType, String region) {
    super(awsConfig, encryptionDetails);
    this.requestType = requestType;
    this.region = region;
  }
}
