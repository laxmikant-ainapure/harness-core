package software.wings.utils;
import static java.lang.String.format;

import io.harness.exception.FileReadException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class GitUtilsDelegate {
  private static final String USER_DIR_KEY = "user.dir";

  @Inject private EncryptionService encryptionService;
  @Inject private GitClientHelper gitClientHelper;
  @Inject private GitClient gitClient;

  public String getRequestDataFromFile(String path) {
    Path jsonPath = Paths.get(path);
    try {
      List<String> data = Files.readAllLines(jsonPath);
      return String.join("\n", data);
    } catch (IOException ex) {
      throw new FileReadException(format("Could not read %s at given branch/commitId", path));
    }
  }

  public GitOperationContext cloneRepo(
      GitConfig gitConfig, GitFileConfig gitFileConfig, List<EncryptedDataDetail> sourceRepoEncryptionDetails) {
    GitOperationContext gitOperationContext =
        GitOperationContext.builder().gitConfig(gitConfig).gitConnectorId(gitFileConfig.getConnectorId()).build();
    encryptionService.decrypt(gitConfig, sourceRepoEncryptionDetails, false);
    gitClient.ensureRepoLocallyClonedAndUpdated(gitOperationContext);
    return gitOperationContext;
  }

  public String resolveAbsoluteFilePath(GitOperationContext gitOperationContext, String scriptPath) {
    return Paths
        .get(Paths.get(System.getProperty(USER_DIR_KEY)).toString(),
            gitClientHelper.getRepoDirectory(gitOperationContext), scriptPath)
        .toString();
  }

  public String resolveAbsoluteRepoPath(GitOperationContext gitOperationContext) {
    return resolveAbsoluteFilePath(gitOperationContext, StringUtils.EMPTY);
  }
}
