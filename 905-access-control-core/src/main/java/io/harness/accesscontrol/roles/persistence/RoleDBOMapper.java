package io.harness.accesscontrol.roles.persistence;

import io.harness.accesscontrol.roles.Role;

import lombok.experimental.UtilityClass;

@UtilityClass
class RoleDBOMapper {
  public static RoleDBO toDBO(Role object) {
    return RoleDBO.builder()
        .identifier(object.getIdentifier())
        .scopeIdentifier(object.getScopeIdentifier())
        .name(object.getName())
        .allowedScopeLevels(object.getAllowedScopeLevels())
        .permissions(object.getPermissions())
        .managed(object.isManaged())
        .description(object.getDescription())
        .tags(object.getTags())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .version(object.getVersion())
        .build();
  }

  public static Role fromDBO(RoleDBO roleDBO) {
    return Role.builder()
        .identifier(roleDBO.getIdentifier())
        .scopeIdentifier(roleDBO.getScopeIdentifier())
        .name(roleDBO.getName())
        .allowedScopeLevels(roleDBO.getAllowedScopeLevels())
        .permissions(roleDBO.getPermissions())
        .managed(roleDBO.isManaged())
        .description(roleDBO.getDescription())
        .tags(roleDBO.getTags())
        .createdAt(roleDBO.getCreatedAt())
        .lastModifiedAt(roleDBO.getLastModifiedAt())
        .version(roleDBO.getVersion())
        .build();
  }
}
