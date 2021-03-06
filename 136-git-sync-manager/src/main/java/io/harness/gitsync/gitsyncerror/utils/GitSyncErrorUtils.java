package io.harness.gitsync.gitsyncerror.utils;

import static io.harness.gitsync.gitsyncerror.beans.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;

import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitToHarnessErrorDetails;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class GitSyncErrorUtils {
  public static final String EMPTY_STR = "";
  public static final Long DEFAULT_COMMIT_TIME = 0L;

  public static boolean isGitToHarnessSyncError(GitSyncError gitSyncError) {
    return GIT_TO_HARNESS == gitSyncError.getGitSyncDirection();
  }

  public static String getCommitIdOfError(GitSyncError error) {
    if (isGitToHarnessSyncError(error)) {
      GitToHarnessErrorDetails gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
      return gitToHarnessErrorDetails.getGitCommitId();
    }
    log.warn("The commitId is specific to the git to harness error, it should not be called for harness to git");
    return EMPTY_STR;
  }

  public static String getYamlContentOfError(GitSyncError error) {
    if (isGitToHarnessSyncError(error)) {
      GitToHarnessErrorDetails gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
      return gitToHarnessErrorDetails.getYamlContent();
    }
    log.warn("The yaml content is specific to the git to harness error, it should not be called for harness to git");
    return EMPTY_STR;
  }

  public static Long getCommitTimeOfError(GitSyncError error) {
    if (isGitToHarnessSyncError(error)) {
      GitToHarnessErrorDetails gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
      return gitToHarnessErrorDetails.getCommitTime();
    }

    log.warn("The commitTime is specific to the git to harness error, it should not be called for harness to git");
    return DEFAULT_COMMIT_TIME;
  }

  public static String getCommitMessageOfError(GitSyncError error) {
    if (isGitToHarnessSyncError(error)) {
      GitToHarnessErrorDetails gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
      return gitToHarnessErrorDetails.getCommitMessage();
    }

    log.warn("The commitMessage is specific to the git to harness error, it should not be called for harness to git");
    return EMPTY_STR;
  }

  public static void setYamlContent(GitSyncError error) {
    if (isGitToHarnessSyncError(error)) {
      GitToHarnessErrorDetails gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
      gitToHarnessErrorDetails.setYamlContent(null);
    }
  }
}
