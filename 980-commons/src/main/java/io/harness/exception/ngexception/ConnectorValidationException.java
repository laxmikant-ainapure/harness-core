package io.harness.exception.ngexception;

import static io.harness.eraro.ErrorCode.CONNECTOR_VALIDATION_EXCEPTION;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class ConnectorValidationException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public ConnectorValidationException(String message) {
    super(message, null, CONNECTOR_VALIDATION_EXCEPTION, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
  }

  public ConnectorValidationException(String message, Throwable cause) {
    super(message, cause, CONNECTOR_VALIDATION_EXCEPTION, Level.ERROR, null, null);
  }
}
