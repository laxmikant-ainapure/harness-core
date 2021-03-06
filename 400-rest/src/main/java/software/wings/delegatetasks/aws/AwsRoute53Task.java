package software.wings.delegatetasks.aws;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.impl.aws.model.AwsRoute53ListHostedZonesRequest;
import software.wings.service.impl.aws.model.AwsRoute53ListHostedZonesResponse;
import software.wings.service.impl.aws.model.AwsRoute53Request;
import software.wings.service.impl.aws.model.AwsRoute53Request.AwsRoute53RequestType;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@TargetModule(Module._930_DELEGATE_TASKS)
public class AwsRoute53Task extends AbstractDelegateRunnableTask {
  @Inject private AwsRoute53HelperServiceDelegate awsRoute53HelperServiceDelegate;

  public AwsRoute53Task(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsRoute53Request request = (AwsRoute53Request) parameters[0];
    try {
      AwsRoute53RequestType requestType = request.getRequestType();
      switch (requestType) {
        case LIST_HOSTED_ZONES: {
          List<AwsRoute53HostedZoneData> hostedZones =
              awsRoute53HelperServiceDelegate.listHostedZones(request.getAwsConfig(), request.getEncryptionDetails(),
                  ((AwsRoute53ListHostedZonesRequest) request).getRegion());
          return AwsRoute53ListHostedZonesResponse.builder().hostedZones(hostedZones).executionStatus(SUCCESS).build();
        }
        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", USER);
        }
      }
    } catch (WingsException exception) {
      throw exception;
    } catch (Exception ex) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(ex), USER);
    }
  }
}
