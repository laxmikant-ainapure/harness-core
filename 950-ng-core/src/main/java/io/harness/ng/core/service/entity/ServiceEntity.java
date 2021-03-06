package io.harness.ng.core.service.entity;

import io.harness.annotation.StoreIn;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CdIndex;
import io.harness.mongo.index.Field;
import io.harness.mongo.index.NgUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.persistence.PersistentEntity;

import java.util.List;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceEntityKeys")
@NgUniqueIndex(name = "unique_accountId_organizationIdentifier_projectIdentifier_serviceIdentifier",
    fields =
    {
      @Field(ServiceEntityKeys.accountId)
      , @Field(ServiceEntityKeys.orgIdentifier), @Field(ServiceEntityKeys.projectIdentifier),
          @Field(ServiceEntityKeys.identifier)
    })
@CdIndex(name = "accountIdIndex", fields = { @Field(ServiceEntityKeys.accountId) })
@Entity(value = "servicesNG", noClassnameStored = true)
@Document("servicesNG")
@TypeAlias("io.harness.ng.core.service.entity.ServiceEntity")
@StoreIn(DbAliases.NG_MANAGER)
public class ServiceEntity implements PersistentEntity {
  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountId;
  @NotEmpty @EntityIdentifier String identifier;
  @Trimmed @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @Wither @Singular @Size(max = 128) private List<NGTag> tags;

  @NotEmpty @EntityName String name;
  @Size(max = 1024) String description;

  // TODO(archit): Add tags

  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
}
