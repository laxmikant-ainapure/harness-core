package io.harness.delegate.beans.connector.scm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GitAuthType {
  @JsonProperty(GitConfigConstants.HTTP) HTTP("Http"),
  @JsonProperty(GitConfigConstants.SSH) SSH("Ssh");

  private final String displayName;

  GitAuthType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
