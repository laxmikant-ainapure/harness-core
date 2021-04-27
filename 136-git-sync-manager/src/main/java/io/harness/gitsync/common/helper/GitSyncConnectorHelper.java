package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.tasks.DecryptGitApiAccessHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
@OwnedBy(DX)
@Slf4j
public class GitSyncConnectorHelper {
  ConnectorService connectorService;
  DecryptGitApiAccessHelper decryptGitApiAccessHelper;

  @Inject
  public GitSyncConnectorHelper(@Named("connectorDecoratorService") ConnectorService connectorService,
      DecryptGitApiAccessHelper decryptGitApiAccessHelper) {
    this.connectorService = connectorService;
    this.decryptGitApiAccessHelper = decryptGitApiAccessHelper;
  }

  public ScmConnector getDecryptedConnector(YamlGitConfigDTO gitSyncConfigDTO, String accountId) {
    final String connectorRef = gitSyncConfigDTO.getGitConnectorRef();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(accountId, identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (connectorDTO.isPresent()) {
      ConnectorInfoDTO connector = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfig = connector.getConnectorConfig();
      if (connectorConfig instanceof ScmConnector) {
        ScmConnector gitConnectorConfig = (ScmConnector) connector.getConnectorConfig();
        return decryptGitApiAccessHelper.decryptScmApiAccess(gitConnectorConfig, accountId,
            gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier());
      }
      throw new UnexpectedException(
          String.format("The connector with thhe  id %s, accountId %s, orgId %s, projectId %s is not a scm connector",
              gitSyncConfigDTO.getIdentifier(), accountId, gitSyncConfigDTO.getOrganizationIdentifier(),
              gitSyncConfigDTO.getProjectIdentifier()));
    } else {
      throw new UnexpectedException(String.format(
          "No connector found with the id %s, accountId %s, orgId %s, projectId %s", gitSyncConfigDTO.getIdentifier(),
          accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier()));
    }
  }

  public void validateTheAPIAccessPresence(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      checkAPIAccessFieldPresence((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      checkAPIAccessFieldPresence((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      checkAPIAccessFieldPresence((BitbucketConnectorDTO) scmConnector);
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  private void checkAPIAccessFieldPresence(GithubConnectorDTO githubConnectorDTO) {
    GithubApiAccessDTO apiAccess = githubConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  private void checkAPIAccessFieldPresence(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabApiAccessDTO apiAccess = gitlabConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  private void checkAPIAccessFieldPresence(BitbucketConnectorDTO bitbucketConnectorDTO) {
    BitbucketApiAccessDTO apiAccess = bitbucketConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }
}
