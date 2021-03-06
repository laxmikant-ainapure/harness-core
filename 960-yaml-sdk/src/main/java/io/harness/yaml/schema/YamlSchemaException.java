package io.harness.yaml.schema;

import static io.harness.eraro.ErrorCode.UNEXPECTED_SCHEMA_EXCEPTION;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class YamlSchemaException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public YamlSchemaException(String message) {
    super(message, null, UNEXPECTED_SCHEMA_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
