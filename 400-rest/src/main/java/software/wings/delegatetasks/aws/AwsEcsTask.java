package software.wings.delegatetasks.aws;

import static io.harness.beans.ExecutionStatus.SUCCESS;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.service.impl.aws.model.AwsEcsListClusterServicesRequest;
import software.wings.service.impl.aws.model.AwsEcsListClusterServicesResponse;
import software.wings.service.impl.aws.model.AwsEcsListClustersResponse;
import software.wings.service.impl.aws.model.AwsEcsRequest;
import software.wings.service.impl.aws.model.AwsEcsRequest.AwsEcsRequestType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.services.ecs.model.Service;
import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class AwsEcsTask extends AbstractDelegateRunnableTask {
  @Inject private AwsEcsHelperServiceDelegate ecsHelperServiceDelegate;

  public AwsEcsTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    AwsEcsRequest request = (AwsEcsRequest) parameters;
    try {
      AwsEcsRequestType requestType = request.getRequestType();
      switch (requestType) {
        case LIST_CLUSTERS: {
          List<String> clusters = ecsHelperServiceDelegate.listClusters(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsEcsListClustersResponse.builder().clusters(clusters).executionStatus(SUCCESS).build();
        }
        case LIST_CLUSTER_SERVICES: {
          AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest =
              (AwsEcsListClusterServicesRequest) parameters;
          List<Service> services = ecsHelperServiceDelegate.listServicesForCluster(
              awsEcsListClusterServicesRequest.getAwsConfig(), awsEcsListClusterServicesRequest.getEncryptionDetails(),
              awsEcsListClusterServicesRequest.getRegion(), awsEcsListClusterServicesRequest.getCluster());
          return AwsEcsListClusterServicesResponse.builder().services(services).executionStatus(SUCCESS).build();
        }
        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
        }
      }
    } catch (WingsException exception) {
      throw exception;
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }
  }
}
