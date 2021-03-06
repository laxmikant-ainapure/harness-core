package io.harness;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NGConstants {
  public static final String SECRET_MANAGER_KEY = "secretManager";
  public static final String FILE_KEY = "file";
  public static final String FILE_METADATA_KEY = "fileMetadata";
  public static final String HARNESS_SECRET_MANAGER_IDENTIFIER = "harnessSecretManager";
  public static final String DEFAULT_ORG_IDENTIFIER = "default";
  public static final String ENTITY_REFERENCE_LOG_PREFIX = "ENTITY_REFERENCE :";
  public static final String HARNESS_BLUE = "#0063F7";
  public static final String STRING_CONNECTOR = ":";
  public static final String CONNECTOR_STRING = "connector [%s] in account [%s], org [%s], project [%s]";
  public static final String CONNECTOR_HEARTBEAT_LOG_PREFIX = "Connector Heartbeat :";
  public static final String REFERRED_ENTITY_FQN = "referredEntityFQN";
  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String REFERRED_BY_ENTITY_FQN = "referredByEntityFQN";
  public static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";
}
