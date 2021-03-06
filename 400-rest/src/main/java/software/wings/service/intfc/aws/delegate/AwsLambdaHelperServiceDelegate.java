package software.wings.service.intfc.aws.delegate;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.aws.model.AwsLambdaFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;

@TargetModule(Module._930_DELEGATE_TASKS)
public interface AwsLambdaHelperServiceDelegate {
  AwsLambdaExecuteWfResponse executeWf(AwsLambdaExecuteWfRequest request, ExecutionLogCallback logCallback);
  AwsLambdaExecuteFunctionResponse executeFunction(AwsLambdaExecuteFunctionRequest request);
  AwsLambdaFunctionResponse getLambdaFunctions(AwsLambdaFunctionRequest request);

  AwsLambdaDetailsResponse getFunctionDetails(AwsLambdaDetailsRequest request, boolean isInstanceSync);
}
