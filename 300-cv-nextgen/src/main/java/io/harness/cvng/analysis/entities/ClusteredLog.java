package io.harness.cvng.analysis.entities;

import static io.harness.cvng.core.utils.DateTimeUtils.instantToEpochMinute;

import io.harness.annotation.HarnessEntity;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "ClusteredLogKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "clusteredLogs", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class ClusteredLog implements PersistentEntity, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(ClusteredLogKeys.verificationTaskId)
                 .field(ClusteredLogKeys.clusterLevel)
                 .field(ClusteredLogKeys.timestamp)
                 .build())
        .build();
  }

  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  private String verificationTaskId;
  private LogClusterLevel clusterLevel;
  private String log;
  private Instant timestamp;
  private String host;
  private String clusterLabel;
  private int clusterCount;

  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  public LogClusterDTO toDTO() {
    return LogClusterDTO.builder()
        .verificationTaskId(verificationTaskId)
        .clusterCount(clusterCount)
        .clusterLabel(clusterLabel)
        .host(host)
        .log(log)
        .epochMinute(instantToEpochMinute(timestamp))
        .build();
  }
}
