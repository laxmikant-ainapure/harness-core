package software.wings.beans.trigger;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.trigger.TriggerController;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLTriggerPayload;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;

public class UpdateTriggerDataFetcher extends BaseMutatorDataFetcher<QLCreateOrUpdateTriggerInput, QLTriggerPayload> {
  @Inject TriggerController triggerController;
  private TriggerService triggerService;

  @Inject
  public UpdateTriggerDataFetcher(TriggerService triggerService) {
    super(QLCreateOrUpdateTriggerInput.class, QLTriggerPayload.class);
    this.triggerService = triggerService;
  }

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLTriggerPayload mutateAndFetch(QLCreateOrUpdateTriggerInput parameter, MutationContext mutationContext) {
    try (AutoLogContext ignore0 =
             new AccountLogContext(mutationContext.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      final Trigger savedTrigger =
          triggerService.update(triggerController.prepareTrigger(parameter, mutationContext.getAccountId()), false);
      return triggerController.prepareQLTrigger(
          savedTrigger, parameter.getClientMutationId(), mutationContext.getAccountId());
    }
  }
}
