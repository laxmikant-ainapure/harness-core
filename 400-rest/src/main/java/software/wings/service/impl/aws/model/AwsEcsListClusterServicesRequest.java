package software.wings.service.impl.aws.model;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsEcsListClusterServicesRequest extends AwsEcsRequest {
  @Getter @Setter private String cluster;
  @Builder
  public AwsEcsListClusterServicesRequest(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String cluster) {
    super(awsConfig, encryptionDetails, AwsEcsRequestType.LIST_CLUSTER_SERVICES, region);
    this.cluster = cluster;
  }
}
