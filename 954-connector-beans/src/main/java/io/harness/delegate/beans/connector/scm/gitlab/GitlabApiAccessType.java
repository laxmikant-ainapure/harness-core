package io.harness.delegate.beans.connector.scm.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GitlabApiAccessType {
  @JsonProperty(GitlabConnectorConstants.TOKEN) TOKEN(GitlabConnectorConstants.TOKEN);

  private final String displayName;

  GitlabApiAccessType(String displayName) {
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
