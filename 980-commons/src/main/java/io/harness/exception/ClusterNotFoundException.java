package io.harness.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;

public class ClusterNotFoundException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public ClusterNotFoundException(String message, ErrorCode code, EnumSet<ReportTarget> reportTargets) {
    super(message, null, code, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_KEY, message);
  }
}
