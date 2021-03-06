package software.wings.service.impl.aws.model;

import static software.wings.service.impl.aws.model.AwsLambdaRequest.AwsLambdaRequestType.LIST_LAMBDA_FUNCTION;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by Pranjal on 01/29/2019
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsLambdaFunctionRequest extends AwsLambdaRequest {
  @Builder
  public AwsLambdaFunctionRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    super(awsConfig, encryptionDetails, LIST_LAMBDA_FUNCTION, region);
  }
}
