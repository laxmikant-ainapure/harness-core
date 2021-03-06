package io.harness.accesscontrol.permissions;

import io.harness.accesscontrol.permissions.persistence.PermissionDao;
import io.harness.accesscontrol.permissions.persistence.PermissionDaoImpl;
import io.harness.accesscontrol.permissions.persistence.PermissionMorphiaRegistrar;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.morphia.MorphiaRegistrar;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class PermissionsModule extends AbstractModule {
  private static PermissionsModule instance;

  public static synchronized PermissionsModule getInstance() {
    if (instance == null) {
      instance = new PermissionsModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    Multibinder<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends MorphiaRegistrar>>() {});
    morphiaRegistrars.addBinding().toInstance(PermissionMorphiaRegistrar.class);

    bind(PermissionService.class).to(PermissionServiceImpl.class);
    bind(PermissionDao.class).to(PermissionDaoImpl.class);
    registerRequiredBindings();
  }

  private void registerRequiredBindings() {
    requireBinding(ScopeService.class);
    requireBinding(RoleService.class);
    requireBinding(TransactionTemplate.class);
    requireBinding(MongoTemplate.class);
  }
}
