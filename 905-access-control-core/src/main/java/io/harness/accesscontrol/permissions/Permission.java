package io.harness.accesscontrol.permissions;

import io.harness.accesscontrol.permissions.validator.PermissionIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(value = "Permission")
public class Permission {
  @PermissionIdentifier String identifier;
  @NGEntityName String name;
  @NotNull PermissionStatus status;
  @NotEmpty Set<String> allowedScopeLevels;
  @EqualsAndHashCode.Exclude @Setter Long version;

  private String getPermissionMetadata(int index) {
    List<String> permissionMetadata = Arrays.asList(identifier.split("\\."));
    return permissionMetadata.get(index);
  }

  public String getAction() {
    return getPermissionMetadata(2);
  }

  public String getResourceType() {
    return getPermissionMetadata(1);
  }

  public String getModule() {
    return getPermissionMetadata(0);
  }
}
