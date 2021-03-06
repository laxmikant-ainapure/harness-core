package software.wings.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;

import static java.util.Collections.singletonList;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ConnectionMode;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.managerclient.AccountPreference;
import io.harness.managerclient.AccountPreferenceQuery;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;
import io.harness.manifest.ManifestCollectionResponseHandler;
import io.harness.perpetualtask.connector.ConnectorHearbeatPublisher;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.Account;
import software.wings.beans.AccountPreferences;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsExceptionMapper;
import software.wings.helpers.ext.pcf.response.PcfCommandExecutionResponse;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.ratelimit.DelegateRequestRateLimiter;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;
import software.wings.utils.ResourceTestRule;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.RequestBody;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;

@RunWith(Parameterized.class)
@Slf4j
public class DelegateAgentResourceTest {
  private static final DelegateService delegateService = mock(DelegateService.class);
  private static final ConfigurationController configurationController = mock(ConfigurationController.class);

  private static final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

  private static final AccountService accountService = mock(AccountService.class);
  private static final WingsPersistence wingsPersistence = mock(WingsPersistence.class);
  private static final DelegateRequestRateLimiter delegateRequestRateLimiter = mock(DelegateRequestRateLimiter.class);
  private static final SubdomainUrlHelperIntfc subdomainUrlHelper = mock(SubdomainUrlHelperIntfc.class);
  private static final InstanceHelper instanceSyncResponseHandler = mock(InstanceHelper.class);
  private static final ArtifactCollectionResponseHandler artifactCollectionResponseHandler;
  private static final DelegateTaskService delegateTaskService = mock(DelegateTaskService.class);

  static {
    artifactCollectionResponseHandler = mock(ArtifactCollectionResponseHandler.class);
  }
  private static final ManifestCollectionResponseHandler manifestCollectionResponseHandler =
      mock(ManifestCollectionResponseHandler.class);
  private static final ConnectorHearbeatPublisher connectorHearbeatPublisher = mock(ConnectorHearbeatPublisher.class);
  private static final KryoSerializer kryoSerializer = mock(KryoSerializer.class);

  @Parameter public String apiUrl;

