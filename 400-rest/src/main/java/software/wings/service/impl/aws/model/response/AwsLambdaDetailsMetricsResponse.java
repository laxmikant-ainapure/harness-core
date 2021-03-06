package software.wings.service.impl.aws.model.response;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;

import software.wings.beans.infrastructure.instance.InvocationCount;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class AwsLambdaDetailsMetricsResponse implements AwsResponse {
  private DelegateMetaInfo delegateMetaInfo;
  private ExecutionStatus executionStatus;
  private String errorMessage;
  private AwsLambdaDetails lambdaDetails;
  private List<InvocationCount> invocationCountList;
}
