package io.harness.encryptors.managerproxy;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.ENCRYPT_SECRET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegatetasks.EncryptSecretTaskParameters;
import io.harness.delegatetasks.EncryptSecretTaskResponse;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.encryptors.KmsEncryptor;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
public class ManagerKmsEncryptor implements KmsEncryptor {
  private final DelegateService delegateService;
  private final ManagerEncryptorHelper managerEncryptorHelper;

  @Inject
  public ManagerKmsEncryptor(DelegateService delegateService, ManagerEncryptorHelper managerEncryptorHelper) {
    this.delegateService = delegateService;
    this.managerEncryptorHelper = managerEncryptorHelper;
  }

  @Override
  public EncryptedRecord encryptSecret(String accountId, String value, EncryptionConfig encryptionConfig) {
    EncryptSecretTaskParameters parameters =
        EncryptSecretTaskParameters.builder().value(value).encryptionConfig(encryptionConfig).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(ENCRYPT_SECRET.name())
                                              .parameters(new Object[] {parameters})
                                              .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .accountId(accountId)
                                    .build();
    try {
      DelegateResponseData delegateResponseData = delegateService.executeTask(delegateTask);
      DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
      if (!(delegateResponseData instanceof EncryptSecretTaskResponse)) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
      }
      EncryptSecretTaskResponse responseData = (EncryptSecretTaskResponse) delegateResponseData;
      return responseData.getEncryptedRecord();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String message =
          String.format("Interrupted while validating reference with encryption config %s", encryptionConfig.getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return managerEncryptorHelper.fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
  }
}
