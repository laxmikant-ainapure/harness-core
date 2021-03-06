package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;
import static java.lang.String.valueOf;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.spotinst.request.SpotInstGetElastigroupJsonParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstGetElastigroupJsonResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupNamesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class SpotInstSyncTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotInstTaskParameters,
      SpotInstConfig spotInstConfig, AwsConfig awsConfig) throws Exception {
    if (!spotInstTaskParameters.isSyncTask()) {
      throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
          spotInstTaskParameters.getCommandType().name()));
    }

    String spotInstAccountId = spotInstConfig.getSpotInstAccountId();
    String spotInstToken = valueOf(spotInstConfig.getSpotInstToken());
    SpotInstTaskResponse syncTaskResponse;
    switch (spotInstTaskParameters.getCommandType()) {
      case SPOT_INST_LIST_ELASTI_GROUPS: {
        List<ElastiGroup> groups = spotInstHelperServiceDelegate.listAllElstiGroups(spotInstToken, spotInstAccountId);
        syncTaskResponse = SpotInstListElastigroupNamesResponse.builder().elastigroups(groups).build();
        break;
      }
      case SPOT_INST_GET_ELASTI_GROUP_JSON: {
        String elastigroupId = ((SpotInstGetElastigroupJsonParameters) spotInstTaskParameters).getElastigroupId();
        String json = spotInstHelperServiceDelegate.getElastigroupJson(spotInstToken, spotInstAccountId, elastigroupId);
        syncTaskResponse = SpotInstGetElastigroupJsonResponse.builder().elastigroupJson(json).build();
        break;
      }
      case SPOT_INST_LIST_ELASTI_GROUP_INSTANCES: {
        SpotInstListElastigroupInstancesParameters listInstancesParams =
            (SpotInstListElastigroupInstancesParameters) spotInstTaskParameters;
        List<Instance> instances = getAllEc2InstancesOfElastiGroup(awsConfig, listInstancesParams.getAwsRegion(),
            spotInstToken, spotInstAccountId, listInstancesParams.getElastigroupId());
        syncTaskResponse = SpotInstListElastigroupInstancesResponse.builder()
                               .elastigroupId(listInstancesParams.getElastigroupId())
                               .elastigroupInstances(instances)
                               .build();
        break;
      }
      default: {
        throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
            spotInstTaskParameters.getCommandType().name()));
      }
    }
    return SpotInstTaskExecutionResponse.builder()
        .spotInstTaskResponse(syncTaskResponse)
        .commandExecutionStatus(SUCCESS)
        .build();
  }
}
