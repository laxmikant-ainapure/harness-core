package software.wings.service.intfc;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitFetchFilesResult;

import java.util.List;

public interface GitService {
  String validate(GitConfig gitConfig);

  void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext);

  GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch);

  GitFetchFilesResult fetchFilesBetweenCommits(
      GitConfig gitConfig, String newCommitId, String oldCommitId, String connectorId);

  GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, List<String> fileExtensions, boolean isRecursive);

  void downloadFiles(GitConfig gitConfig, GitFileConfig gitFileConfig, String destinationDirectory);

  GitCommitAndPushResult commitAndPush(GitOperationContext gitOperationContext);
}
