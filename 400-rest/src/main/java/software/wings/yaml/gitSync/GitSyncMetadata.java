package software.wings.yaml.gitSync;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "GitSyncMetadataKeys")
public class GitSyncMetadata {
  String gitConnectorId;
  private String repositoryName;
  String branchName;
  String yamlGitConfigId;
}
