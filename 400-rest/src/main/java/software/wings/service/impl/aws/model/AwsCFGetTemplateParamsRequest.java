package software.wings.service.impl.aws.model;

import static software.wings.service.impl.aws.model.AwsCFRequest.AwsCFRequestType.GET_TEMPLATE_PARAMETERS;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsCFGetTemplateParamsRequest extends AwsCFRequest {
  @NotNull private String type;
  private String data;
  private List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private GitConfig gitConfig;
  private GitFileConfig gitFileConfig;

  @Builder
  public AwsCFGetTemplateParamsRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String data,
      String region, String type, String sourceRepoSettingId, String sourceRepoBranch, String templatePath,
      List<EncryptedDataDetail> sourceRepoEncryptionDetails, GitFileConfig gitFileConfig, GitConfig gitConfig) {
    super(awsConfig, encryptionDetails, GET_TEMPLATE_PARAMETERS, region);
    this.data = data;
    this.type = type;
    this.gitFileConfig = gitFileConfig;
    this.gitConfig = gitConfig;
    this.sourceRepoEncryptionDetails = sourceRepoEncryptionDetails;
  }
}
