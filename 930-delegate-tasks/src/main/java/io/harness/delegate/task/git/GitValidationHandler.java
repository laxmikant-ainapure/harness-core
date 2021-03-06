package io.harness.delegate.task.git;

import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.task.ConnectorValidationHandler;
import io.harness.delegate.task.shell.SshSessionConfigMapper;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GitValidationHandler implements ConnectorValidationHandler {
  @Inject private GitCommandTaskHandler gitCommandTaskHandler;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;
  @Inject private SecretDecryptionService decryptionService;
  @Inject private GitDecryptionHelper gitDecryptionHelper;

  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final ScmValidationParams scmValidationParams = (ScmValidationParams) connectorValidationParams;
    GitConfigDTO gitConfig = ScmConnectorMapper.toGitConfigDTO(scmValidationParams.getGitConfigDTO());
    gitDecryptionHelper.decryptGitConfig(gitConfig, scmValidationParams.getEncryptedDataDetails());
    SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
        scmValidationParams.getSshKeySpecDTO(), scmValidationParams.getEncryptedDataDetails());
    return gitCommandTaskHandler.validateGitCredentials(scmValidationParams.getGitConfigDTO(), accountIdentifier,
        scmValidationParams.getEncryptedDataDetails(), sshSessionConfig);
  }
}