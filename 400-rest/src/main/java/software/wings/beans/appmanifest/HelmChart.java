package software.wings.beans.appmanifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.NameAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(HarnessTeam.CDC)

@Data
@Builder
@Entity(value = "helmCharts", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "HelmChartKeys")
public class HelmChart implements AccountAccess, NameAccess, PersistentEntity, UuidAware, CreatedAtAware,
                                  UpdatedAtAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appId_serviceId")
                 .field(HelmChartKeys.appId)
                 .field(HelmChartKeys.serviceId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_appManifestId")
                 .field(HelmChartKeys.accountId)
                 .field(HelmChartKeys.applicationManifestId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("appId_appManifestId")
                 .field(HelmChartKeys.appId)
                 .field(HelmChartKeys.applicationManifestId)
                 .build())
        .build();
  }

  @Id private String uuid;
  @Trimmed private String version;
  @FdIndex private String applicationManifestId;
  private String name;
  private String displayName;
  @FdIndex private String accountId;
  private String appId;
  private String serviceId;
  private long createdAt;
  private long lastUpdatedAt;
  private String appVersion;
  private String description;
  private Map<String, String> metadata;
}
