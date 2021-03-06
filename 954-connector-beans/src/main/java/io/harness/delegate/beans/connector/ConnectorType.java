package io.harness.delegate.beans.connector;

import io.harness.EntitySubtype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConnectorType implements EntitySubtype {
  @JsonProperty("K8sCluster") KUBERNETES_CLUSTER("K8sCluster"),
  @JsonProperty("Git") GIT("Git"),
  @JsonProperty("Splunk") SPLUNK("Splunk"),
  @JsonProperty("AppDynamics") APP_DYNAMICS("AppDynamics"),
  @JsonProperty("Vault") VAULT("Vault"),
  @JsonProperty("DockerRegistry") DOCKER("DockerRegistry"),
  @JsonProperty("Local") LOCAL("Local"),
  //  @JsonProperty("AwsKms") KMS("AwsKms"),
  @JsonProperty("GcpKms") GCP_KMS("GcpKms"),
  //  @JsonProperty("Awssecretsmanager") AWS_SECRETS_MANAGER("Awssecretsmanager"),
  //  @JsonProperty("Azurevault") AZURE_VAULT("Azurevault"),
  //  @JsonProperty("Cyberark") CYBERARK("Cyberark"),
  //  @JsonProperty("CustomSecretManager") CUSTOM("CustomSecretManager"),
  @JsonProperty("Gcp") GCP("Gcp"),
  @JsonProperty("Aws") AWS("Aws"),
  @JsonProperty("Artifactory") ARTIFACTORY("Artifactory"),
  @JsonProperty("Jira") JIRA("Jira"),
  @JsonProperty("Nexus") NEXUS("Nexus"),
  @JsonProperty("Github") GITHUB("Github"),
  @JsonProperty("Gitlab") GITLAB("Gitlab"),
  @JsonProperty("Bitbucket") BITBUCKET("Bitbucket"),
  @JsonProperty("CEAws") CE_AWS("CEAws"),
  @JsonProperty("CEAzure") CE_AZURE("CEAzure"),
  @JsonProperty("CEK8sCluster") CE_KUBERNETES_CLUSTER("CEK8sCluster");

  private final String displayName;

  @JsonCreator
  public static ConnectorType getConnectorType(@JsonProperty("type") String displayName) {
    for (ConnectorType connectorType : ConnectorType.values()) {
      if (connectorType.displayName.equalsIgnoreCase(displayName)) {
        return connectorType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  ConnectorType(String displayName) {
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

  public static ConnectorType fromString(final String s) {
    return ConnectorType.getConnectorType(s);
  }
}
