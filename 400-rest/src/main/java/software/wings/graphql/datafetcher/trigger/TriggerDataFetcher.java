package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLTriggerQueryParameters;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.graphql.schema.type.trigger.QLTrigger.QLTriggerBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.trigger.TriggerAuthHandler;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import java.util.Collections;

@OwnedBy(CDC)
@TargetModule(Module._380_CG_GRAPHQL)
public class TriggerDataFetcher extends AbstractObjectDataFetcher<QLTrigger, QLTriggerQueryParameters> {
  private static final String EMPTY_TRIGGER_NAME = "Trigger Name should not be empty";
  @Inject HPersistence persistence;
  @Inject AppService appService;
  @Inject TriggerAuthHandler triggerAuthHandler;
  @Inject TriggerController triggerController;
  public static final String EMPTY_APPLICATION_ID = "Application Id should not be empty";

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLTrigger fetch(QLTriggerQueryParameters parameters, String accountId) {
    Trigger trigger = null;
    if (parameters.getTriggerId() != null) {
      trigger = persistence.get(Trigger.class, parameters.getTriggerId());
      if (trigger == null) {
        return null;
      }
    } else if (parameters.getTriggerName() != null) {
      if (EmptyPredicate.isEmpty(parameters.getApplicationId())) {
        throw new InvalidRequestException(EMPTY_APPLICATION_ID, WingsException.USER);
      }

      if (EmptyPredicate.isEmpty(parameters.getTriggerName())) {
        throw new InvalidRequestException(EMPTY_TRIGGER_NAME, WingsException.USER);
      }
      trigger = persistence.createQuery(Trigger.class)
                    .filter(TriggerKeys.name, parameters.getTriggerName())
                    .filter(TriggerKeys.appId, parameters.getApplicationId())
                    .get();
      if (trigger == null) {
        throw new InvalidRequestException(String.format("Trigger %s does not exist in given application %s",
                                              parameters.getTriggerName(), parameters.getApplicationId()),
            USER);
      }
    }

    if (!accountId.equals(appService.getAccountIdByAppId(trigger.getAppId()))) {
      throw new InvalidRequestException("Trigger doesn't exist", USER);
    }
    triggerAuthHandler.authorizeAppAccess(Collections.singletonList(trigger.getAppId()), accountId);

    QLTriggerBuilder qlTriggerBuilder = QLTrigger.builder();
    triggerController.populateTrigger(trigger, qlTriggerBuilder, accountId);
    return qlTriggerBuilder.build();
  }
}
