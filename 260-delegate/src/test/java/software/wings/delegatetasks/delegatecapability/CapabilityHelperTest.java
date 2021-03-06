package software.wings.delegatetasks.delegatecapability;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptableSettingWithEncryptionDetails;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.VaultConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(Module._930_DELEGATE_TASKS)
public class CapabilityHelperTest extends WingsBaseTest {
  public static final String HTTP_VAUTL_URL = "http://vautl.com";
  public static final String US_EAST_2 = "us-east-2";
  public static final String AWS_KMS_URL = "https://kms.us-east-2.amazonaws.com";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchEncryptionDetailsListFromParameters() {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.LOCAL).build())
            .build());

    TaskData taskData =
        TaskData.builder().parameters(new Object[] {JenkinsConfig.builder().build(), encryptedDataDetails}).build();

    Map encryptionMap = CapabilityHelper.fetchEncryptionDetailsListFromParameters(taskData);
    assertThat(encryptionMap).isNotNull();
    assertThat(encryptionMap).isEmpty();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchEncryptionDetailsListFromParameters_VaultConfig() throws Exception {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.VAULT).build())
            .encryptionConfig(VaultConfig.builder().vaultUrl(HTTP_VAUTL_URL).build())
            .build());

    TaskData taskData =
        TaskData.builder().parameters(new Object[] {JenkinsConfig.builder().build(), encryptedDataDetails}).build();

    Map encryptionMap = CapabilityHelper.fetchEncryptionDetailsListFromParameters(taskData);
    assertThat(encryptionMap).isNotNull();
    assertThat(encryptionMap).hasSize(1);
    EncryptionConfig encryptionConfig = (EncryptionConfig) encryptionMap.values().iterator().next();

    assertThat(encryptionConfig.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(encryptionConfig instanceof VaultConfig).isTrue();
    assertThat(((VaultConfig) encryptionConfig).getVaultUrl()).isEqualTo(HTTP_VAUTL_URL);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchEncryptionDetailsListFromParameters_KmsConfig() throws Exception {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
            .encryptionConfig(KmsConfig.builder().region(US_EAST_2).build())
            .build());

    TaskData taskData =
        TaskData.builder().parameters(new Object[] {JenkinsConfig.builder().build(), encryptedDataDetails}).build();

    Map encryptionMap = CapabilityHelper.fetchEncryptionDetailsListFromParameters(taskData);
    assertThat(encryptionMap).isNotNull();
    assertThat(encryptionMap).hasSize(1);
    EncryptionConfig encryptionConfig = (EncryptionConfig) encryptionMap.values().iterator().next();
    assertThat(encryptionConfig.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    assertThat(encryptionConfig instanceof KmsConfig).isTrue();
    assertThat(((KmsConfig) encryptionConfig).getRegion()).isEqualTo(US_EAST_2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetHttpCapabilityForDecryption_VaultConfig() throws Exception {
    EncryptionConfig encryptionConfig = VaultConfig.builder().vaultUrl(HTTP_VAUTL_URL).build();
    List<ExecutionCapability> capability =
        EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(encryptionConfig, null);
    assertThat(HTTP_VAUTL_URL).isEqualTo(capability.get(0).fetchCapabilityBasis());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetHttpCapabilityForDecryption_KmsConfig() throws Exception {
    EncryptionConfig encryptionConfig = KmsConfig.builder().region(US_EAST_2).build();
    List<ExecutionCapability> capability =
        EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(encryptionConfig, null);
    assertThat(AWS_KMS_URL).isEqualTo(capability.get(0).fetchCapabilityBasis());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testGetHttpCapabilityForDecryption_secretconfig() throws Exception {
    EncryptionConfig encryptionConfig = CyberArkConfig.builder().cyberArkUrl("https://harness.cyberark.com").build();
    List<ExecutionCapability> capability =
        EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(encryptionConfig, null);
    assertThat("https://harness.cyberark.com").isEqualTo(capability.get(0).fetchCapabilityBasis());
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void testFetchEncryptionDetailsList_BATCH_SECRET_DECRYPT() throws Exception {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.VAULT).build())
            .encryptionConfig(VaultConfig.builder().vaultUrl(HTTP_VAUTL_URL).build())
            .build());
    List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetails = new ArrayList<>();
    encryptableSettingWithEncryptionDetails.add(
        EncryptableSettingWithEncryptionDetails.builder().encryptedDataDetails(encryptedDataDetails).build());
    TaskData taskData =
        TaskData.builder()
            .parameters(new Object[] {JenkinsConfig.builder().build(), encryptableSettingWithEncryptionDetails})
            .build();

    Map encryptionMap = CapabilityHelper.fetchEncryptionDetailsListFromParameters(taskData);
    assertThat(encryptionMap).isNotNull();
    assertThat(encryptionMap).hasSize(1);
    EncryptionConfig encryptionConfig = (EncryptionConfig) encryptionMap.values().iterator().next();

    assertThat(encryptionConfig.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(encryptionConfig instanceof VaultConfig).isTrue();
    assertThat(((VaultConfig) encryptionConfig).getVaultUrl()).isEqualTo(HTTP_VAUTL_URL);
  }
}
