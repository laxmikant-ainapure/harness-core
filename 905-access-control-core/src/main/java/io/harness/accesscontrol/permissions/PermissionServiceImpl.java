package io.harness.accesscontrol.permissions;

import io.harness.accesscontrol.permissions.persistence.PermissionDao;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Singleton
@ValidateOnExecution
public class PermissionServiceImpl implements PermissionService {
  private final PermissionDao permissionDao;
  private final ScopeService scopeService;
  private final RoleService roleService;
  private final TransactionTemplate transactionTemplate;

  private static final RetryPolicy<Object> transactionRetryPolicy = RetryUtils.getRetryPolicy(
      "[Retrying]: Failed to remove permission from roles and remove the permission; attempt: {}",
      "[Failed]: Failed to remove permission from roles and remove the permission; attempt: {}",
      ImmutableList.of(TransactionException.class), Duration.ofSeconds(2), 3, log);

  @Inject
  public PermissionServiceImpl(PermissionDao permissionDao, ScopeService scopeService, RoleService roleService,
      TransactionTemplate transactionTemplate) {
    this.permissionDao = permissionDao;
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Permission create(Permission permission) {
    if (!scopeService.areScopeLevelsValid(permission.getAllowedScopeLevels())) {
      throw new InvalidRequestException(
          String.format("The scopes provided in the permission %s are invalid. Please select scopes from [ %s ]",
              permission.getIdentifier(), String.join(",", scopeService.getAllScopeLevels())));
    }
    return permissionDao.create(permission);
  }

  @Override
  public Optional<Permission> get(String identifier) {
    return permissionDao.get(identifier);
  }

  @Override
  public List<Permission> list(@Valid @NotNull PermissionFilter permissionFilter) {
    return permissionDao.list(permissionFilter);
  }

  @Override
  public Permission update(Permission permissionUpdate) {
    Optional<Permission> currentPermissionOptional = get(permissionUpdate.getIdentifier());
    if (!currentPermissionOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the permission %s", permissionUpdate.getIdentifier()));
    }
    Permission currentPermission = currentPermissionOptional.get();
    if (!permissionUpdate.getAllowedScopeLevels().equals(currentPermission.getAllowedScopeLevels())) {
      throw new InvalidRequestException("Cannot change the the scopes at which this permission can be used.");
    }
    permissionUpdate.setVersion(currentPermission.getVersion());
    return permissionDao.update(permissionUpdate);
  }

  @Override
  public Permission delete(String identifier) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      boolean updateSuccessful = roleService.removePermissionFromRoles(identifier);
      if (!updateSuccessful) {
        throw new UnexpectedException(
            String.format("The removal of permissions from role has failed for permission, %s", identifier));
      }
      return permissionDao.delete(identifier);
    }));
  }
}
