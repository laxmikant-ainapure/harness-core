package io.harness.ng.core.entities;

import static io.harness.mongo.CollationLocale.ENGLISH;
import static io.harness.mongo.CollationStrength.PRIMARY;

import io.harness.annotation.StoreIn;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.mongo.index.CdUniqueIndexWithCollation;
import io.harness.mongo.index.Field;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.persistence.PersistentEntity;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "OrganizationKeys")
@Entity(value = "organizations", noClassnameStored = true)
@Document("organizations")
@TypeAlias("organizations")
@CdUniqueIndexWithCollation(name = "unique_accountIdentifier_organizationIdentifier",
    fields = { @Field(OrganizationKeys.accountIdentifier)
               , @Field(OrganizationKeys.identifier) }, locale = ENGLISH,
    strength = PRIMARY)
@StoreIn(DbAliases.NG_MANAGER)
public class Organization implements PersistentEntity, NGAccountAccess {
  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  String accountIdentifier;
  @EntityIdentifier(allowBlank = false) String identifier;

  @NGEntityName String name;

  @NotNull @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  @Builder.Default Boolean harnessManaged = Boolean.FALSE;
  @Wither @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastModifiedAt;
  @Wither @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
}
