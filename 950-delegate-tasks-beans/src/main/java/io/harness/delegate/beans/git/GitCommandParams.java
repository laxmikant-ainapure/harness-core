package io.harness.delegate.beans.git;

import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.git.model.GitBaseRequest;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitCommandParams implements TaskParameters, ExecutionCapabilityDemander {
  ScmConnector gitConfig;
  GitCommandType gitCommandType;
  List<EncryptedDataDetail> encryptionDetails;
  GitBaseRequest gitCommandRequest;
  SSHKeySpecDTO sshKeySpecDTO;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(GitConnectionNGCapability.builder()
                                         .encryptedDataDetails(encryptionDetails)
                                         .gitConfig(ScmConnectorMapper.toGitConfigDTO(gitConfig))
                                         .sshKeySpecDTO(sshKeySpecDTO)
                                         .build());
  }
}
