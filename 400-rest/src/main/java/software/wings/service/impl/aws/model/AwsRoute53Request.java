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
public class AwsRoute53Request extends AwsRequest {
  public enum AwsRoute53RequestType { LIST_HOSTED_ZONES }

  @NotNull private AwsRoute53RequestType requestType;

  public AwsRoute53Request(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, AwsRoute53RequestType requestType) {
    super(awsConfig, encryptionDetails);
    this.requestType = requestType;
  }
}
