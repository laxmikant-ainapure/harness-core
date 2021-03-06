package software.wings.helpers.ext.openshift;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.cli.CliResponse;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class OpenShiftDelegateServiceTest extends WingsBaseTest {
  @Mock private OpenShiftClient openShiftClient;
  @InjectMocks @Inject private OpenShiftDelegateService openShiftDelegateService;

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testProcessTemplatization() throws IOException {
    shouldGiveK8SReadyYaml();
    shouldThrowExceptionWhenOcProcessFails();
    shouldThrowExceptionWhenProcessResultEmpty();
    shouldThrowExceptionWhenItemsEmpty();
  }

  private void shouldThrowExceptionWhenItemsEmpty() throws IOException {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = ".";
    List<String> paramFileContent = Arrays.asList("a:b", "c:d");
    List<String> paramFilePaths = Arrays.asList("params-0", "params-1");
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    File ocResultYamlFile = new File("400-rest/src/test/resources/openshift/oc_empty_items.yaml");

    CliResponse cliResponse = CliResponse.builder()
                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                  .output(FileUtils.readFileToString(ocResultYamlFile, "UTF-8"))
                                  .build();

    Mockito.doReturn(cliResponse)
        .when(openShiftClient)
        .process(OC_BINARY_PATH, TEMPLATE_FILE_PATH, paramFilePaths, MANIFEST_DIRECTORY_PATH, executionLogCallback);

    assertThatThrownBy(()
                           -> openShiftDelegateService.processTemplatization(MANIFEST_DIRECTORY_PATH, OC_BINARY_PATH,
                               TEMPLATE_FILE_PATH, executionLogCallback, paramFileContent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Items list can't be empty");
  }

  private void shouldThrowExceptionWhenProcessResultEmpty() {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = ".";
    List<String> paramFileContent = Arrays.asList("a:b", "c:d");
    List<String> paramFilePaths = Arrays.asList("params-0", "params-1");
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    CliResponse cliResponse =
        CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output("").build();

    Mockito.doReturn(cliResponse)
        .when(openShiftClient)
        .process(OC_BINARY_PATH, TEMPLATE_FILE_PATH, paramFilePaths, MANIFEST_DIRECTORY_PATH, executionLogCallback);

    assertThatThrownBy(()
                           -> openShiftDelegateService.processTemplatization(MANIFEST_DIRECTORY_PATH, OC_BINARY_PATH,
                               TEMPLATE_FILE_PATH, executionLogCallback, paramFileContent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Oc process result can't be empty");
  }

  private void shouldThrowExceptionWhenOcProcessFails() throws IOException {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = ".";
    List<String> paramFileContent = Arrays.asList("a:b", "c:d");
    List<String> paramFilePaths = Arrays.asList("params-0", "params-1");
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    CliResponse cliResponse = CliResponse.builder()
                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                  .output("Invalid parameter")
                                  .build();

    Mockito.doReturn(cliResponse)
        .when(openShiftClient)
        .process(OC_BINARY_PATH, TEMPLATE_FILE_PATH, paramFilePaths, MANIFEST_DIRECTORY_PATH, executionLogCallback);

    assertThatThrownBy(()
                           -> openShiftDelegateService.processTemplatization(MANIFEST_DIRECTORY_PATH, OC_BINARY_PATH,
                               TEMPLATE_FILE_PATH, executionLogCallback, paramFileContent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Oc process command failed. Invalid parameter");
  }

  private void shouldGiveK8SReadyYaml() throws IOException {
    String OC_BINARY_PATH = "OC_BINARY_PATH";
    String TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH";
    String MANIFEST_DIRECTORY_PATH = ".";
    List<String> paramFileContent = Arrays.asList("a:b", "c:d");
    List<String> paramFilePaths = Arrays.asList("params-0", "params-1");
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    File ocResultYamlFile = new File("400-rest/src/test/resources/openshift/oc_process_result.yaml");
    File expectedYamlFile = new File("400-rest/src/test/resources/openshift/expected_parsed_result.yaml");

    CliResponse cliResponse = CliResponse.builder()
                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                  .output(FileUtils.readFileToString(ocResultYamlFile, "UTF-8"))
                                  .build();

    Mockito.doReturn(cliResponse)
        .when(openShiftClient)
        .process(OC_BINARY_PATH, TEMPLATE_FILE_PATH, paramFilePaths, MANIFEST_DIRECTORY_PATH, executionLogCallback);

    List<FileData> manifestFiles = openShiftDelegateService.processTemplatization(
        MANIFEST_DIRECTORY_PATH, OC_BINARY_PATH, TEMPLATE_FILE_PATH, executionLogCallback, paramFileContent);

    assertThat(manifestFiles).hasSize(1);
    assertThat(manifestFiles.get(0).getFileContent()).isEqualTo(FileUtils.readFileToString(expectedYamlFile, "UTF-8"));
  }
}
