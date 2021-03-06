package software.wings.delegatetasks.aws;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.aws.model.AwsRoute53ListHostedZonesRequest;
import software.wings.service.impl.aws.model.AwsRoute53Request;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(Module._930_DELEGATE_TASKS)
public class AwsRoute53TaskTest extends WingsBaseTest {
  @Mock private AwsRoute53HelperServiceDelegate mockAwsRoute53HelperServiceDelegate;

  @InjectMocks
  private AwsRoute53Task task =
      new AwsRoute53Task(DelegateTaskPackage.builder()
                             .delegateId("delegateid")
                             .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                             .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("awsRoute53HelperServiceDelegate", mockAwsRoute53HelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsRoute53Request request = AwsRoute53ListHostedZonesRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsRoute53HelperServiceDelegate).listHostedZones(any(), anyList(), anyString());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunWithInvalidRequestException() {
    doThrow(new RuntimeException("Error msg"))
        .when(mockAwsRoute53HelperServiceDelegate)
        .listHostedZones(any(), anyList(), anyString());

    AwsRoute53ListHostedZonesRequest request = AwsRoute53ListHostedZonesRequest.builder().build();
    task.run(new Object[] {request});

    verify(mockAwsRoute53HelperServiceDelegate).listHostedZones(any(), anyList(), anyString());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunWithWingsException() {
    doThrow(new InvalidRequestException("Error msg"))
        .when(mockAwsRoute53HelperServiceDelegate)
        .listHostedZones(any(), anyList(), anyString());

    AwsRoute53ListHostedZonesRequest request = AwsRoute53ListHostedZonesRequest.builder().build();
    task.run(new Object[] {request});

    verify(mockAwsRoute53HelperServiceDelegate).listHostedZones(any(), anyList(), anyString());
  }
}
