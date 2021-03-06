package migrations.accountpermission;

import static software.wings.security.PermissionAttribute.PermissionType.CREATE_CUSTOM_DASHBOARDS;

import software.wings.security.PermissionAttribute;

import com.google.common.collect.Sets;
import java.util.Set;

public class CreateCustomDashboardPermissionMigration extends AbstractAccountManagementPermissionMigration {
  @Override
  public Set<PermissionAttribute.PermissionType> getToBeAddedPermissions() {
    return Sets.newHashSet(CREATE_CUSTOM_DASHBOARDS);
  }
}
