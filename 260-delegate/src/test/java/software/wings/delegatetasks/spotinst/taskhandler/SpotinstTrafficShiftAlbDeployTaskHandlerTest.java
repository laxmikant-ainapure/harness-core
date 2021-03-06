package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbDeployParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbDeployResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;

import com.amazonaws.services.ec2.model.Instance;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(Module._930_DELEGATE_TASKS)
public class SpotinstTrafficShiftAlbDeployTaskHandlerTest extends WingsBaseTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteInternal() throws Exception {
    SpotinstTrafficShiftAlbDeployTaskHandler handler = spy(SpotinstTrafficShiftAlbDeployTaskHandler.class);
    doNothing().when(handler).updateElastiGroupAndWait(
        anyString(), anyString(), any(), anyInt(), any(), anyString(), anyString());
    doReturn(singletonList(new Instance().withInstanceId("i-newId")))
        .doReturn(singletonList(new Instance().withInstanceId("i-oldId")))
        .when(handler)
        .getAllEc2InstancesOfElastiGroup(any(), anyString(), anyString(), anyString(), anyString());
    SpotinstTrafficShiftAlbDeployParameters parameters = SpotinstTrafficShiftAlbDeployParameters.builder()
                                                             .newElastigroup(ElastiGroup.builder().build())
                                                             .oldElastigroup(ElastiGroup.builder().build())
                                                             .awsRegion("us-east-1")
                                                             .timeoutIntervalInMin(10)
                                                             .build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(
        parameters, SpotInstConfig.builder().spotInstToken(new char[] {'t'}).build(), AwsConfig.builder().build());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    SpotInstTaskResponse spotinstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotinstTaskResponse).isNotNull();
    assertThat(spotinstTaskResponse instanceof SpotinstTrafficShiftAlbDeployResponse).isTrue();
    SpotinstTrafficShiftAlbDeployResponse deployResponse = (SpotinstTrafficShiftAlbDeployResponse) spotinstTaskResponse;
    assertThat(deployResponse.getEc2InstancesAdded().size()).isEqualTo(1);
    assertThat(deployResponse.getEc2InstancesAdded().get(0).getInstanceId()).isEqualTo("i-newId");
    assertThat(deployResponse.getEc2InstancesExisting().size()).isEqualTo(1);
    assertThat(deployResponse.getEc2InstancesExisting().get(0).getInstanceId()).isEqualTo("i-oldId");
  }
}
