package io.harness.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretText;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface VaultEncryptor {
  EncryptedRecord createSecret(@NotEmpty String accountId, @NotEmpty String name, @NotEmpty String plaintext,
      @NotNull EncryptionConfig encryptionConfig);

  EncryptedRecord updateSecret(@NotEmpty String accountId, @NotEmpty String name, @NotEmpty String plaintext,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  EncryptedRecord renameSecret(@NotEmpty String accountId, @NotEmpty String name,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  default EncryptedRecord createSecret(
      @NotEmpty String accountId, @NotNull SecretText secretText, @NotNull EncryptionConfig encryptionConfig) {
    return createSecret(accountId, secretText.getName(), secretText.getValue(), encryptionConfig);
  }

  default EncryptedRecord updateSecret(@NotEmpty String accountId, @NotNull SecretText secretText,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig) {
    return updateSecret(accountId, secretText.getName(), secretText.getValue(), existingRecord, encryptionConfig);
  }

  default EncryptedRecord renameSecret(@NotEmpty String accountId, @NotNull SecretText secretText,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig) {
    return renameSecret(accountId, secretText.getName(), existingRecord, encryptionConfig);
  }

  default boolean validateReference(
      @NotEmpty String accountId, @NotNull SecretText secretText, @NotNull EncryptionConfig encryptionConfig) {
    return validateReference(accountId, secretText.getPath(), encryptionConfig);
  }

  boolean deleteSecret(
      @NotEmpty String accountId, @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  boolean validateReference(
      @NotEmpty String accountId, @NotEmpty String path, @NotNull EncryptionConfig encryptionConfig);

  char[] fetchSecretValue(
      @NotEmpty String accountId, @NotNull EncryptedRecord encryptedRecord, @NotNull EncryptionConfig encryptionConfig);
}
