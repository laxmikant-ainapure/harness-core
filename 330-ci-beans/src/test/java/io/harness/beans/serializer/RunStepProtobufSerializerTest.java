package io.harness.beans.serializer;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import io.harness.CiBeansTestBase;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RunStepProtobufSerializerTest extends CiBeansTestBase {
  public static final String RUN_STEP = "run-step";
  public static final String RUN_STEP_ID = "run-step-id";
  public static final String MVN_CLEAN_INSTALL = "mvn clean install";
  public static final String OUTPUT = "output";
  public static final int TIMEOUT = 100;
  public static final String CALLBACK_ID = "callbackId";
  public static final int RETRY = 2;
  public static final Integer PORT = 8000;
  @Inject ProtobufStepSerializer<RunStepInfo> protobufSerializer;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldSerializeRunStep() throws InvalidProtocolBufferException {
    List<String> paths = Arrays.asList("path1", "path2");
    //    JunitTestReport junitTestReport =
    //        JunitTestReport.builder().spec(JunitTestReport.Spec.builder().paths(paths).build()).build();
    //    List<UnitTestReport> unitTestReportList = Arrays.asList(junitTestReport);
    //    RunStepInfo runStepInfo = RunStepInfo.builder()
    //                                  .name(RUN_STEP)
    //                                  .identifier(RUN_STEP_ID)
    //                                  .retry(RETRY)
    //                                  .command(MVN_CLEAN_INSTALL)
    //                                  .reports(unitTestReportList)
    //                                  .output(Arrays.asList(OUTPUT))
    //                                  .build();
    //    StepElementConfig stepElement = StepElementConfig.builder()
    //                                        .name(RUN_STEP)
    //                                        .identifier(RUN_STEP_ID)
    //                                        .type("run")
    //                                        .stepSpecType(runStepInfo)
    //                                        .build();

    // Report report = Report.newBuilder().setType(Report.Type.JUNIT).addAllPaths(paths).build();

    //    runStepInfo.setCallbackId(CALLBACK_ID);
    //    runStepInfo.setPort(PORT);
    //    assertThat(runStep.getId()).isEqualTo(RUN_STEP_ID);
    //    assertThat(runStep.getDisplayName()).isEqualTo(RUN_STEP);
    //    assertThat(runStep.getRun().getContext().getNumRetries()).isEqualTo(RETRY);
    //    assertThat(runStep.getRun().getContext().getExecutionTimeoutSecs()).isEqualTo(TIMEOUT);
    //    assertThat(runStep.getRun().getCommand()).isEqualTo(MVN_CLEAN_INSTALL);
    //    assertThat(runStep.getRun().getContainerPort()).isEqualTo(PORT);
    //    assertThat(runStep.getRun().getReports(0)).isEqualTo(report);
  }
}
