package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.localconnector.LocalConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;

public class LocalEntityToDTO implements ConnectorEntityToDTOMapper<LocalConnectorDTO, LocalConnector> {
  @Override
  public LocalConnectorDTO createConnectorDTO(LocalConnector connector) {
    LocalConnectorDTO localConnectorDTO = LocalConnectorDTO.builder().isDefault(connector.isDefault()).build();
    localConnectorDTO.setHarnessManaged(Boolean.TRUE.equals(connector.getHarnessManaged()));
    return localConnectorDTO;
  }
}