  @Parameters
  public static String[] data() {
    return new String[] {null, "https://testUrl"};
  }

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new DelegateAgentResource(delegateService, accountService, wingsPersistence,
              delegateRequestRateLimiter, subdomainUrlHelper, artifactCollectionResponseHandler,
              instanceSyncResponseHandler, manifestCollectionResponseHandler, connectorHearbeatPublisher,
              kryoSerializer, configurationController))
          .instance(new AbstractBinder() {
            @Override
            protected void configure() {
              bind(httpServletRequest).to(HttpServletRequest.class);
            }
          })
          .type(WingsExceptionMapper.class)
          .build();

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetDelegateConfiguration() {
    List<String> delegateVersions = new ArrayList<>();
    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(delegateVersions).build();
    when(accountService.getDelegateConfiguration(ACCOUNT_ID)).thenReturn(delegateConfiguration);
    RestResponse<DelegateConfiguration> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/configuration?accountId=" + ACCOUNT_ID)
            .request()
            .get(new GenericType<RestResponse<DelegateConfiguration>>() {});

    verify(accountService, atLeastOnce()).getDelegateConfiguration(ACCOUNT_ID);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateConfiguration.class).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetConnectionHeartbeat() {
    DelegateConnectionHeartbeat delegateConnectionHeartbeat = DelegateConnectionHeartbeat.builder().build();
    RESOURCES.client()
        .target("/agent/delegates/connectionHeartbeat/" + DELEGATE_ID + "?delegateId=" + DELEGATE_ID
            + "&accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(delegateConnectionHeartbeat, MediaType.APPLICATION_JSON),
            new GenericType<RestResponse<String>>() {});
    verify(delegateService, atLeastOnce())
        .registerHeartbeat(ACCOUNT_ID, DELEGATE_ID, delegateConnectionHeartbeat, ConnectionMode.POLLING);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldClearCache() {
    Delegate delegate = Delegate.builder().build();
    when(delegateService.update(any(Delegate.class)))
        .thenAnswer(invocation -> invocation.getArgumentAt(0, Delegate.class));

    RESOURCES.client()
        .target(
            "/agent/delegates/" + DELEGATE_ID + "/clear-cache?delegateId=" + DELEGATE_ID + "&accountId=" + ACCOUNT_ID)
        .request()
        .put(entity(delegate, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Delegate>>() {});
    verify(delegateService, atLeastOnce()).clearCache(ACCOUNT_ID, DELEGATE_ID);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldRegisterDelegate() {
    DelegateRegisterResponse registerResponse = DelegateRegisterResponse.builder().delegateId(ID_KEY).build();
    when(delegateService.register(any(DelegateParams.class))).thenReturn(registerResponse);
    RestResponse<DelegateRegisterResponse> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/register?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(DelegateParams.builder().delegateId(ID_KEY).build(), MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateRegisterResponse>>() {});

    DelegateRegisterResponse resourceResponse = restResponse.getResource();
    assertThat(registerResponse).isEqualTo(resourceResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldAddDelegate() {
    Delegate delegate = Delegate.builder().build();

    when(delegateService.add(any(Delegate.class)))
        .thenAnswer(invocation -> invocation.getArgumentAt(0, Delegate.class));
    RestResponse<Delegate> restResponse =
        RESOURCES.client()
            .target("/agent/delegates?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegate, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Delegate>>() {});

    ArgumentCaptor<Delegate> captor = ArgumentCaptor.forClass(Delegate.class);
    verify(delegateService, atLeastOnce()).add(captor.capture());
    Delegate captorValue = captor.getValue();
    assertThat(captorValue.getAccountId()).isEqualTo(ACCOUNT_ID);
    Delegate resource = restResponse.getResource();
    assertThat(resource.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldProcessArtifactCollection() {
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .buildSourceResponse(BuildSourceResponse.builder().build())
            .build();

    when(kryoSerializer.asObject(any(byte[].class))).thenReturn(buildSourceExecutionResponse);

    RequestBody requestBody = RequestBody.create(okhttp3.MediaType.parse("application/octet-stream"), "");

    RESOURCES.client()
        .target("/agent/delegates/artifact-collection/12345679?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(requestBody, "application/x-kryo"), new GenericType<RestResponse<Boolean>>() {});

    verify(artifactCollectionResponseHandler, atLeastOnce())
        .processArtifactCollectionResult(ACCOUNT_ID, "12345679", buildSourceExecutionResponse);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldProcessInstanceSyncResponse() {
    DelegateResponseData responseData = PcfCommandExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
    String perpetualTaskId = "12345679";

    RESOURCES.client()
        .target("/agent/delegates/instance-sync/12345679?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(responseData, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});

    verify(instanceSyncResponseHandler, atLeastOnce())
        .processInstanceSyncResponseFromPerpetualTask(perpetualTaskId, responseData);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldGetDelegateTaskEvents() {
    List<DelegateTaskEvent> delegateTaskEventList =
        singletonList(DelegateTaskEventBuilder.aDelegateTaskEvent().build());
    when(delegateService.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false)).thenReturn(delegateTaskEventList);
    List<DelegateTaskEvent> restResponse = RESOURCES.client()
                                               .target("/agent/delegates/" + DELEGATE_ID + "/task-events?delegateId="
                                                   + DELEGATE_ID + "&accountId=" + ACCOUNT_ID + "&syncOnly=" + false)
                                               .request()
                                               .get(new GenericType<List<DelegateTaskEvent>>() {});

    verify(delegateService, atLeastOnce()).getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false);
    assertThat(restResponse).isInstanceOf(List.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldCheckForUpgrade() throws IOException {
    String version = "0.0.0";
    String verificationUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":"
        + httpServletRequest.getServerPort();
    DelegateScripts delegateScripts = DelegateScripts.builder().build();
    when(delegateService.getDelegateScripts(
             ACCOUNT_ID, version, subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl))
        .thenReturn(delegateScripts);
    RestResponse<DelegateScripts> restResponse = RESOURCES.client()
                                                     .target("/agent/delegates/" + DELEGATE_ID + "/upgrade?delegateId="
                                                         + DELEGATE_ID + "&accountId=" + ACCOUNT_ID)
                                                     .request()
                                                     .header("Version", version)
                                                     .get(new GenericType<RestResponse<DelegateScripts>>() {});

    verify(delegateService, atLeastOnce())
        .getDelegateScripts(
            ACCOUNT_ID, version, subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateScripts.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateHB() {
    DelegateParams delegateParams = DelegateParams.builder().pollingModeEnabled(true).build();

    Delegate delegate = Delegate.builder().polllingModeEnabled(true).build();
    when(delegateService.updateHeartbeatForDelegateWithPollingEnabled(any(Delegate.class))).thenReturn(delegate);
    RestResponse<Delegate> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/heartbeat-with-polling?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegateParams, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Delegate>>() {});
    verify(delegateService, atLeastOnce()).updateHeartbeatForDelegateWithPollingEnabled(delegate);
    assertThat(restResponse.getResource()).isInstanceOf(Delegate.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldUpdateECSDelegateHB() {
    String delegateType = "ECS";

    DelegateParams delegateParams =
        DelegateParams.builder().pollingModeEnabled(true).delegateType(delegateType).build();

    Delegate delegate = Delegate.builder().polllingModeEnabled(true).delegateType(delegateType).build();

    when(delegateService.handleEcsDelegateRequest(any(Delegate.class))).thenReturn(delegate);
    RestResponse<Delegate> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/heartbeat-with-polling?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(delegateParams, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Delegate>>() {});
    verify(delegateService, atLeastOnce()).handleEcsDelegateRequest(delegate);
    assertThat(restResponse.getResource()).isInstanceOf(Delegate.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldCheckForProfile() {
    DelegateProfileParams profileParams = DelegateProfileParams.builder().build();
    when(delegateService.checkForProfile(ACCOUNT_ID, DELEGATE_ID, "profile1", 99L)).thenReturn(profileParams);
    RestResponse<DelegateProfileParams> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/" + DELEGATE_ID + "/profile?accountId=" + ACCOUNT_ID
                + "&delegateId=" + DELEGATE_ID + "&profileId=profile1"
                + "&lastUpdatedAt=" + 99L)
            .request()
            .get(new GenericType<RestResponse<DelegateProfileParams>>() {});
    verify(delegateService, atLeastOnce()).checkForProfile(ACCOUNT_ID, DELEGATE_ID, "profile1", 99L);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateProfileParams.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldGetDelegateScripts() throws IOException {
    String delegateVersion = "0.0.0";
    String verificationUrl = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() + ":"
        + httpServletRequest.getServerPort();
    DelegateScripts delegateScripts = DelegateScripts.builder().build();
    when(delegateService.getDelegateScripts(ACCOUNT_ID, delegateVersion,
             subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl))
        .thenReturn(delegateScripts);

    RestResponse<DelegateScripts> restResponse =
        RESOURCES.client()
            .target("/agent/delegates/delegateScripts?accountId=" + ACCOUNT_ID + "&delegateVersion=" + delegateVersion)
            .request()
            .get(new GenericType<RestResponse<DelegateScripts>>() {});

    verify(delegateService, atLeastOnce())
        .getDelegateScripts(ACCOUNT_ID, delegateVersion,
            subdomainUrlHelper.getManagerUrl(httpServletRequest, ACCOUNT_ID), verificationUrl);
    assertThat(restResponse.getResource()).isInstanceOf(DelegateScripts.class).isNotNull();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldSaveApiCallLogs() {
    int numOfApiCallLogs = 10;
    List<ThirdPartyApiCallLog> apiCallLogs = new ArrayList<>();
    for (int i = 0; i < numOfApiCallLogs; i++) {
      apiCallLogs.add(ThirdPartyApiCallLog.builder()
                          .accountId(ACCOUNT_ID)
                          .stateExecutionId(STATE_EXECUTION_ID)
                          .requestTimeStamp(i)
                          .request(Lists.newArrayList(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                                          .name(generateUuid())
                                                          .value(generateUuid())
                                                          .type(ThirdPartyApiCallLog.FieldType.TEXT)
                                                          .build()))
                          .response(Lists.newArrayList(ThirdPartyApiCallLog.ThirdPartyApiCallField.builder()
                                                           .name(generateUuid())
                                                           .value(generateUuid())
                                                           .type(ThirdPartyApiCallLog.FieldType.TEXT)
                                                           .build()))
                          .delegateId(DELEGATE_ID)
                          .delegateTaskId(generateUuid())
                          .build());
    }

    when(kryoSerializer.asObject(any(byte[].class))).thenReturn(apiCallLogs);

    RESOURCES.client()
        .target("/agent/delegates/" + DELEGATE_ID + "/state-executions?delegateId=" + DELEGATE_ID
            + "&accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(apiCallLogs, MediaType.APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    verify(wingsPersistence, atLeastOnce()).save(apiCallLogs);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailed() {
    String taskId = generateUuid();
    RESOURCES.client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "/fail?accountId=" + ACCOUNT_ID)
        .request()
        .get(new GenericType<RestResponse<String>>() {});
    verify(delegateService, atLeastOnce()).failIfAllDelegatesFailed(ACCOUNT_ID, DELEGATE_ID, taskId);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  @Ignore("TODO: refactor to allow initialization of KryoFeature")
  public void shouldReportConnectionResults() {
    String taskId = generateUuid();
    List<DelegateConnectionResult> connectionResults = singletonList(DelegateConnectionResult.builder()
                                                                         .accountId(ACCOUNT_ID)
                                                                         .delegateId(DELEGATE_ID)
                                                                         .duration(100L)
                                                                         .criteria("aaa")
                                                                         .validated(true)
                                                                         .build());
    RESOURCES.client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "/report?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(connectionResults, "application/x-kryo"), new GenericType<RestResponse<DelegateTaskPackage>>() {});
    verify(delegateService, atLeastOnce()).reportConnectionResults(ACCOUNT_ID, DELEGATE_ID, taskId, connectionResults);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  @Ignore("TODO: refactor to allow initialization of KryoFeature")
  public void shouldUpdateTaskResponse() {
    String taskId = generateUuid();
    DelegateTaskResponse taskResponse = DelegateTaskResponse.builder().build();
    RESOURCES.client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "?accountId=" + ACCOUNT_ID)
        .request()
        .post(entity(taskResponse, "application/x-kryo"), new GenericType<RestResponse<String>>() {});
    verify(delegateTaskService, atLeastOnce()).processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, taskId, taskResponse);
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldAcquireDelegateTask() {
    String taskId = generateUuid();
    DelegateTaskResponse taskResponse = DelegateTaskResponse.builder().build();
    RESOURCES.client()
        .target("/agent/delegates/" + DELEGATE_ID + "/tasks/" + taskId + "/acquire?accountId=" + ACCOUNT_ID)
        .request()
        .put(entity(taskResponse, "application/x-kryo"), Response.class);
    verify(delegateService, atLeastOnce()).acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, taskId);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void shouldPublishNGConnectorHeartbeatResult() {
    String taskId = generateUuid();
    ConnectorHeartbeatDelegateResponse taskResponse =
        ConnectorHeartbeatDelegateResponse.builder().accountIdentifier(ACCOUNT_ID).build();
    RestResponse<Boolean> response =
        RESOURCES.client()
            .target("/agent/delegates/connectors/" + taskId + "?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(taskResponse, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(connectorHearbeatPublisher, atLeastOnce()).pushConnectivityCheckActivity(ACCOUNT_ID, taskResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldGetDelegateProperties() throws TextFormat.ParseException {
    GetDelegatePropertiesRequest request = GetDelegatePropertiesRequest.newBuilder()
                                               .setAccountId(ACCOUNT_ID)
                                               .addRequestEntry(Any.pack(AccountPreferenceQuery.newBuilder().build()))
                                               .build();

    Account account =
        Account.Builder.anAccount()
            .withUuid(ACCOUNT_ID)
            .withAccountPreferences(AccountPreferences.builder().delegateSecretsCacheTTLInHours(new Integer(2)).build())
            .build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    RestResponse<String> restResponse = RESOURCES.client()
                                            .target("/agent/delegates/properties?accountId=" + ACCOUNT_ID)
                                            .request()
                                            .post(entity(request.toByteArray(), MediaType.APPLICATION_OCTET_STREAM),
                                                new GenericType<RestResponse<String>>() {});
    GetDelegatePropertiesResponse responseProto =
        TextFormat.parse(restResponse.getResource(), GetDelegatePropertiesResponse.class);
    assertThat(responseProto).isNotNull();
    assertThat(responseProto.getResponseEntryList().get(0).is(AccountPreference.class)).isTrue();
  }
}
