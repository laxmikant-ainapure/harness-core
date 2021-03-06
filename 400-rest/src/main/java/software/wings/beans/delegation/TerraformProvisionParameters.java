package software.wings.beans.delegation;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.provision.TfVarSource;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.capabilities.GitConnectionCapability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(Module._950_DELEGATE_TASKS_BEANS)
public class TerraformProvisionParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  public static final long TIMEOUT_IN_MINUTES = 100;
  public static final String TERRAFORM = "terraform";

  public enum TerraformCommand { APPLY, DESTROY }

  public enum TerraformCommandUnit {
    Apply,
    Adjust,
    Destroy,
    Rollback;
  }

  private String accountId;
  private final String activityId;
  private final String appId;
  private final String entityId;
  private final String currentStateFileId;
  private final String sourceRepoSettingId;
  private final GitConfig sourceRepo;
  private final String sourceRepoBranch;
  private final String commitId;
  List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private final String scriptPath;
  private final List<NameValuePair> rawVariables;
  @Expression(ALLOW_SECRETS) private final Map<String, String> variables;
  private final Map<String, EncryptedDataDetail> encryptedVariables;

  private final Map<String, String> backendConfigs;
  private final Map<String, EncryptedDataDetail> encryptedBackendConfigs;

  @Expression(ALLOW_SECRETS) private final Map<String, String> environmentVariables;
  private final Map<String, EncryptedDataDetail> encryptedEnvironmentVariables;

  private final TerraformCommand command;
  private final TerraformCommandUnit commandUnit;
  @Builder.Default private long timeoutInMillis = TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES);

  private final List<String> targets;

  private final List<String> tfVarFiles;
  private final boolean runPlanOnly;
  private final boolean exportPlanToApplyStep;
  private final String workspace;
  private final String delegateTag;

  private final boolean saveTerraformJson;
  private final SecretManagerConfig secretManagerConfig;
  private final EncryptedRecordData encryptedTfPlan;
  private final String planName;

  private final TfVarSource tfVarSource;
  /**
   * Boolean to indicate if we should skip updating terraform state using refresh command before applying an approved
   * terraform plan
   */
  private boolean skipRefreshBeforeApplyingPlan;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        CapabilityHelper.generateExecutionCapabilitiesForTerraform(sourceRepoEncryptionDetails, maskingEvaluator);
    if (sourceRepo != null) {
      capabilities.add(GitConnectionCapability.builder()
                           .gitConfig(sourceRepo)
                           .settingAttribute(sourceRepo.getSshSettingAttribute())
                           .encryptedDataDetails(sourceRepoEncryptionDetails)
                           .build());
    }
    if (secretManagerConfig != null) {
      capabilities.addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(secretManagerConfig, null));
    }
    return capabilities;
  }
}
