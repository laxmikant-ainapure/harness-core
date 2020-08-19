package io.harness.ng.core;

import com.google.inject.AbstractModule;

import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.api.InvitesService;
import io.harness.ng.core.api.impl.InvitesServiceImpl;
import io.harness.ng.core.impl.OrganizationServiceImpl;
import io.harness.ng.core.impl.ProjectServiceImpl;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.persistence.HPersistence;

public class CoreModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(ProjectService.class).to(ProjectServiceImpl.class);
    bind(OrganizationService.class).to(OrganizationServiceImpl.class);
    bind(InvitesService.class).to(InvitesServiceImpl.class);
    registerRequiredBindings();
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
