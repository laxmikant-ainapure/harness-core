package migrations.accountpermission;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;

import software.wings.security.PermissionAttribute;

import com.google.common.collect.Sets;
import java.util.Set;

public class AddManageTagsPermission extends AbstractAccountManagementPermissionMigration {
  @Override
  public Set<PermissionAttribute.PermissionType> getToBeAddedPermissions() {
    return Sets.newHashSet(MANAGE_TAGS);
  }
}
