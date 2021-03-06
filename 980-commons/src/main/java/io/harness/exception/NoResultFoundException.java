package io.harness.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;
import lombok.Builder;

public class NoResultFoundException extends WingsException {
  @Builder(builderMethodName = "newBuilder")
  protected NoResultFoundException(String message, Throwable cause, ErrorCode code, Level level,
      EnumSet<ReportTarget> reportTargets, EnumSet<FailureType> failureTypes) {
    super(message, cause, code, level, reportTargets, failureTypes);
    param("message", message);
  }
}
