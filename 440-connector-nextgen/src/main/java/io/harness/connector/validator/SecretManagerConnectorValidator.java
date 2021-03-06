package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.FAILURE;

import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.dto.ErrorDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretManagerConnectorValidator implements ConnectionValidator {
  @Inject private NGSecretManagerService ngSecretManagerService;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    long currentTime = System.currentTimeMillis();
    try {
      return ngSecretManagerService.validate(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    } catch (Exception exception) {
      String errorMessage = exception.getMessage();
      return ConnectorValidationResult.builder()
          .status(FAILURE)
          .testedAt(currentTime)
          .errorSummary(ngErrorHelper.createErrorSummary("Invalid Credentials", errorMessage))
          .errors(getErrorDetail(errorMessage))
          .build();
    }
  }

  private List<ErrorDetail> getErrorDetail(String errorMessage) {
    return Collections.singletonList(
        ErrorDetail.builder().message(errorMessage).code(450).reason("Invalid Credentials").build());
  }
}
