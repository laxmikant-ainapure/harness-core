package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "MonitoringSourcePerpetualTaskKeys")
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "monitoringSourcePerpetualTasks", noClassnameStored = true)
@HarnessEntity(exportable = true)
public final class MonitoringSourcePerpetualTask
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("insert_index")
                 .unique(true)
                 .field(MonitoringSourcePerpetualTaskKeys.accountId)
                 .field(MonitoringSourcePerpetualTaskKeys.orgIdentifier)
                 .field(MonitoringSourcePerpetualTaskKeys.projectIdentifier)
                 .field(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotNull private String accountId;
  @FdIndex private long createdAt;
  private long lastUpdatedAt;

  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String monitoringSourceIdentifier;
  @NotNull private String connectorIdentifier;

  @FdIndex private Long dataCollectionTaskIteration;
  private String perpetualTaskId;
  private String dataCollectionWorkerId;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration.equals(fieldName)) {
      this.dataCollectionTaskIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration.equals(fieldName)) {
      return this.dataCollectionTaskIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
