package software.wings.sm.states.provision;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BOJANA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PORTAL_URL;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKSPACE;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.service.DelegateAgentFileService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.TerraformApplyMarkerParam;
import software.wings.api.TerraformExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.mongodb.DBCursor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

public class TerraformRollbackStateTest extends WingsBaseTest {
  @Mock TerraformConfig configParameter;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) ExecutionContextImpl executionContext;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) MainConfiguration configuration;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private FileService fileService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock private SecretManager secretManager;
  @Mock private DelegateService delegateService;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @InjectMocks TerraformRollbackState terraformRollbackState = new TerraformRollbackState("Rollback Terraform Test");

  @Before
  public void setup() {
    Answer<String> doReturnSameValue = invocation -> invocation.getArgumentAt(0, String.class);
    BiFunction<String, Collector, Answer> extractVariablesOfType = (type, collector) -> {
      return invocation -> {
        List<NameValuePair> input = invocation.getArgumentAt(0, List.class);
        return input.stream().filter(value -> type.equals(value.getValueType())).collect(collector);
      };
    };
    Answer doExtractTextVariables =
        extractVariablesOfType.apply("TEXT", toMap(NameValuePair::getName, NameValuePair::getValue));
    Answer doExtractEncryptedVariables = extractVariablesOfType.apply("ENCRYPTED_TEXT",
        toMap(NameValuePair::getName, entry -> EncryptedDataDetail.builder().fieldName(entry.getName()).build()));
    doAnswer(doExtractTextVariables)
        .when(infrastructureProvisionerService)
        .extractTextVariables(anyListOf(NameValuePair.class), any(ExecutionContext.class));
    doAnswer(doExtractTextVariables)
        .when(infrastructureProvisionerService)
        .extractUnresolvedTextVariables(anyListOf(NameValuePair.class));
    doAnswer(doExtractEncryptedVariables)
        .when(infrastructureProvisionerService)
        .extractEncryptedTextVariables(anyListOf(NameValuePair.class), anyString());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
  }

  /**
   * Tests whether expected last successful workflow execution is returned.
   */
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldReturnValidLastSuccessfulWorkflowExecutionUrl() {
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configParameter.getAccountId()).thenReturn(ACCOUNT_ID);
    when(configParameter.getAppId()).thenReturn(APP_ID);
    when(executionContext.getEnv().getUuid()).thenReturn(ENV_ID);
    when(configParameter.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    final String expectedUrl = PORTAL_URL + "/#/account/" + ACCOUNT_ID + "/app/" + APP_ID + "/env/" + ENV_ID
        + "/executions/" + WORKFLOW_EXECUTION_ID + "/details";
    assertThat(terraformRollbackState.getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext)
                   .toString()
                   .equals(expectedUrl))
        .isTrue();
    // Check Url when env is null.
    when(executionContext.getEnv()).thenReturn(null);

    final String nullEnvExpectedUrl = PORTAL_URL + "/#/account/" + ACCOUNT_ID + "/app/" + APP_ID
        + "/env/null/executions/" + WORKFLOW_EXECUTION_ID + "/details";
    assertThat(terraformRollbackState.getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext)
                   .toString()
                   .equals(nullEnvExpectedUrl))
        .isTrue();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternalNoApply() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().name("Terraform Provisioner").build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerraformApplyMarkerParam.builder().applyCompleted(false).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(1)).find(any(SweepingOutputInquiry.class));

    // we didn't find a result in sweeping output
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(null);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(infrastructureProvisionerService, times(2)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(2)).find(any(SweepingOutputInquiry.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternal() {
    setUp("sourceRepoBranch", true, WORKFLOW_EXECUTION_ID);
    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(
        executionResponse, "sourceRepoBranch", true, 1, TerraformProvisionParameters.TerraformCommand.DESTROY);

    // no variables, no backend configs, no source repo branch
    setUp(null, false, WORKFLOW_EXECUTION_ID);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, null, false, 2, TerraformProvisionParameters.TerraformCommand.DESTROY);

    // Inheriting terraform execution from last successful terraform execution
    setUp("sourceRepoBranch", true, null);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 3, TerraformProvisionParameters.TerraformCommand.APPLY);
  }

  private void setUp(String sourceRepoBranch, boolean setVarsAndBackendConfigs, String workflowExecutionId) {
    terraformRollbackState.setWorkspace(WORKSPACE);
    when(executionContext.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner = TerraformInfrastructureProvisioner.builder()
                                                                                .name("Terraform Provisioner")
                                                                                .sourceRepoBranch(sourceRepoBranch)
                                                                                .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerraformApplyMarkerParam.builder().applyCompleted(true).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    doReturn(environment).when(executionContext).getEnv();
    Query<TerraformConfig> query = mock(Query.class);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(anyString(), any(Object.class))).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
            .sourceRepoSettingId("sourceRepoSettingsId")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(setVarsAndBackendConfigs ? getTerraformBackendConfigs() : null)
            .variables(setVarsAndBackendConfigs ? getTerraformVariables() : null)
            .environmentVariables(setVarsAndBackendConfigs ? getTerraformEnvironmentVariables() : null)
            .build();

    MorphiaIterator<TerraformConfig, TerraformConfig> morphiaIterator = mock(MorphiaIterator.class);
    DBCursor dbCursor = mock(DBCursor.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(terraformConfig);
    when(morphiaIterator.getCursor()).thenReturn(dbCursor);
    when(query.fetch()).thenReturn(morphiaIterator);

    when(fileService.getLatestFileId(anyString(), any(DelegateAgentFileService.FileBucket.class))).thenReturn("fileId");
    when(gitUtilsManager.getGitConfig(anyString())).thenReturn(GitConfig.builder().build());
    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
  }

  private void verifyResponse(ExecutionResponse executionResponse, String branch, boolean checkVarsAndBackendConfigs,
      int i, TerraformProvisionParameters.TerraformCommand command) {
    verify(wingsPersistence, times(i)).createQuery(TerraformConfig.class);
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo(ACTIVITY_ID);
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo(ACTIVITY_ID);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(i)).queueTask(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(1);
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) delegateTask.getData().getParameters()[0];
    assertThat(parameters.getSourceRepoSettingId()).isEqualTo("sourceRepoSettingsId");
    assertThat(parameters.getCommand()).isEqualTo(command);
    if (checkVarsAndBackendConfigs) {
      assertThat(parameters.getVariables()).containsOnlyKeys("vpc_id", "region");
      assertThat(parameters.getEncryptedVariables()).containsOnlyKeys("access_key", "secret_key");
      assertThat(parameters.getEncryptedBackendConfigs()).containsOnlyKeys("access_token");
      assertThat(parameters.getBackendConfigs()).containsOnlyKeys("bucket", "key");
      assertThat(parameters.getEnvironmentVariables()).containsOnlyKeys("TF_LOG");
      assertThat(parameters.getEncryptedEnvironmentVariables()).containsOnlyKeys("secret_key");
    } else {
      assertThat(parameters.getVariables()).isNull();
      assertThat(parameters.getEncryptedVariables()).isNull();
      assertThat(parameters.getBackendConfigs()).isNull();
      assertThat(parameters.getEncryptedBackendConfigs()).isNull();
      assertThat(parameters.getEnvironmentVariables()).isNull();
      assertThat(parameters.getEncryptedEnvironmentVariables()).isNull();
    }
    GitConfig gitConfig = parameters.getSourceRepo();
    assertThat(gitConfig.getBranch()).isEqualTo(branch);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseApply() {
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    when(executionContext.getEnv()).thenReturn(environment);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner = TerraformInfrastructureProvisioner.builder()
                                                                                .name("Terraform Provisioner")
                                                                                .sourceRepoBranch("sourceRepoBranch")
                                                                                .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .stateFileId("stateFileId")
            .commandExecuted(TerraformProvisionParameters.TerraformCommand.APPLY)
            .build();
    response.put("activityId", terraformExecutionData);

    ExecutionResponse executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 1);
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(any(Class.class), anyString(), anyInt(), anyString(), anyMap(),
            any(DelegateAgentFileService.FileBucket.class));

    // no state file
    terraformExecutionData.setStateFileId(null);
    executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 2);
  }

  private void verifyResponse(ExecutionResponse executionResponse, int i) {
    verify(infrastructureProvisionerService, times(i)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    verify(wingsPersistence, times(i)).save(captor.capture());
    TerraformConfig terraformConfig = captor.getValue();
    assertThat(terraformConfig).isNotNull();
    assertThat(terraformConfig.getWorkflowExecutionId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(terraformConfig.getEntityId()).isEqualTo(String.format("%s-%s", PROVISIONER_ID, UUID));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDestroy() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .stateFileId("stateFileId")
            .commandExecuted(TerraformProvisionParameters.TerraformCommand.DESTROY)
            .build();
    response.put("activityId", terraformExecutionData);

    Query<TerraformConfig> query = mock(Query.class);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(anyString(), any(Object.class))).thenReturn(query);
    ExecutionResponse executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);

    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(any(Class.class), anyString(), anyInt(), anyString(), anyMap(),
            any(DelegateAgentFileService.FileBucket.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(wingsPersistence, times(1)).createQuery(TerraformConfig.class);
    verify(wingsPersistence, times(1)).delete(query);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private List<NameValuePair> getTerraformVariables() {
    return Arrays.asList(NameValuePair.builder().name("region").value("us-east").valueType("TEXT").build(),
        NameValuePair.builder().name("vpc_id").value("vpc-id").valueType("TEXT").build(),
        NameValuePair.builder().name("access_key").value("access_key").valueType("ENCRYPTED_TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformEnvironmentVariables() {
    return Arrays.asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformBackendConfigs() {
    return Arrays.asList(NameValuePair.builder().name("key").value("terraform.tfstate").valueType("TEXT").build(),
        NameValuePair.builder().name("bucket").value("tf-remote-state").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }
}
