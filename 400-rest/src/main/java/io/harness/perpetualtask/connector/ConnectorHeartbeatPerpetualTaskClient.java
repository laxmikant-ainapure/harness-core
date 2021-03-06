package io.harness.perpetualtask.connector;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsValidationParams;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.exception.UnexpectedException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.RestCallToNGManagerClientUtils;

import software.wings.beans.GcpKmsConfig;
import software.wings.beans.TaskType;
import software.wings.beans.VaultConfig;
import software.wings.service.intfc.security.NGSecretManagerService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class ConnectorHeartbeatPerpetualTaskClient implements PerpetualTaskServiceClient {
  private KryoSerializer kryoSerializer;
  private ConnectorResourceClient connectorResourceClient;
  private NGSecretManagerService ngSecretManagerService;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String accountIdentifier = clientParams.get(ACCOUNT_KEY);
    String orgIdentifier = clientParams.get(ORG_KEY);
    String projectIdentifier = clientParams.get(PROJECT_KEY);
    String connectorIdentifier = clientParams.get(CONNECTOR_IDENTIFIER_KEY);
    final ConnectorValidationParams connectorValidationParams = getConnectorValidationParams(clientParams);
    populateSecretManagerFields(
        connectorValidationParams, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    ByteString connectorValidatorBytes = ByteString.copyFrom(kryoSerializer.asBytes(connectorValidationParams));
    ConnectorHeartbeatTaskParams.Builder connectorHeartbeatTaskParamsBuilder =
        ConnectorHeartbeatTaskParams.newBuilder()
            .setAccountIdentifier(accountIdentifier)
            .setConnectorIdentifier(connectorIdentifier)
            .setConnectorValidationParams(connectorValidatorBytes);
    if (isNotBlank(orgIdentifier)) {
      connectorHeartbeatTaskParamsBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotBlank(projectIdentifier)) {
      connectorHeartbeatTaskParamsBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }
    return connectorHeartbeatTaskParamsBuilder.build();
  }

  private void populateSecretManagerFields(ConnectorValidationParams connectorValidationParams,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<SecretManagerConfig> secretManagerConfig =
        ngSecretManagerService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    if (secretManagerConfig.isPresent()) {
      switch (connectorValidationParams.getConnectorType()) {
        case VAULT:
          VaultConfig vaultConfig = (VaultConfig) secretManagerConfig.get();
          VaultConnectorDTO vaultConnectorDTO =
              ((VaultValidationParams) connectorValidationParams).getVaultConnectorDTO();
          vaultConnectorDTO.setAuthToken(vaultConfig.getAuthToken());
          vaultConnectorDTO.setAppRoleId(vaultConfig.getAppRoleId());
          vaultConnectorDTO.setSecretId(vaultConfig.getSecretId());
          return;
        case GCP_KMS:
          GcpKmsConfig gcpKmsConfig = (GcpKmsConfig) secretManagerConfig.get();
          GcpKmsConnectorDTO gcpKmsConnectorDTO =
              ((GcpKmsValidationParams) connectorValidationParams).getGcpKmsConnectorDTO();
          gcpKmsConnectorDTO.setCredentials(gcpKmsConfig.getCredentials());
          return;
        default:
      }
    }
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    Map<String, String> clientParams = clientContext.getClientParams();
    ConnectorValidationParams connectorValidationParams = getConnectorValidationParams(clientParams);
    List<ExecutionCapability> executionCapabilities = Collections.emptyList();
    if (connectorValidationParams instanceof ExecutionCapabilityDemander) {
      executionCapabilities =
          ((ExecutionCapabilityDemander) connectorValidationParams).fetchRequiredExecutionCapabilities(null);
    }
    return DelegateTask.builder()
        .accountId(accountId)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.CAPABILITY_VALIDATION.name())
                  .parameters(new Object[] {executionCapabilities})
                  .timeout(TimeUnit.MINUTES.toMillis(1))
                  .build())
        .build();
  }

  private ConnectorValidationParams getConnectorValidationParams(Map<String, String> clientParams) {
    String accountIdentifier = clientParams.get(ACCOUNT_KEY);
    String orgIdentifier = clientParams.get(ORG_KEY);
    String projectIdentifier = clientParams.get(PROJECT_KEY);
    String connectorIdentifier = clientParams.get(CONNECTOR_IDENTIFIER_KEY);
    ConnectorValidationParams connectorValidationParams =
        RestCallToNGManagerClientUtils.execute(connectorResourceClient.getConnectorValidationParams(
            connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    if (connectorValidationParams == null) {
      log.info(
          "{} The connector doesn't exists with the following ids accountId: {}, orgId: {}, projectId:{}, identifier:{}",
          CONNECTOR_HEARTBEAT_LOG_PREFIX, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      throw new UnexpectedException(String.format("The " + CONNECTOR_STRING + " doesn't exists.", connectorIdentifier,
          accountIdentifier, orgIdentifier, projectIdentifier));
    }
    return connectorValidationParams;
  }
}