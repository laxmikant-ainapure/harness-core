package io.harness.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;

public class NonPersistentLockException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public NonPersistentLockException(String message, ErrorCode code, EnumSet<ReportTarget> reportTarget) {
    super(message, null, code, Level.ERROR, reportTarget, null);
    param(MESSAGE_KEY, message);
  }

  public NonPersistentLockException(
      String message, Throwable error, ErrorCode code, EnumSet<ReportTarget> reportTarget) {
    super(message, error, code, Level.ERROR, reportTarget, null);
    param(MESSAGE_KEY, message);
  }
}
