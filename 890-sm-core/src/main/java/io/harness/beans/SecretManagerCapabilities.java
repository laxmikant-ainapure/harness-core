package io.harness.beans;

public enum SecretManagerCapabilities {
  CREATE_INLINE_SECRET,
  CREATE_REFERENCE_SECRET,
  CREATE_PARAMETERIZED_SECRET,
  CREATE_FILE_SECRET,
  TRANSITION_SECRET_TO_SM,
  TRANSITION_SECRET_FROM_SM,
  CAN_BE_DEFAULT_SM;
}
