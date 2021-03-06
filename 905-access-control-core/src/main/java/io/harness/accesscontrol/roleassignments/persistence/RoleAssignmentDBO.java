package io.harness.accesscontrol.roleassignments.persistence;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "RoleAssignmentDBOKeys")
@Entity(value = "roleassignments", noClassnameStored = true)
@Document("roleassignments")
@TypeAlias("roleassignments")
public class RoleAssignmentDBO implements PersistentEntity {
  @Setter @Id @org.mongodb.morphia.annotations.Id String id;
  @EntityIdentifier final String identifier;
  @NotEmpty final String scopeIdentifier;
  @NotEmpty final String resourceGroupIdentifier;
  @NotEmpty final String roleIdentifier;
  @NotEmpty final String principalIdentifier;
  @NotNull final PrincipalType principalType;
  final boolean managed;
  final boolean disabled;

  @Setter @CreatedDate Long createdAt;
  @Setter @LastModifiedDate Long lastModifiedAt;
  @Setter @CreatedBy EmbeddedUser createdBy;
  @Setter @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @Setter @Version Long version;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueIndex")
                 .unique(true)
                 .field(RoleAssignmentDBOKeys.identifier)
                 .field(RoleAssignmentDBOKeys.scopeIdentifier)
                 .build())
        .build();
  }
}
