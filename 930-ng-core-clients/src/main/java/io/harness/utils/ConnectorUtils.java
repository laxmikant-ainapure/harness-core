package io.harness.utils;

import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;

import static java.lang.String.format;

import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails.ConnectorDetailsBuilder;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * This was defined in CI module (320-ci-executions).
 * Instead of moving, cloning here, so can be re-used.
 * (Due to CI-beta release, not changing CI flow for now)
 */
@Singleton
@Slf4j
public class ConnectorUtils {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;

  @Inject
  public ConnectorUtils(
      ConnectorResourceClient connectorResourceClient, SecretManagerClientService secretManagerClientService) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretManagerClientService = secretManagerClientService;
  }

  public ConnectorDetails getConnectorDetails(NGAccess ngAccess, String connectorIdentifier) {
    log.info("Getting connector details for connector ref [{}]", connectorIdentifier);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifier,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    ConnectorDTO connectorDTO = getConnector(connectorRef);
    ConnectorType connectorType = connectorDTO.getConnectorInfo().getConnectorType();

    ConnectorDetailsBuilder connectorDetailsBuilder =
        ConnectorDetails.builder()
            .connectorType(connectorType)
            .connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig())
            .identifier(connectorDTO.getConnectorInfo().getIdentifier())
            .orgIdentifier(connectorDTO.getConnectorInfo().getOrgIdentifier())
            .projectIdentifier(connectorDTO.getConnectorInfo().getProjectIdentifier());

    log.info("Fetching encryption details for connector details for connector id:[{}] type:[{}]", connectorIdentifier,
        connectorType);
    ConnectorDetails connectorDetails;

    switch (connectorType) {
      case DOCKER:
        connectorDetails = getDockerConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case KUBERNETES_CLUSTER:
        connectorDetails = getK8sConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GIT:
        connectorDetails = getGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GITHUB:
        connectorDetails = getGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GITLAB:
        connectorDetails = getGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case BITBUCKET:
        connectorDetails = getGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GCP:
        connectorDetails = getGcpConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case AWS:
        connectorDetails = getAwsConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case ARTIFACTORY:
        connectorDetails = getArtifactoryConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      default:
        throw new InvalidArgumentsException(format("Unexpected connector type:[%s]", connectorType));
    }
    log.info(
        "Successfully fetched encryption details for  connector id:[{}] type:[{}]", connectorIdentifier, connectorType);
    return connectorDetails;
  }

  private ConnectorDetails getArtifactoryConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (artifactoryConnectorDTO.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO auth =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnectorDTO.getAuth().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, auth);
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    }
    throw new InvalidArgumentsException(format("Unsupported artifactory auth type:[%s] on connector:[%s]",
        artifactoryConnectorDTO.getAuth().getAuthType(), artifactoryConnectorDTO));
  }

  private ConnectorDetails getAwsConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    AwsCredentialDTO awsCredentialDTO = awsConnectorDTO.getCredential();
    if (awsCredentialDTO.getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) awsCredentialDTO.getConfig();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, awsManualConfigSpecDTO);
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else if (awsCredentialDTO.getAwsCredentialType() == AwsCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.build();
    }
    throw new InvalidArgumentsException(format("Unsupported aws credential type:[%s] on connector:[%s]",
        awsCredentialDTO.getAwsCredentialType(), awsConnectorDTO));
  }

  private ConnectorDetails getGcpConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    GcpConnectorCredentialDTO credential = gcpConnectorDTO.getCredential();
    if (credential.getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO credentialConfig = (GcpManualDetailsDTO) credential.getConfig();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, credentialConfig);
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else if (credential.getGcpCredentialType() == GcpCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.build();
    }
    throw new InvalidArgumentsException(format("Unsupported gcp credential type:[%s] on connector:[%s]",
        gcpConnectorDTO.getCredential().getGcpCredentialType(), gcpConnectorDTO));
  }

  private ConnectorDetails getGitConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    if (connectorDTO.getConnectorInfo().getConnectorType() == GITHUB) {
      return buildGithubConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == GITLAB) {
      return buildGitlabConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == BITBUCKET) {
      return buildBitBucketConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector " + connectorDTO.getConnectorInfo().getConnectorType());
    }
  }

  private ConnectorDetails buildGitlabConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
          (GitlabHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, gitlabHttpCredentialsDTO.getHttpCredentialsSpec());
      encryptedDataDetails.addAll(
          secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildGithubConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GithubHttpCredentialsDTO githubHttpCredentialsDTO =
          (GithubHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, githubHttpCredentialsDTO.getHttpCredentialsSpec());
      encryptedDataDetails.addAll(
          secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else {
      throw new InvalidArgumentsException(
          "Unsupported git connector auth: " + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildBitBucketConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
          (BitbucketHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
          ngAccess, bitbucketHttpCredentialsDTO.getHttpCredentialsSpec());
      encryptedDataDetails.addAll(
          secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));

      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else {
      throw new InvalidArgumentsException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails getDockerConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, dockerConnectorDTO.getAuth().getCredentials());
    return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
  }

  private ConnectorDetails getK8sConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        (KubernetesClusterConfigDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    KubernetesCredentialDTO config = kubernetesClusterConfigDTO.getCredential();
    if (config.getKubernetesCredentialType() == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesClusterDetailsDTO kubernetesCredentialSpecDTO = (KubernetesClusterDetailsDTO) config.getConfig();
      KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO = kubernetesCredentialSpecDTO.getAuth().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, kubernetesAuthCredentialDTO);
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else if (config.getKubernetesCredentialType() == KubernetesCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.build();
    }
    throw new InvalidArgumentsException(format("Unsupported gcp credential type:[%s] on connector:[%s]",
        kubernetesClusterConfigDTO.getCredential().getKubernetesCredentialType(), kubernetesClusterConfigDTO));
  }

  private ConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorDTO> connectorDTO;

    try {
      log.info("Fetching connector details for connector id:[{}] acc:[{}] project:[{}] org:[{}]",
          connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(), connectorRef.getProjectIdentifier(),
          connectorRef.getOrgIdentifier());

      connectorDTO =
          SafeHttpCall
              .execute(connectorResourceClient.get(connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(),
                  connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()))
              .getData();

    } catch (Exception e) {
      log.error(format("Unable to get connector information : [%s] with scope: [%s]", connectorRef.getIdentifier(),
          connectorRef.getScope()));
      throw new CIStageExecutionException(format("Unable to get connector information : [%s] with scope: [%s]",
                                              connectorRef.getIdentifier(), connectorRef.getScope()),
          e);
    }

    if (!connectorDTO.isPresent()) {
      throw NoResultFoundException.newBuilder()
          .message(format("Connector not found for identifier : [%s] with scope: [%s]", connectorRef.getIdentifier(),
              connectorRef.getScope()))
          .build();
    }
    return connectorDTO.get();
  }
}
