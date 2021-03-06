package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GitFileConfigHelperServiceTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;

  @InjectMocks @Inject GitFileConfigHelperService configHelperService;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGitFileConfigFromYaml() {
    SettingAttribute setting = aSettingAttribute().withUuid("uuid").build();
    GitFileConfig gitFileConfig = getBaseGitFileConfig();

    doReturn(setting).when(settingsService).getByName(ACCOUNT_ID, APP_ID, gitFileConfig.getConnectorName());
    GitFileConfig newGitFileConfig = configHelperService.getGitFileConfigFromYaml(ACCOUNT_ID, APP_ID, gitFileConfig);
    assertThat(newGitFileConfig.getConnectorId()).isEqualTo(setting.getUuid());
    assertThat(newGitFileConfig.getConnectorName()).isNull();
    assertGitFileConfigTheSame(gitFileConfig, newGitFileConfig);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGitFileConfigForToYaml() {
    SettingAttribute setting = aSettingAttribute().withName("name").build();
    GitFileConfig gitFileConfig = getBaseGitFileConfig();

    doReturn(setting).when(settingsService).get(gitFileConfig.getConnectorId());
    GitFileConfig newGitFileConfig = configHelperService.getGitFileConfigForToYaml(gitFileConfig);
    assertThat(newGitFileConfig.getConnectorId()).isNull();
    assertThat(newGitFileConfig.getConnectorName()).isEqualTo(setting.getName());
    assertGitFileConfigTheSame(gitFileConfig, newGitFileConfig);
  }

  private GitFileConfig getBaseGitFileConfig() {
    return GitFileConfig.builder()
        .connectorName("gitConnector")
        .branch("master")
        .useBranch(true)
        .commitId("commitId")
        .filePath("filePath")
        .filePathList(asList("filePath1", "filePath2"))
        .build();
  }

  private void assertGitFileConfigTheSame(GitFileConfig oldConfig, GitFileConfig newConfig) {
    assertThat(newConfig.getBranch()).isEqualTo(oldConfig.getBranch());
    assertThat(newConfig.getCommitId()).isEqualTo(oldConfig.getCommitId());
    assertThat(newConfig.getFilePath()).isEqualTo(oldConfig.getFilePath());
    assertThat(newConfig.isUseBranch()).isEqualTo(oldConfig.isUseBranch());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGitFileConfigFromYamlWithNullSettingAttribute() {
    GitFileConfig gitFileConfig = getBaseGitFileConfig();
    doReturn(null).when(settingsService).get(ACCOUNT_ID, APP_ID, gitFileConfig.getConnectorName());
    assertThatThrownBy(() -> configHelperService.getGitFileConfigFromYaml(ACCOUNT_ID, APP_ID, gitFileConfig))
        .hasMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetGitFileConfigForToYamlWithNullSettingAttribute() {
    GitFileConfig gitFileConfig = getBaseGitFileConfig();
    doReturn(null).when(settingsService).get(gitFileConfig.getConnectorId());
    assertThatThrownBy(() -> configHelperService.getGitFileConfigForToYaml(gitFileConfig))
        .hasMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderGitFileConfig() {
    ExecutionContext context = mock(ExecutionContext.class);

    GitFileConfig gitFileConfig = GitFileConfig.builder()
                                      .branch("${expression}")
                                      .commitId("${expression}")
                                      .filePath("${expression}")
                                      .filePathList(asList("${expression}", "${expression}"))
                                      .build();

    doReturn("rendered").when(context).renderExpression("${expression}");
    configHelperService.renderGitFileConfig(context, gitFileConfig);

    verify(context, times(5)).renderExpression("${expression}");

    assertThat(gitFileConfig.getBranch()).isEqualTo("rendered");
    assertThat(gitFileConfig.getCommitId()).isEqualTo("rendered");
    assertThat(gitFileConfig.getFilePath()).isEqualTo("rendered");
    assertThat(gitFileConfig.getFilePathList()).containsExactly("rendered", "rendered");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidate() {
    GitFileConfig gitFileConfig = GitFileConfig.builder().branch("").filePath("").build();
    assertThatExceptionOfType(GeneralException.class).isThrownBy(() -> configHelperService.validate(null));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> configHelperService.validate(gitFileConfig))
        .withMessageContaining("Connector id cannot be empty.");

    String connectorId = "CONNECTOR_ID";
    gitFileConfig.setConnectorId(connectorId);
    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.REPO).build();
    doReturn(aSettingAttribute().withValue(gitConfig).build()).when(settingsService).get(connectorId);

    gitFileConfig.setUseBranch(true);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> configHelperService.validate(gitFileConfig))
        .withMessageContaining("Branch cannot be empty");

    gitFileConfig.setBranch("b1");
    configHelperService.validate(gitFileConfig);

    gitFileConfig.setUseBranch(false);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> configHelperService.validate(gitFileConfig))
        .withMessageContaining("CommitId cannot be empty");

    gitFileConfig.setCommitId("c1");
    configHelperService.validate(gitFileConfig);

    gitConfig.setUrlType(GitConfig.UrlType.ACCOUNT);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> configHelperService.validate(gitFileConfig))
        .withMessageContaining("Repository name not provided for Account level git connector.");

    gitFileConfig.setRepoName("repo1");
    configHelperService.validate(gitFileConfig);
  }
}
