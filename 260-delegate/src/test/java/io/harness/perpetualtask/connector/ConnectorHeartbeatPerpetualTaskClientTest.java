package io.harness.perpetualtask.connector;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.security.NGSecretManagerService;
import software.wings.service.intfc.security.NGSecretService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class ConnectorHeartbeatPerpetualTaskClientTest extends WingsBaseTest {
  @Inject private KryoSerializer kryoSerializer;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private NGSecretService ngSecretService;
  @Mock private NGSecretManagerService ngSecretManagerService;
  @Mock private Call<ResponseDTO<ConnectorValidationParams>> call;
  @InjectMocks ConnectorHeartbeatPerpetualTaskClient connectorHeartbeatPerpetualTaskClient;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  PerpetualTaskClientContext perpetualTaskClientContext;

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    when(connectorResourceClient.getConnectorValidationParams(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(call);
    when(ngSecretManagerService.get(anyString(), anyString(), anyString(), anyString(), eq(false)))
        .thenReturn(Optional.empty());
    ScmValidationParams gitValidationParameters =
        ScmValidationParams.builder().gitConfigDTO(GitConfigDTO.builder().build()).build();
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(gitValidationParameters)));
    FieldUtils.writeField(connectorHeartbeatPerpetualTaskClient, "kryoSerializer", kryoSerializer, true);
    Map<String, String> connectorDetails = ImmutableMap.of(ACCOUNT_KEY, accountIdentifier, ORG_KEY, orgIdentifier,
        PROJECT_KEY, projectIdentifier, CONNECTOR_IDENTIFIER_KEY, identifier);
    perpetualTaskClientContext = PerpetualTaskClientContext.builder().clientParams(connectorDetails).build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getTaskParams() {
    ConnectorHeartbeatTaskParams taskParams =
        (ConnectorHeartbeatTaskParams) connectorHeartbeatPerpetualTaskClient.getTaskParams(perpetualTaskClientContext);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getAccountIdentifier()).isEqualTo(accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getValidationTask() {
    DelegateTask delegateTask =
        connectorHeartbeatPerpetualTaskClient.getValidationTask(perpetualTaskClientContext, accountIdentifier);
    assertThat(delegateTask).isNotNull();
  }
}