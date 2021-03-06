package io.harness.stateutils.buildstate;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveSecretRefWithDefaultValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SecretUtils {
  private final SecretNGManagerClient secretNGManagerClient;
  private final SecretManagerClientService secretManagerClientService;

  @Inject
  public SecretUtils(
      SecretNGManagerClient secretNGManagerClient, SecretManagerClientService secretManagerClientService) {
    this.secretNGManagerClient = secretNGManagerClient;
    this.secretManagerClientService = secretManagerClientService;
  }

  public SecretVariableDetails getSecretVariableDetails(NGAccess ngAccess, SecretNGVariable secretVariable) {
    SecretRefData secretRefData =
        resolveSecretRefWithDefaultValue("Variables", "stage", "stageIdentifier", secretVariable.getValue(), false);
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretRefData == null && secretVariable.getDefaultValue() != null) {
      secretIdentifier = secretVariable.getDefaultValue();
    }

    if (secretIdentifier == null || secretRefData == null) {
      log.warn("Failed to resolve secret variable " + secretVariable.getName());
      return null;
    }

    log.info("Getting secret variable details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretVariableDTO.Type secretType = getSecretType(getSecret(identifierRef).getType());
    SecretVariableDTO secret =
        SecretVariableDTO.builder().name(secretVariable.getName()).secret(secretRefData).type(secretType).build();
    log.info("Getting secret variable encryption details for secret type:[{}] ref:[{}]", secretType, secretIdentifier);
    List<EncryptedDataDetail> encryptionDetails = secretManagerClientService.getEncryptionDetails(ngAccess, secret);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SecretVariableDetails.builder().encryptedDataDetailList(encryptionDetails).secretVariableDTO(secret).build();
  }

  public SecretVariableDetails getSecretVariableDetailsWithScope(NGAccess ngAccess, SecretNGVariable secretVariable) {
    SecretRefData secretRefData =
        resolveSecretRefWithDefaultValue("Variables", "stage", "stageIdentifier", secretVariable.getValue(), false);
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretRefData == null && secretVariable.getDefaultValue() != null) {
      secretIdentifier = secretVariable.getDefaultValue();
    }

    if (secretIdentifier == null || secretRefData == null) {
      log.warn("Failed to resolve secret variable " + secretVariable.getName());
      return null;
    }

    log.info("Getting secret variable details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretVariableDTO.Type secretType = getSecretType(getSecret(identifierRef).getType());
    SecretVariableDTO secret =
        SecretVariableDTO.builder()
            .name("HARNESS"
                + "_" + identifierRef.getScope().getYamlRepresentation() + "_" + secretVariable.getName())
            .secret(secretRefData)
            .type(secretType)
            .build();

    log.info("Getting secret variable encryption details for secret type:[{}] ref:[{}]", secretType, secretIdentifier);
    List<EncryptedDataDetail> encryptionDetails = secretManagerClientService.getEncryptionDetails(ngAccess, secret);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SecretVariableDetails.builder().encryptedDataDetailList(encryptionDetails).secretVariableDTO(secret).build();
  }

  private SecretVariableDTO.Type getSecretType(SecretType type) {
    switch (type) {
      case SecretFile:
        return SecretVariableDTO.Type.FILE;
      case SecretText:
        return SecretVariableDTO.Type.TEXT;
      default:
        throw new InvalidArgumentsException(format("Unsupported secret type [%s]", type), WingsException.USER);
    }
  }

  public SSHKeyDetails getSshKey(NGAccess ngAccess, SecretRefData secretRefData) {
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretIdentifier == null) {
      log.warn("Failed to resolve secret variable, secretIdentifier is null");
      return null;
    }

    log.info("Getting ssh key details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretDTOV2 secretDTOV2 = getSecret(identifierRef);
    if (secretDTOV2.getType() != SecretType.SSHKey) {
      throw new CIStageExecutionUserException(format("Secret id:[%s] type should be SSHKey", secretIdentifier));
    }
    SSHKeySpecDTO spec = (SSHKeySpecDTO) secretDTOV2.getSpec();
    if (spec.getAuth().getAuthScheme() != SSHAuthScheme.SSH) {
      throw new CIStageExecutionUserException(
          format("Secret id:[%s] auth scheme type should be SSH", secretIdentifier));
    }
    SSHConfigDTO sshConfigDTO = (SSHConfigDTO) spec.getAuth().getSpec();
    if (sshConfigDTO.getCredentialType() != SSHCredentialType.KeyReference) {
      throw new CIStageExecutionUserException(
          format("Secret id:[%s] credential type should be KeyReference", secretIdentifier));
    }
    SSHKeyReferenceCredentialDTO credentialSpecDTO = (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();

    log.info(
        "Getting secret encryption details for secret type:[{}] ref:[{}]", secretDTOV2.getType(), secretIdentifier);
    List<EncryptedDataDetail> encryptionDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, credentialSpecDTO);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SSHKeyDetails.builder().encryptedDataDetails(encryptionDetails).sshKeyReference(credentialSpecDTO).build();
  }

  private SecretDTOV2 getSecret(IdentifierRef identifierRef) {
    SecretResponseWrapper secretResponseWrapper;
    try {
      secretResponseWrapper = SafeHttpCall
                                  .execute(secretNGManagerClient.getSecret(identifierRef.getIdentifier(),
                                      identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
                                      identifierRef.getProjectIdentifier()))
                                  .getData();

    } catch (IOException e) {
      log.error(format("Unable to get secret information : [%s] with scope: [%s]", identifierRef.getIdentifier(),
                    identifierRef.getScope()),
          e);

      throw new CIStageExecutionException(format("Unable to get secret information : [%s] with scope: [%s]",
          identifierRef.getIdentifier(), identifierRef.getScope()));
    }

    if (secretResponseWrapper == null) {
      throw new CIStageExecutionUserException(format("Secret not found for identifier : [%s] with scope: [%s]",
          identifierRef.getIdentifier(), identifierRef.getScope()));
    }
    return secretResponseWrapper.getSecret();
  }
}
