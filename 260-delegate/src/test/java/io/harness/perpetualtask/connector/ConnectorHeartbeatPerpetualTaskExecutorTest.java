package io.harness.perpetualtask.connector;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.ConnectorValidationHandler;
import io.harness.delegate.task.k8s.KubernetesValidationHandler;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class ConnectorHeartbeatPerpetualTaskExecutorTest extends DelegateTest {
  @InjectMocks ConnectorHeartbeatPerpetualTaskExecutor connectorHeartbeatPerpetualTaskExecutor;
  @Inject KryoSerializer kryoSerializer;
  @Mock KubernetesValidationHandler KubernetesValidationHandler;
  @Mock DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Call<RestResponse<Boolean>> call;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(connectorHeartbeatPerpetualTaskExecutor, "kryoSerializer", kryoSerializer, true);
    doReturn(KubernetesValidationHandler).when(connectorTypeToConnectorValidationHandlerMap).get(Matchers.any());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishConnectorHeartbeatResult(anyString(), anyString(), any(ConnectorHeartbeatDelegateResponse.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category({UnitTests.class})
  public void runOnce() {
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(generateUuid()).build();
    when(KubernetesValidationHandler.validate(Matchers.any(), Matchers.any()))
        .thenReturn(ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build());
    connectorHeartbeatPerpetualTaskExecutor.runOnce(perpetualTaskId, getPerpetualTaskParams(), Instant.EPOCH);
    verify(delegateAgentManagerClient, times(1)).publishConnectorHeartbeatResult(anyString(), anyString(), any());
  }

  PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ConnectorDTO connectorDTO = ConnectorDTO.builder()
                                    .connectorInfo(ConnectorInfoDTO.builder()
                                                       .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                                                       .connectorConfig(KubernetesClusterConfigDTO.builder().build())
                                                       .build())
                                    .build();
    K8sValidationParams k8sValidationParams =
        K8sValidationParams.builder().kubernetesClusterConfigDTO(KubernetesClusterConfigDTO.builder().build()).build();
    ByteString connectorConfigBytes = ByteString.copyFrom(kryoSerializer.asBytes(k8sValidationParams));
    ConnectorHeartbeatTaskParams connectorHeartbeatTaskParams = ConnectorHeartbeatTaskParams.newBuilder()
                                                                    .setAccountIdentifier("accountIdentifier")
                                                                    .setConnectorIdentifier("connectorIdentifier")
                                                                    .setConnectorValidationParams(connectorConfigBytes)
                                                                    .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(connectorHeartbeatTaskParams))
        .build();
  }
}