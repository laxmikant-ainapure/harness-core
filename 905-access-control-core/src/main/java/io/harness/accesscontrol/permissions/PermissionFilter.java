package io.harness.accesscontrol.permissions;

import io.swagger.annotations.ApiModel;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@ApiModel(value = "PermissionListQuery")
public class PermissionFilter {
  @NotNull @Builder.Default Set<String> allowedScopeLevelsFilter = new HashSet<>();
  @NotNull @Builder.Default Set<PermissionStatus> statusFilter = new HashSet<>();
  @NotNull @Builder.Default Set<String> identifierFilter = new HashSet<>();

  public boolean isEmpty() {
    return allowedScopeLevelsFilter.isEmpty() && statusFilter.isEmpty() && identifierFilter.isEmpty();
  }
}
