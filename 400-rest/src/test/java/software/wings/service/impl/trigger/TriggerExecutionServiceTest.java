package software.wings.service.impl.trigger;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.beans.trigger.TriggerExecution.WebhookEventDetails;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.trigger.TriggerExecutionService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TriggerExecutionServiceTest extends WingsBaseTest {
  @Inject @InjectMocks private TriggerExecutionService triggerExecutionService;
  @Mock private AppService appService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFetchLastSuccessTriggerExecution() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    String webhookToken = generateUuid();
    TriggerExecution triggerExecution =
        TriggerExecution.builder()
            .appId(APP_ID)
            .triggerId(TRIGGER_ID)
            .triggerName(TRIGGER_NAME)
            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
            .status(Status.SUCCESS)
            .webhookToken(webhookToken)
            .webhookEventDetails(WebhookEventDetails.builder().branchName("master").build())
            .build();

    TriggerExecution savedTriggerExecution = triggerExecutionService.save(triggerExecution);

    assertThat(savedTriggerExecution).isNotNull();
    assertThat(savedTriggerExecution.getTriggerId()).isEqualTo(TRIGGER_ID);

    savedTriggerExecution = triggerExecutionService.get(APP_ID, savedTriggerExecution.getUuid());
    assertThat(savedTriggerExecution).isNotNull();
    assertThat(savedTriggerExecution.getTriggerId()).isEqualTo(TRIGGER_ID);

    TriggerExecution successExecution =
        triggerExecutionService.fetchLastSuccessOrRunningExecution(APP_ID, TRIGGER_ID, webhookToken);

    assertThat(successExecution).isNotNull();
    assertThat(successExecution.getTriggerId()).isEqualTo(TRIGGER_ID);
    assertThat(successExecution.getStatus()).isEqualTo(Status.SUCCESS);
  }
}
