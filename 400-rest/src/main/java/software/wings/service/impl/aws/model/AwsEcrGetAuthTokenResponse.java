package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;

import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsEcrGetAuthTokenResponse implements AwsResponse {
  private DelegateMetaInfo delegateMetaInfo;
  private ExecutionStatus executionStatus;
  private String errorMessage;
  String ecrAuthToken;
}
