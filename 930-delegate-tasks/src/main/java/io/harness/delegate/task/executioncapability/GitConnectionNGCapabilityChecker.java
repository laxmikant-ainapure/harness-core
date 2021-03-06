package io.harness.delegate.task.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.git.NGGitService;
import io.harness.delegate.task.shell.SshSessionConfigMapper;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitConnectionNGCapabilityChecker implements CapabilityCheck {
  @Inject private SecretDecryptionService decryptionService;
  @Inject private NGGitService gitService;
  @Inject private DelegateConfiguration delegateConfiguration;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    GitConnectionNGCapability capability = (GitConnectionNGCapability) delegateCapability;
    GitConfigDTO gitConfig = capability.getGitConfig();
    List<EncryptedDataDetail> encryptedDataDetails = capability.getEncryptedDataDetails();
    SshSessionConfig sshSessionConfig = null;

    try {
      if (gitConfig.getGitAuthType() == GitAuthType.HTTP) {
        decryptionService.decrypt(gitConfig.getGitAuth(), encryptedDataDetails);
      } else if (gitConfig.getGitAuthType() == GitAuthType.SSH) {
        sshSessionConfig =
            getSSHConfigIfSSHCredsAreUsed(capability.getSshKeySpecDTO(), capability.getEncryptedDataDetails());
      }
    } catch (Exception e) {
      log.info("Failed to decrypt " + capability.getGitConfig(), e);
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    String accountId = delegateConfiguration.getAccountId();
    if (isNotEmpty(gitService.validate(gitConfig, accountId, sshSessionConfig))) {
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    return CapabilityResponse.builder().delegateCapability(capability).validated(true).build();
  }

  private SshSessionConfig getSSHConfigIfSSHCredsAreUsed(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    return sshSessionConfigMapper.getSSHSessionConfig(sshKeySpecDTO, encryptedDataDetails);
  }
}
