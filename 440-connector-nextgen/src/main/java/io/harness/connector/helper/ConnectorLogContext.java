package io.harness.connector.helper;

import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;

import io.harness.logging.AutoLogContext;

public class ConnectorLogContext extends AutoLogContext {
  public ConnectorLogContext(String connectorIdentifier, OverrideBehavior behavior) {
    super(CONNECTOR_IDENTIFIER_KEY, connectorIdentifier, behavior);
  }
}
