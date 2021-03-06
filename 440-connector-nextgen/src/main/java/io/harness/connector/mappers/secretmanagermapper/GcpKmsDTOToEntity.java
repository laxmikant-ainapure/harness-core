package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.gcpkmsconnector.GcpKmsConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;

public class GcpKmsDTOToEntity implements ConnectorDTOToEntityMapper<GcpKmsConnectorDTO, GcpKmsConnector> {
  @Override
  public GcpKmsConnector toConnectorEntity(GcpKmsConnectorDTO connectorDTO) {
    return GcpKmsConnector.builder()
        .projectId(connectorDTO.getProjectId())
        .region(connectorDTO.getRegion())
        .keyRing(connectorDTO.getKeyRing())
        .keyName(connectorDTO.getKeyName())
        .isDefault(connectorDTO.isDefault())
        .harnessManaged(connectorDTO.isHarnessManaged())
        .build();
  }
}
