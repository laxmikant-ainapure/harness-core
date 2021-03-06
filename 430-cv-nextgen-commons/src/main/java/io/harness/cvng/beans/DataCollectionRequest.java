package io.harness.cvng.beans;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsCapabilityHelper;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkCapabilityHelper;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public abstract class DataCollectionRequest<T extends ConnectorConfigDTO> implements ExecutionCapabilityDemander {
  private ConnectorInfoDTO connectorInfoDTO;

  public T getConnectorConfigDTO() {
    return (T) connectorInfoDTO.getConnectorConfig();
  }
  private String tracingId;
  private DataCollectionRequestType type;
  public abstract String getDSL();
  public abstract String getBaseUrl();
  public abstract Map<String, String> collectionHeaders();
  public Map<String, String> collectionParams() {
    return Collections.emptyMap();
  }

  public Map<String, Object> fetchDslEnvVariables() {
    return Collections.emptyMap();
  }

  public Instant getEndTime(Instant currentTime) {
    return currentTime;
  }
  public Instant getStartTime(Instant currentTime) {
    return currentTime.minus(Duration.ofMinutes(1));
  }
  protected static String readDSL(String fileName, Class clazz) {
    try {
      return Resources.toString(clazz.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    // TODO: this is a stop gap fix, we will be refactoring it once DX team works on their proposal
    switch (connectorInfoDTO.getConnectorType()) {
      case KUBERNETES_CLUSTER:
        return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
            (KubernetesClusterConfigDTO) connectorInfoDTO.getConnectorConfig(), maskingEvaluator);
      case APP_DYNAMICS:
        return AppDynamicsCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, (AppDynamicsConnectorDTO) connectorInfoDTO.getConnectorConfig());
      case SPLUNK:
        return SplunkCapabilityHelper.fetchRequiredExecutionCapabilities(
            maskingEvaluator, (SplunkConnectorDTO) connectorInfoDTO.getConnectorConfig());
      case GCP:
        return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "https://storage.cloud.google.com/", maskingEvaluator));
      default:
        throw new InvalidRequestException("Connector capability not found");
    }
  }
}
