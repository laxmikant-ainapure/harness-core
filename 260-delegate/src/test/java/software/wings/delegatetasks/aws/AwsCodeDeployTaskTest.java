package software.wings.delegatetasks.aws;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployRequest;
import software.wings.service.intfc.aws.delegate.AwsCodeDeployHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(Module._930_DELEGATE_TASKS)
public class AwsCodeDeployTaskTest extends WingsBaseTest {
  @Mock private AwsCodeDeployHelperServiceDelegate mockAwsCodeDeployHelperServiceDelegate;

  @InjectMocks
  private AwsCodeDeployTask task =
      new AwsCodeDeployTask(DelegateTaskPackage.builder()
                                .delegateId("delegateid")
                                .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("awsCodeDeployHelperServiceDelegate", mockAwsCodeDeployHelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsCodeDeployRequest request = AwsCodeDeployListAppRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listApplications(any(), anyList(), anyString());
    request = AwsCodeDeployListDeploymentConfigRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentConfiguration(any(), anyList(), anyString());
    request = AwsCodeDeployListDeploymentGroupRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentGroups(any(), anyList(), anyString(), anyString());
    request = AwsCodeDeployListDeploymentInstancesRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentInstances(any(), anyList(), anyString(), anyString());
    request = AwsCodeDeployListAppRevisionRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate)
        .listAppRevision(any(), anyList(), anyString(), anyString(), anyString());
  }
}
