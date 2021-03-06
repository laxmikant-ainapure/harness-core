package software.wings.graphql.datafetcher.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Service;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLServiceQueryParameters;
import software.wings.graphql.schema.type.QLService;
import software.wings.graphql.schema.type.QLService.QLServiceBuilder;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(Module._380_CG_GRAPHQL)
public class ServiceDataFetcher extends AbstractObjectDataFetcher<QLService, QLServiceQueryParameters> {
  @Inject HPersistence persistence;

  @Override
  @AuthRule(permissionType = PermissionType.SERVICE, action = Action.READ)
  protected QLService fetch(QLServiceQueryParameters qlQuery, String accountId) {
    Service service = persistence.get(Service.class, qlQuery.getServiceId());
    if (service == null) {
      return null;
    }

    if (!service.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("Service does not exist", WingsException.USER);
    }

    final QLServiceBuilder builder = QLService.builder();
    ServiceController.populateService(service, builder);
    return builder.build();
  }
}
