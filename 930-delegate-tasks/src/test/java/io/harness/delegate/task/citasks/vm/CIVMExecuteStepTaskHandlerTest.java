package io.harness.delegate.task.citasks.vm;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepResponse;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVMExecuteStepTaskHandlerTest extends CategoryTest {
  @Mock private HttpHelper httpHelper;
  @InjectMocks private io.harness.delegate.task.citasks.vm.CIVMExecuteStepTaskHandler CIVMExecuteStepTaskHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternal() throws IOException {
    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder().stageRuntimeId("stage").build();
    Response<ExecuteStepResponse> executeStepResponse =
        Response.success(ExecuteStepResponse.builder().ExitCode(0).build());
    when(httpHelper.executeStepWithRetries(anyMap())).thenReturn(executeStepResponse);
    VmTaskExecutionResponse response = CIVMExecuteStepTaskHandler.executeTaskInternal(params);
    assertEquals(CommandExecutionStatus.SUCCESS, response.getCommandExecutionStatus());
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void executeTaskInternalFailure() {
    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder().stageRuntimeId("stage").build();
    Response<ExecuteStepResponse> executeStepResponse =
        Response.success(ExecuteStepResponse.builder().ExitCode(1).build());
    when(httpHelper.executeStepWithRetries(anyMap())).thenReturn(executeStepResponse);
    VmTaskExecutionResponse response = CIVMExecuteStepTaskHandler.executeTaskInternal(params);
    assertEquals(CommandExecutionStatus.FAILURE, response.getCommandExecutionStatus());
  }
}