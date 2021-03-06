package io.harness.connector.stats;

import io.harness.delegate.beans.connector.ConnectorType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ConnectorTypeStatsKeys")
@ApiModel("ConnectorTypeStatistics")
public class ConnectorTypeStats {
  ConnectorType type;
  int count;
}
