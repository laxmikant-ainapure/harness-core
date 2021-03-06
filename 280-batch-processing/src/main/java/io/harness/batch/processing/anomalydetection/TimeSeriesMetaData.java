package io.harness.batch.processing.anomalydetection;

import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.entities.TimeGranularity;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TimeSeriesMetaData {
  String accountId;
  Instant trainStart;
  Instant trainEnd;
  Instant testStart;
  Instant testEnd;
  TimeGranularity timeGranularity;
  EntityType entityType;
  String entityIdentifier;

  K8sQueryMetaData k8sQueryMetaData;
  CloudQueryMetaData cloudQueryMetaData;
}
