package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static software.wings.common.Constants.BG_BLUE;
import static software.wings.common.Constants.BG_GREEN;
import static software.wings.common.Constants.BG_VERSION;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Tag;
import com.amazonaws.services.ecs.model.TagResourceRequest;
import com.amazonaws.services.ecs.model.UntagResourceRequest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.security.encryption.EncryptedDataDetail;
import software.wings.service.impl.AwsHelperService;

import java.util.List;

@Singleton
public class EcsSwapRoutesCommandTaskHelper {
  @Inject private AwsHelperService awsHelperService;
  @Inject private EcsContainerService ecsContainerService;

  public void upsizeOlderService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region,
      String cluster, int count, String serviceName, ExecutionLogCallback executionLogCallback, int timeout) {
    if (isEmpty(serviceName)) {
      executionLogCallback.saveExecutionLog("No service needs to be upsized");
      return;
    }

    if (needToUpdateDesiredCount(
            awsConfig, encryptedDataDetails, region, cluster, count, serviceName, executionLogCallback)) {
      ecsContainerService.updateServiceCount(
          region, encryptedDataDetails, cluster, serviceName, count, executionLogCallback, awsConfig);
    }

    ecsContainerService.waitForTasksToBeInRunningStateButDontThrowException(
        region, awsConfig, encryptedDataDetails, cluster, serviceName, executionLogCallback, count);

    ecsContainerService.waitForServiceToReachSteadyState(
        region, awsConfig, encryptedDataDetails, cluster, serviceName, timeout, executionLogCallback);
  }

  private boolean needToUpdateDesiredCount(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String region, String cluster, int count, String serviceName, ExecutionLogCallback executionLogCallback) {
    DescribeServicesResult describeServicesResult = awsHelperService.describeServices(region, awsConfig,
        encryptedDataDetails, new DescribeServicesRequest().withCluster(cluster).withServices(serviceName));
    Service service = describeServicesResult.getServices().get(0);
    if (count == service.getDesiredCount()) {
      executionLogCallback.saveExecutionLog(
          format("Service: [%s] is already at desired count: [%d]", serviceName, count));
      return false;
    } else {
      executionLogCallback.saveExecutionLog(
          format("Need to update service desired count to: [%d] for service: [%s]", count, serviceName));
      return true;
    }
  }

  public void updateServiceTags(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region,
      String cluster, String newServiceName, String oldServiceName, boolean rollback,
      ExecutionLogCallback logCallback) {
    DescribeServicesResult newService = awsHelperService.describeServices(region, awsConfig, encryptedDataDetails,
        new DescribeServicesRequest().withCluster(cluster).withServices(newServiceName));

    DescribeServicesResult oldService = null;
    if (isNotBlank(oldServiceName)) {
      oldService = awsHelperService.describeServices(region, awsConfig, encryptedDataDetails,
          new DescribeServicesRequest().withCluster(cluster).withServices(oldServiceName));
    }

    Service blueService;
    Service greenService;
    if (rollback) {
      // This was first deploy, so there was no prev "BLUE" service
      blueService = oldService == null ? null : oldService.getServices().get(0);
      greenService = newService.getServices().get(0);
    } else {
      // This is first deploy, so there is no "GREEN" service
      greenService = oldService == null ? null : oldService.getServices().get(0);
      blueService = newService.getServices().get(0);
    }

    tagEcsService(awsConfig, encryptedDataDetails, region, blueService, BG_BLUE, logCallback);
    tagEcsService(awsConfig, encryptedDataDetails, region, greenService, BG_GREEN, logCallback);
  }

  private void tagEcsService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region,
      Service service, String tagValue, ExecutionLogCallback logCallback) {
    if (service == null) {
      return;
    }
    logCallback.saveExecutionLog(
        format("Updating service: [%s] with tag: [%s:%s]", service.getServiceName(), BG_VERSION, tagValue));
    awsHelperService.untagService(region, encryptedDataDetails,
        new UntagResourceRequest().withResourceArn(service.getServiceArn()).withTagKeys(BG_VERSION), awsConfig);
    awsHelperService.tagService(region, encryptedDataDetails,
        new TagResourceRequest()
            .withResourceArn(service.getServiceArn())
            .withTags(new Tag().withKey(BG_VERSION).withValue(tagValue)),
        awsConfig);
    logCallback.saveExecutionLog("Tag update successful");
  }

  public void downsizeOlderService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region,
      String cluster, String serviceName, ExecutionLogCallback logCallback) {
    if (isNotEmpty(serviceName)) {
      logCallback.saveExecutionLog("Downsizing Green Service: " + serviceName);
      ecsContainerService.updateServiceCount(
          region, encryptedDataDetails, cluster, serviceName, 0, logCallback, awsConfig);
      final int downSizeTimeSecs = 30;
      logCallback.saveExecutionLog(
          format("Waiting: [%d] seconds for the downsize to complete Ecs services to synchrnoze", downSizeTimeSecs));
      sleep(ofSeconds(downSizeTimeSecs));
    } else {
      logCallback.saveExecutionLog("No Service needs to be downsized");
    }
  }
}