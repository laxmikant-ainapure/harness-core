package software.wings.settings;

import lombok.Getter;

/**
 * The Enum SettingVariableTypes.
 */
public enum SettingVariableTypes {
  /**
   * Host connection attributes setting variable types.
   */
  HOST_CONNECTION_ATTRIBUTES("Host Connection Attributes"),

  /**
   * Bastion host connection attributes setting variable types.
   */
  BASTION_HOST_CONNECTION_ATTRIBUTES("Bastion Host Connection Attributes"),

  /**
   * Smtp setting variable types.
   */
  SMTP("SMTP"),
  /**
   * Sftp setting variable types.
   */
  SFTP("Sftp"),
  /**
   * Jenkins setting variable types.
   */
  JENKINS("Jenkins"),

  /**
   * Bamboo setting variable types.
   */
  BAMBOO("Bamboo"),

  /**
   * String setting variable types.
   */
  STRING,

  /**
   * Splunk setting variable types.
   */
  SPLUNK("Splunk"),

  /**
   * Elk setting variable types.
   */
  ELK("ELK"),

  /**
   * Logz setting variable types.
   */
  LOGZ("LOGZ"),

  /**
   * SUMO setting variable types.
   */
  SUMO("Sumo Logic"),

  /**
   * Datadog setting variable type.
   */
  DATA_DOG("Datadog"),

  /**
   * Generic APM setting variable types.
   */
  APM_VERIFICATION("APM Verification"),
  /**
   * Bugsnag setting variable type.
   */
  BUG_SNAG("Bugsnag"),

  /**
   * Generic Log setting variable types.
   */
  LOG_VERIFICATION("Log Verification"),

  /**
   * App dynamics setting variable types.
   */
  APP_DYNAMICS("AppDynamics"),

  /**
   * New relic setting variable types.
   */
  NEW_RELIC("New Relic"),

  /**
   * Dyna trace variable types.
   */
  DYNA_TRACE("Dynatrace"),

  INSTANA("Instana"),

  DATA_DOG_LOG("Datadog log"),
  /**
   * Dyna trace variable types.
   */
  CLOUD_WATCH("Cloudwatch"),

  SCALYR("Scalyr"),

  /**
   * Elastic Load Balancer Settings
   */
  ELB("Elastic Classic Load Balancer"),

  /**
   * Slack setting variable types.
   */
  SLACK("SLACK"),

  /**
   * AWS setting variable types.
   */
  AWS("Amazon Web Services"),

  /**
   * GCS setting variable types.
   */
  GCS("Google Cloud Storage"),

  /**
   * GCP setting variable types.
   */
  GCP("Google Cloud Platform"),

  /**
   * Azure setting variable types.
   */
  AZURE("Microsoft Azure"),

  /**
   * Pivotal Cloud Foundry
   */
  PCF("Pivotal Cloud Foundry"),

  /**
   * Direct connection setting variable types.
   */
  @Deprecated DIRECT("Direct Kubernetes"),

  /**
   * Kubernetes Cluster setting variable types.
   */
  KUBERNETES_CLUSTER("Kubernetes Cluster"),

  /**
   * Docker registry setting variable types.
   */
  DOCKER("Docker Registry"),

  /**
   * AWS ECR registry setting variable types.
   */
  ECR("Amazon EC2 Container Registry (ECR)"),

  /**
   * Google Container Registry setting variable types.
   */
  GCR("Google Container Registry (GCR)"),

  /**
   * Azure Container Registry setting variable types.
   */
  ACR("Azure Container Registry (ACR)"),

  /**
   * Physical data center setting variable types.
   */
  PHYSICAL_DATA_CENTER("Physical Data Center"),

  /**
   * Kubernetes setting variable types.
   */
  KUBERNETES("Kubernetes"),

  /**
   * Nexus setting variable types.
   */
  NEXUS("Nexus"),

  /**
   * Artifactory setting variable types
   */
  ARTIFACTORY("Artifactory"),
  /**
   * SMB setting variable types
   */
  SMB("Smb"),

  /**
   * Amazon S3 setting variable types
   */
  AMAZON_S3("AmazonS3"),

  /**
   * Git setting variable types.
   */
  GIT("GIT"),
  /**
   * Ssh session config setting variable types.
   */
  SSH_SESSION_CONFIG,

  /**
   * Service variable setting variable types.
   */
  SERVICE_VARIABLE,

  /**
   * Config file setting variable types.
   */
  CONFIG_FILE,

  /**
   * Kms setting variable types.
   */
  KMS,

  /**
   * GCP KMS setting variable types
   */
  GCP_KMS,

  /**
   * Jira setting variable types.
   */
  JIRA("JIRA"),

  SERVICENOW("ServiceNow"),

  SECRET_TEXT,

  YAML_GIT_SYNC,

  VAULT,

  AWS_SECRETS_MANAGER,

  CYBERARK,

  WINRM_CONNECTION_ATTRIBUTES("WinRm Connection Attributes"),

  WINRM_SESSION_CONFIG,

  PROMETHEUS("Prometheus"),

  INFRASTRUCTURE_MAPPING,

  HTTP_HELM_REPO("HTTP Helm Repository"),

  AMAZON_S3_HELM_REPO("Amazon S3 Helm Repository"),

  GCS_HELM_REPO("GCS Helm Repository"),

  SPOT_INST("Spotinst"),

  AZURE_ARTIFACTS_PAT("Azure Artifacts"),

  CUSTOM("Custom Artifact Source"),

  CE_AWS("Continuous Efficiency AWS"),

  CE_GCP("Continuous Efficiency GCP"),

  CE_AZURE("Continuous Efficiency AZURE"),

  AZURE_VAULT("Azure Vault Secrets Manager"),

  KUBERNETES_CLUSTER_NG("Kubernetes Cluster"),

  GIT_NG("Git Ng"),

  GCP_SECRETS_MANAGER;

  @Getter private String displayName;

  SettingVariableTypes() {
    this.displayName = name();
  }

  SettingVariableTypes(String displayName) {
    this.displayName = displayName;
  }
}
