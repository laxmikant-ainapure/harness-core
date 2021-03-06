package software.wings.service.impl;

import static io.harness.beans.FeatureName.REVALIDATE_WHITELISTED_DELEGATE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NICOLAS;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_NAME;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateInitializationDetails;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.selection.log.BatchDelegateSelectionLog;
import io.harness.serializer.KryoSerializer;
import io.harness.service.dto.RetryDelegate;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.tasks.Cd1SetupFields;
import io.harness.version.VersionInfoManager;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.CEDelegateStatus;
import software.wings.beans.DelegateConnection;
import software.wings.beans.DelegateConnection.DelegateConnectionKeys;
import software.wings.beans.KmsConfig;
import software.wings.beans.TaskType;
import software.wings.beans.VaultConfig;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.states.HttpState.HttpStateExecutionResponse;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class DelegateServiceImplTest extends WingsBaseTest {
  private static final String HTTP_VAUTL_URL = "http://vautl.com";
  private static final String GOOGLE_COM = "http://google.com";
  private static final String US_EAST_2 = "us-east-2";
  private static final String AWS_KMS_URL = "https://kms.us-east-2.amazonaws.com";
  private static final String SECRET_URL = "http://google.com/?q=${secretManager.obtain(\"test\", 1234)}";

  private static final String VERSION = "1.0.0";
  private static final String TEST_SESSION_IDENTIFIER = generateUuid();
  private static final String TEST_DELEGATE_PROFILE_ID = generateUuid();
  private static final long TEST_PROFILE_EXECUTION_TIME = System.currentTimeMillis();

  @Mock private UsageLimitedFeature delegatesFeature;
  @Mock private Broadcaster broadcaster;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private DelegateTaskBroadcastHelper broadcastHelper;
  @Mock private DelegateCallbackRegistry delegateCallbackRegistry;
  @Mock private EmailNotificationService emailNotificationService;
  @Mock private AccountService accountService;
  @Mock private Account account;
  @Mock private DelegateProfileService delegateProfileService;
  @Mock private DelegateCache delegateCache;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private DelegateSyncServiceImpl delegateSyncService;
  @InjectMocks @Inject private DelegateTaskServiceImpl delegateTaskService;

  @Mock private AssignDelegateService assignDelegateService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateSelectionLogsService delegateSelectionLogsService;
  @Mock private SettingsService settingsService;

  @InjectMocks @Spy private DelegateServiceImpl spydelegateService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private VersionInfoManager versionInfoManager;
  @Mock private Subject<DelegateTaskRetryObserver> retryObserverSubject;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() throws IllegalAccessException {
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    FieldUtils.writeField(delegateTaskService, "retryObserverSubject", retryObserverSubject, true);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testRetrieveLogStreamingAccountToken() {
    String accountId = generateUuid();

    try {
      delegateService.retrieveLogStreamingAccountToken(accountId);
      fail("Should have failed while retrieving log streaming token");
    } catch (Exception ignored) {
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteTask() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);
    DelegateTask delegateTask = getDelegateTask();
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().build();
    when(delegateSelectionLogsService.createBatch(delegateTask)).thenReturn(batch);
    when(assignDelegateService.canAssign(eq(batch), anyString(), any())).thenReturn(true);
    when(assignDelegateService.retrieveActiveDelegates(
             eq(delegateTask.getAccountId()), any(BatchDelegateSelectionLog.class)))
        .thenReturn(Collections.singletonList(delegate.getUuid()));

    RetryDelegate retryDelegate = RetryDelegate.builder().retryPossible(true).delegateTask(delegateTask).build();
    when(retryObserverSubject.fireProcess(any(), any())).thenReturn(retryDelegate);

    Thread thread = new Thread(() -> {
      await().atMost(5L, TimeUnit.SECONDS).until(() -> isNotEmpty(delegateSyncService.syncTaskWaitMap));
      DelegateTask task =
          persistence.createQuery(DelegateTask.class).filter("accountId", delegateTask.getAccountId()).get();

      delegateTaskService.processDelegateResponse(task.getAccountId(), delegate.getUuid(), task.getUuid(),
          DelegateTaskResponse.builder()
              .accountId(task.getAccountId())
              .response(HttpStateExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build())
              .responseCode(ResponseCode.OK)
              .build());
      new Thread(delegateSyncService).start();
    });
    thread.start();
    DelegateResponseData responseData = delegateService.executeTask(delegateTask);
    assertThat(responseData).isInstanceOf(HttpStateExecutionResponse.class);
    HttpStateExecutionResponse httpResponse = (HttpStateExecutionResponse) responseData;
    assertThat(httpResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithPreAssignedDelegateId_Sync() {
    DelegateTask delegateTask = getDelegateTask();
    delegateTask.getData().setAsync(false);
    delegateService.saveDelegateTask(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(delegateTask.getBroadcastCount()).isZero();
    verify(broadcastHelper, times(0)).rebroadcastDelegateTask(any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithPreAssignedDelegateId_Async() {
    DelegateTask delegateTask = getDelegateTask();
    delegateTask.getData().setAsync(true);
    delegateService.saveDelegateTask(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(delegateTask.getBroadcastCount()).isZero();
    verify(broadcastHelper, times(0)).rebroadcastDelegateTask(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithPreAssignedDelegateIdSetToMustExecuteOn() {
    String delegateId = generateUuid();
    String taskId = generateUuid();

    DelegateTask delegateTask = getDelegateTask();
    delegateTask.getData().setAsync(false);
    delegateTask.setMustExecuteOnDelegateId(delegateId);
    delegateTask.setUuid(taskId);

    delegateService.saveDelegateTask(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(persistence.get(DelegateTask.class, taskId).getPreAssignedDelegateId()).isEqualTo(delegateId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithoutPreAssignedDelegateIdSetToMustExecuteOn() {
    String delegateId = generateUuid();
    String taskId = generateUuid();

    DelegateTask delegateTask = getDelegateTask();
    delegateTask.getData().setAsync(false);
    delegateTask.setUuid(taskId);

    delegateService.saveDelegateTask(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(persistence.get(DelegateTask.class, taskId).getPreAssignedDelegateId()).isNotEqualTo(delegateId);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldObtainDelegateName() {
    when(delegatesFeature.getMaxUsageAllowedForAccount(anyString())).thenReturn(Integer.MAX_VALUE);

    String delegateId = generateUuid();
    assertThat(delegateService.obtainDelegateName(null, delegateId, true)).isEmpty();
    assertThat(delegateService.obtainDelegateName("accountId", delegateId, true)).isEqualTo(delegateId);

    DelegateBuilder delegateBuilder = Delegate.builder();

    when(delegateProfileService.fetchPrimaryProfile(ACCOUNT_ID))
        .thenReturn(DelegateProfile.builder().uuid(TEST_DELEGATE_PROFILE_ID).build());
    delegateService.add(delegateBuilder.uuid(delegateId).accountId(ACCOUNT_ID).build());
    assertThat(delegateService.obtainDelegateName("accountId", delegateId, true)).isEqualTo(delegateId);

    delegateService.add(delegateBuilder.accountId(ACCOUNT_ID).build());
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo(delegateId);

    Delegate delegate = delegateBuilder.hostName("hostName").build();
    delegateService.add(delegate);
    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo("hostName");

    Delegate delegate2 = delegateBuilder.hostName("delegateName").build();
    delegateService.add(delegate2);
    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate2);
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo("delegateName");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldAcquireDelegateTaskWhitelistedDelegateAndFFisOFF() {
    Delegate delegate = createDelegateBuilder().build();
    doReturn(delegate).when(delegateCache).get(ACCOUNT_ID, delegate.getUuid(), false);
    doReturn(false).when(featureFlagService).isEnabled(eq(REVALIDATE_WHITELISTED_DELEGATE), anyString());

    DelegateTask delegateTask = getDelegateTask();
    doReturn(delegateTask).when(spydelegateService).getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());

    doReturn(getDelegateTaskPackage())
        .when(spydelegateService)
        .assignTask(anyString(), anyString(), any(DelegateTask.class));

    when(assignDelegateService.canAssign(any(), anyString(), any())).thenReturn(true);
    when(assignDelegateService.isWhitelisted(any(), anyString())).thenReturn(true);
    when(assignDelegateService.shouldValidate(any(), anyString())).thenReturn(false);
    BatchDelegateSelectionLog batch = BatchDelegateSelectionLog.builder().build();
    when(delegateSelectionLogsService.createBatch(delegateTask)).thenReturn(batch);

    spydelegateService.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), "XYZ");

    verify(spydelegateService, times(1)).assignTask(anyString(), anyString(), any(DelegateTask.class));
    verify(delegateSelectionLogsService).save(batch);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldStartTaskValidationForWhitelistedDelegateAndFFisOn() {
    Delegate delegate = createDelegateBuilder().build();
    doReturn(delegate).when(delegateCache).get(ACCOUNT_ID, delegate.getUuid(), false);
    doReturn(true).when(featureFlagService).isEnabled(eq(REVALIDATE_WHITELISTED_DELEGATE), anyString());

    doReturn(getDelegateTask())
        .when(spydelegateService)
        .getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());

    doNothing().when(spydelegateService).setValidationStarted(anyString(), any(DelegateTask.class));

    when(assignDelegateService.canAssign(any(), anyString(), any())).thenReturn(true);
    when(assignDelegateService.isWhitelisted(any(), anyString())).thenReturn(true);
    when(assignDelegateService.shouldValidate(any(), anyString())).thenReturn(false);

    spydelegateService.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), "XYZ");

    verify(spydelegateService, times(1)).setValidationStarted(anyString(), any(DelegateTask.class));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldStartTaskValidationNotWhitelistedAndFFisOff() {
    Delegate delegate = createDelegateBuilder().build();
    doReturn(delegate).when(delegateCache).get(ACCOUNT_ID, delegate.getUuid(), false);
    doReturn(true).when(featureFlagService).isEnabled(eq(REVALIDATE_WHITELISTED_DELEGATE), anyString());

    doReturn(getDelegateTask())
        .when(spydelegateService)
        .getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());

    doNothing().when(spydelegateService).setValidationStarted(anyString(), any(DelegateTask.class));

    when(assignDelegateService.canAssign(any(), anyString(), any())).thenReturn(true);
    when(assignDelegateService.isWhitelisted(any(), anyString())).thenReturn(false);
    when(assignDelegateService.shouldValidate(any(), anyString())).thenReturn(true);

    spydelegateService.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), "XYZ");

    verify(spydelegateService, times(1)).setValidationStarted(anyString(), any(DelegateTask.class));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotAcquireDelegateTaskIfTaskIsNull() {
    Delegate delegate = createDelegateBuilder().build();
    doReturn(delegate).when(delegateCache).get(ACCOUNT_ID, delegate.getUuid(), false);
    doReturn(null).when(spydelegateService).getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());
    assertThat(spydelegateService.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), "XYZ")).isNull();
  }

  private DelegateTaskPackage getDelegateTaskPackage() {
    DelegateTask delegateTask = getDelegateTask();
    return DelegateTaskPackage.builder().delegateTaskId(delegateTask.getUuid()).data(delegateTask.getData()).build();
  }

  private DelegateTask getDelegateTask() {
    return DelegateTask.builder()
        .uuid(generateUuid())
        .accountId(ACCOUNT_ID)
        .waitId(generateUuid())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
        .version(VERSION)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HTTP.name())
                  .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                  .build())
        .tags(new ArrayList<>())
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidateThatDelegateNameIsUnique() {
    String delegateName = "delegateName";
    Delegate delegate = createDelegateBuilder().build();
    delegate.setDelegateName(delegateName);
    persistence.save(delegate);
    boolean validationResult = delegateService.validateThatDelegateNameIsUnique(ACCOUNT_ID, delegateName);
    assertThat(validationResult).isFalse();

    // If the delegate doesn't exists
    boolean checkingValidationForRandomName =
        delegateService.validateThatDelegateNameIsUnique(ACCOUNT_ID, String.valueOf(System.currentTimeMillis()));
    assertThat(checkingValidationForRandomName).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEDelegate() {
    long lastHeartBeart = DateTime.now().getMillis();
    Delegate delegate =
        createDelegateBuilder().accountId(ACCOUNT_ID).delegateName(DELEGATE_NAME).uuid(generateUuid()).build();
    persistence.save(delegate);

    DelegateConnection delegateConnection = DelegateConnection.builder()
                                                .accountId(ACCOUNT_ID)
                                                .delegateId(delegate.getUuid())
                                                .lastHeartbeat(lastHeartBeart)
                                                .disconnected(false)
                                                .build();
    persistence.save(delegateConnection);

    when(settingsService.validateCEDelegateSetting(any(), any()))
        .thenReturn(CEK8sDelegatePrerequisite.builder().build());

    CEDelegateStatus ceDelegateStatus = delegateService.validateCEDelegate(ACCOUNT_ID, DELEGATE_NAME);

    verify(settingsService, times(1)).validateCEDelegateSetting(eq(ACCOUNT_ID), eq(DELEGATE_NAME));

    assertThat(ceDelegateStatus).isNotNull();
    assertThat(ceDelegateStatus.getFound()).isTrue();
    assertThat(ceDelegateStatus.getUuid()).isEqualTo(delegate.getUuid());
    assertThat(ceDelegateStatus.getMetricsServerCheck()).isNull();
    assertThat(ceDelegateStatus.getPermissionRuleList()).isNull();
    assertThat(ceDelegateStatus.getLastHeartBeat()).isGreaterThanOrEqualTo(lastHeartBeart);
    assertThat(ceDelegateStatus.getDelegateName()).isEqualTo(DELEGATE_NAME);

    assertThat(ceDelegateStatus.getConnections()).hasSizeGreaterThan(0);
    assertThat(ceDelegateStatus.getConnections().get(0).getLastHeartbeat()).isEqualTo(lastHeartBeart);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverResponseWithoutArguments() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(false).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    delegateService.handleDriverResponse(null, delegateTaskResponse);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());

    delegateService.handleDriverResponse(delegateTask, null);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());

    delegateService.handleDriverResponse(null, null);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverResponseWithNonExistingDriver() {
    DelegateTask delegateTask = mock(DelegateTask.class);
    DelegateTaskResponse delegateTaskResponse = mock(DelegateTaskResponse.class);

    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId())).thenReturn(null);

    delegateService.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateTask, never()).getUuid();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverSyncResponse() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(false).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = kryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateService.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishSyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverAsyncResponse() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(true).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = kryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateService.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishAsyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDelegateDisconnected() {
    DelegateObserver delegateObserver = mock(DelegateObserver.class);
    delegateService.getSubject().register(delegateObserver);

    String delegateId = generateUuid();
    String delegateConnectionId = generateUuid();
    String accountId = generateUuid();

    DelegateConnection delegateConnection = DelegateConnection.builder()
                                                .accountId(accountId)
                                                .uuid(delegateConnectionId)
                                                .delegateId(delegateId)
                                                .disconnected(false)
                                                .build();

    persistence.save(delegateConnection);

    delegateService.delegateDisconnected(accountId, delegateId, delegateConnectionId);

    DelegateConnection retrievedDelegateConnection =
        persistence.createQuery(DelegateConnection.class)
            .filter(DelegateConnectionKeys.uuid, delegateConnection.getUuid())
            .get();

    assertThat(retrievedDelegateConnection).isNotNull();
    assertThat(retrievedDelegateConnection.getDelegateId()).isEqualTo(delegateId);
    assertThat(retrievedDelegateConnection.getAccountId()).isEqualTo(accountId);
    assertThat(retrievedDelegateConnection.isDisconnected()).isTrue();

    verify(delegateObserver).onDisconnected(accountId, delegateId);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldSelectDelegateToRetain() {
    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(0)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(1);

    delegateService.deleteAllDelegatesExceptOne(ACCOUNT_ID, 1);

    Delegate delegateToRetain1 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate1.getUuid()).get();
    Delegate delegateToRetain2 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate2.getUuid()).get();

    assertThat(delegateToRetain1).isNotNull();
    assertThat(delegateToRetain2).isNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldSelectDelegateToRetainSendEmailAboutDelegatesOverUsage() {
    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(0)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    EmailData emailData =
        EmailData.builder()
            .hasHtml(false)
            .body(
                "Account is using more than [0] delegates. Account Id : [ACCOUNT_ID], Company Name : [NCR], Account Name : [testAccountName], Delegate Count : [1]")
            .subject("Found account with more than 0 delegates")
            .to(Lists.newArrayList("support@harness.io"))
            .build();

    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(account.getCompanyName()).thenReturn("NCR");
    when(account.getAccountName()).thenReturn("testAccountName");
    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(0);

    when(emailNotificationService.send(emailData)).thenReturn(true);

    delegateService.deleteAllDelegatesExceptOne(ACCOUNT_ID, 1);

    Delegate delegateToRetain1 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate1.getUuid()).get();
    Delegate delegateToRetain2 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate2.getUuid()).get();

    assertThat(delegateToRetain1).isNotNull();
    assertThat(delegateToRetain2).isNull();

    verify(emailNotificationService, atLeastOnce()).send(emailData);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testObtainDelegateIdShouldReturnEmpty() {
    assertThat(delegateService.obtainDelegateIds(generateUuid(), generateUuid())).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testObtainDelegateIdShouldReturnDelegateId() {
    Delegate delegate = createDelegateBuilder().sessionIdentifier(TEST_SESSION_IDENTIFIER).build();
    String delegateId = persistence.save(delegate);

    List<String> delegateIds =
        delegateService.obtainDelegateIds(delegate.getAccountId(), delegate.getSessionIdentifier());

    assertThat(delegateIds).size().isEqualTo(1);
    assertThat(delegateIds.get(0)).isEqualTo(delegateId);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetConnectedDelegates() {
    List<String> delegateIds = new ArrayList<>();

    Delegate delegate1 =
        createDelegateBuilder().accountId(ACCOUNT_ID).sessionIdentifier(TEST_SESSION_IDENTIFIER).build();
    String delegateId1 = persistence.save(delegate1);

    DelegateConnection delegateConnection1 = DelegateConnection.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .delegateId(delegateId1)
                                                 .version(versionInfoManager.getVersionInfo().getVersion())
                                                 .disconnected(false)
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .build();

    persistence.save(delegateConnection1);

    delegateIds.add(delegateId1);

    Delegate delegate2 =
        createDelegateBuilder().accountId(ACCOUNT_ID).sessionIdentifier(TEST_SESSION_IDENTIFIER).build();
    String delegateId2 = persistence.save(delegate2);

    DelegateConnection delegateConnection2 = DelegateConnection.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .delegateId(delegateId2)
                                                 .version(versionInfoManager.getVersionInfo().getVersion())
                                                 .disconnected(true)
                                                 .lastHeartbeat(System.currentTimeMillis())
                                                 .build();

    persistence.save(delegateConnection2);

    delegateIds.add(delegateId2);

    List<String> connectedDelegates = delegateService.getConnectedDelegates(ACCOUNT_ID, delegateIds);

    assertThat(connectedDelegates.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_VaultConfig() {
    TaskData taskData =
        TaskData.builder().parameters(new Object[] {HttpTaskParameters.builder().url(GOOGLE_COM).build()}).build();
    DelegateTask task = DelegateTask.builder().data(taskData).build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();
    EncryptionConfig encryptionConfig = VaultConfig.builder().vaultUrl(HTTP_VAUTL_URL).build();
    encryptionConfigs.add(encryptionConfig);

    DelegateServiceImpl.embedCapabilitiesInDelegateTask(task, encryptionConfigs, null);
    assertThat(task.getExecutionCapabilities()).isNotNull();
    assertThat(task.getExecutionCapabilities()).hasSize(2);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder(HTTP_VAUTL_URL, GOOGLE_COM);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_KmsConfig() {
    TaskData taskData =
        TaskData.builder().parameters(new Object[] {HttpTaskParameters.builder().url(GOOGLE_COM).build()}).build();
    DelegateTask task = DelegateTask.builder().data(taskData).build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();
    EncryptionConfig encryptionConfig = KmsConfig.builder().region(US_EAST_2).build();
    encryptionConfigs.add(encryptionConfig);

    DelegateServiceImpl.embedCapabilitiesInDelegateTask(task, encryptionConfigs, null);
    assertThat(task.getExecutionCapabilities()).isNotNull();
    assertThat(task.getExecutionCapabilities()).hasSize(2);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder(AWS_KMS_URL, GOOGLE_COM);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_SecretInUrl() {
    TaskData taskData =
        TaskData.builder().parameters(new Object[] {HttpTaskParameters.builder().url(SECRET_URL).build()}).build();
    DelegateTask task = DelegateTask.builder().data(taskData).build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();

    DelegateServiceImpl.embedCapabilitiesInDelegateTask(
        task, encryptionConfigs, new ManagerPreviewExpressionEvaluator());
    assertThat(task.getExecutionCapabilities()).isNotNull().hasSize(1);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder("http://google.com/?q=<<<test>>>");
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_isProfileError() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(true)
                            .build();

    String delegateId = persistence.save(delegate);

    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.isProfileError()).isTrue();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_notProfileErrorAndExecutionTime() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(TEST_PROFILE_EXECUTION_TIME)
                            .build();

    String delegateId = persistence.save(delegate);

    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_notProfileErrorBlankScript() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(0L)
                            .delegateProfileId(TEST_DELEGATE_PROFILE_ID)
                            .build();

    String delegateId = persistence.save(delegate);

    DelegateProfile delegateProfile =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(TEST_DELEGATE_PROFILE_ID).startupScript("").build();

    when(delegateProfileService.get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID)).thenReturn(delegateProfile);

    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    verify(delegateProfileService, times(1)).get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_pendingInitialization() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(0L)
                            .delegateProfileId(TEST_DELEGATE_PROFILE_ID)
                            .build();

    String delegateId = persistence.save(delegate);

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .accountId(ACCOUNT_ID)
                                          .uuid(TEST_DELEGATE_PROFILE_ID)
                                          .startupScript("testScript")
                                          .build();

    when(delegateProfileService.get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID)).thenReturn(delegateProfile);
    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    verify(delegateProfileService, times(1)).get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void obtainDelegateInitializationDetails() {
    List<String> delegateIds = setUpDelegatesForInitializationTest();

    List<DelegateInitializationDetails> delegateInitializationDetails =
        delegateService.obtainDelegateInitializationDetails(ACCOUNT_ID, delegateIds);

    assertThat(delegateInitializationDetails).isNotEmpty();
    assertThat(delegateIds.size()).isEqualTo(delegateInitializationDetails.size());

    assertThat(delegateInitializationDetails.get(0).getDelegateId()).isEqualTo(delegateIds.get(0));
    assertThat(delegateInitializationDetails.get(0).isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.get(0).isProfileError()).isTrue();

    assertThat(delegateInitializationDetails.get(1).getDelegateId()).isEqualTo(delegateIds.get(1));
    assertThat(delegateInitializationDetails.get(1).isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.get(1).isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.get(1).getProfileExecutedAt()).isEqualTo(TEST_PROFILE_EXECUTION_TIME);

    assertThat(delegateInitializationDetails.get(2).getDelegateId()).isEqualTo(delegateIds.get(2));
    assertThat(delegateInitializationDetails.get(2).isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.get(2).isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.get(2).getProfileExecutedAt()).isEqualTo(0L);

    assertThat(delegateInitializationDetails.get(3).getDelegateId()).isEqualTo(delegateIds.get(3));
    assertThat(delegateInitializationDetails.get(3).isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.get(3).isProfileError()).isFalse();
  }

  private List<String> setUpDelegatesForInitializationTest() {
    List<String> delegateIds = new ArrayList<>();

    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(true)
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(TEST_PROFILE_EXECUTION_TIME)
                             .build();

    String delegateProfileId1 = TEST_DELEGATE_PROFILE_ID + "_1";
    Delegate delegate3 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(0L)
                             .delegateProfileId(delegateProfileId1)
                             .build();

    DelegateProfile delegateProfile1 =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(delegateProfileId1).startupScript("").build();

    when(delegateProfileService.get(ACCOUNT_ID, delegateProfileId1)).thenReturn(delegateProfile1);

    String delegateProfileId2 = TEST_DELEGATE_PROFILE_ID + "_2";
    Delegate delegate4 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .sessionIdentifier(TEST_SESSION_IDENTIFIER)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(0L)
                             .delegateProfileId(delegateProfileId2)
                             .build();

    DelegateProfile delegateProfile2 =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(delegateProfileId2).startupScript("testScript").build();

    when(delegateProfileService.get(ACCOUNT_ID, delegateProfileId2)).thenReturn(delegateProfile2);

    String delegateId_1 = persistence.save(delegate1);
    String delegateId_2 = persistence.save(delegate2);
    String delegateId_3 = persistence.save(delegate3);
    String delegateId_4 = persistence.save(delegate4);

    when(delegateCache.get(ACCOUNT_ID, delegateId_1, true)).thenReturn(delegate1);
    when(delegateCache.get(ACCOUNT_ID, delegateId_2, true)).thenReturn(delegate2);
    when(delegateCache.get(ACCOUNT_ID, delegateId_3, true)).thenReturn(delegate3);
    when(delegateCache.get(ACCOUNT_ID, delegateId_4, true)).thenReturn(delegate4);

    delegateIds.add(delegateId_1);
    delegateIds.add(delegateId_2);
    delegateIds.add(delegateId_3);
    delegateIds.add(delegateId_4);

    return delegateIds;
  }
}
