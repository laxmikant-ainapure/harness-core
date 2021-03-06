package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.USER_GROUP_ALREADY_EXIST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

@OwnedBy(PL)
@SuppressWarnings("squid:CallToDeprecatedMethod")
public class UserGroupAlreadyExistException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public UserGroupAlreadyExistException(String message) {
    super(message, null, USER_GROUP_ALREADY_EXIST, Level.INFO, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
