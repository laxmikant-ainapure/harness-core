package io.harness.cdng.artifact.resources.docker.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.docker.mappers.DockerResourceMapper;
import io.harness.connector.apis.dto.ConnectorInfoDTO;
import io.harness.connector.apis.dto.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import software.wings.beans.TaskType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Singleton
public class DockerResourceServiceImpl implements DockerResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public DockerResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public DockerResponseDTO getBuildDetails(
      IdentifierRef dockerConnectorRef, String imagePath, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(dockerConnectorRef.getAccountId(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = DockerArtifactDelegateRequest.builder()
                                                      .dockerConnectorDTO(connector)
                                                      .encryptedDataDetails(encryptionDetails)
                                                      .imagePath(imagePath)
                                                      .sourceType(ArtifactSourceType.DOCKER_HUB)
                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        dockerRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess, "Docker Get Builds task failure due to error");
    return getDockerResponseDTO(artifactTaskExecutionResponse);
  }

  @Override
  public DockerResponseDTO getLabels(IdentifierRef dockerConnectorRef, String imagePath,
      DockerRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(dockerConnectorRef.getAccountId(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = DockerArtifactDelegateRequest.builder()
                                                      .dockerConnectorDTO(connector)
                                                      .encryptedDataDetails(encryptionDetails)
                                                      .tagsList(dockerRequestDTO.getTagsList())
                                                      .imagePath(imagePath)
                                                      .sourceType(ArtifactSourceType.DOCKER_HUB)
                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        dockerRequest, ArtifactTaskType.GET_LABELS, baseNGAccess, "Docker Get labels task failure due to error");
    return getDockerResponseDTO(artifactTaskExecutionResponse);
  }

  @Override
  public DockerBuildDetailsDTO getSuccessfulBuild(IdentifierRef dockerConnectorRef, String imagePath,
      DockerRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(dockerConnectorRef.getAccountId(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = DockerArtifactDelegateRequest.builder()
                                                      .dockerConnectorDTO(connector)
                                                      .encryptedDataDetails(encryptionDetails)
                                                      .tag(dockerRequestDTO.getTag())
                                                      .tagRegex(dockerRequestDTO.getTagRegex())
                                                      .imagePath(imagePath)
                                                      .sourceType(ArtifactSourceType.DOCKER_HUB)
                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Docker Get last successful build task failure due to error");
    DockerResponseDTO dockerResponseDTO = getDockerResponseDTO(artifactTaskExecutionResponse);
    if (dockerResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactServerException("Docker get last successful build task failure.");
    }
    return dockerResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(dockerConnectorRef.getAccountId(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = DockerArtifactDelegateRequest.builder()
                                                      .dockerConnectorDTO(connector)
                                                      .encryptedDataDetails(encryptionDetails)
                                                      .sourceType(ArtifactSourceType.DOCKER_HUB)
                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Docker validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public boolean validateArtifactSource(
      String imagePath, IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(dockerConnectorRef.getAccountId(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = DockerArtifactDelegateRequest.builder()
                                                      .dockerConnectorDTO(connector)
                                                      .encryptedDataDetails(encryptionDetails)
                                                      .imagePath(imagePath)
                                                      .sourceType(ArtifactSourceType.DOCKER_HUB)
                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE, baseNGAccess,
            "Docker validate artifact source task failure due to error");
    return artifactTaskExecutionResponse.isArtifactSourceValid();
  }

  private DockerConnectorDTO getConnector(IdentifierRef dockerConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(dockerConnectorRef.getAccountId(), dockerConnectorRef.getOrgIdentifier(),
            dockerConnectorRef.getProjectIdentifier(), dockerConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isADockerConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            dockerConnectorRef.getIdentifier(), dockerConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (DockerConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isADockerConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.DOCKER == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull DockerConnectorDTO dockerConnectorDTO, @Nonnull NGAccess ngAccess) {
    return secretManagerClientService.getEncryptionDetails(ngAccess, dockerConnectorDTO.getAuth().getCredentials());
  }

  private ArtifactTaskExecutionResponse executeSyncTask(DockerArtifactDelegateRequest dockerRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, dockerRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, DockerArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(TaskType.DOCKER_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
            .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
            .build();
    return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private DockerResponseDTO getDockerResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<DockerArtifactDelegateResponse> dockerArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (DockerArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return DockerResourceMapper.toDockerResponse(dockerArtifactDelegateResponses);
  }
}
