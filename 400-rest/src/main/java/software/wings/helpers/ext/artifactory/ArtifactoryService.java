package software.wings.helpers.ext.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by sgurubelli on 6/27/17.
 */
@OwnedBy(CDC)
public interface ArtifactoryService {
  List<BuildDetails> getBuilds(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds);

  /**
   *
   * @param artifactoryConfig
   * @param encryptionDetails
   * @param repositoryName
   * @param artifactPath
   * @param repositoryType
   * @param maxVersions
   * @return
   */
  List<BuildDetails> getFilePaths(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails,
      String repositoryName, String artifactPath, String repositoryType, int maxVersions);

  /**
   * Get Repositories
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * Get Repositories
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, ArtifactType artifactType);

  /**
   * Get Repositories
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, String packageType);

  /**
   * Get Repositories
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, RepositoryType repositoryType);
  /***
   * Get docker tags
   * @param artifactoryConfig the Artifactory Config
   * @param repoKey
   * @return List of Repo paths or docker images
   */
  List<String> getRepoPaths(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, String repoKey);

  /**
   * Download artifacts
   * @param repoType
   * @return Input stream
   */
  ListNotifyResponseData downloadArtifacts(ArtifactoryConfig artifactoryConfig,
      List<EncryptedDataDetail> encryptionDetails, String repoType, Map<String, String> metadata, String delegateId,
      String taskId, String accountId);

  Pair<String, InputStream> downloadArtifact(ArtifactoryConfig artifactoryConfig,
      List<EncryptedDataDetail> encryptionDetails, String repositoryName, Map<String, String> metadata);

  boolean validateArtifactPath(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails,
      String repoType, String artifactPath, String repositoryType);

  Long getFileSize(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata);

  boolean isRunning(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);
}
