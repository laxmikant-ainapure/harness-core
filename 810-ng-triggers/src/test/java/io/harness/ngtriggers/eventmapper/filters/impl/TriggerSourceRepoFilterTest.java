package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.response.WebhookEventResponse.FinalStatus;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TriggerSourceRepoFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Inject @InjectMocks private SourceRepoTypeTriggerFilter filter;
  private static List<NGTriggerEntity> triggerEntities;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void applyRepoUrlFilterTest() {
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .metadata(
                        NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("GITHUB").build()).build())
                    .identifier("T1")
                    .build())
            .build();
    TriggerDetails details2 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .metadata(
                        NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("GITLAB").build()).build())
                    .identifier("T2")
                    .build())
            .build();

    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .projectFqn("acc/org/proj")
                                              .webhookPayloadData(WebhookPayloadData.builder()
                                                                      .originalEvent(TriggerWebhookEvent.builder()
                                                                                         .accountId("acc")
                                                                                         .orgIdentifier("org")
                                                                                         .projectIdentifier("proj")
                                                                                         .sourceRepoType("GITHUB")
                                                                                         .createdAt(0l)
                                                                                         .nextIteration(0l)
                                                                                         .build())
                                                                      .build())
                                              .build();

    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(FinalStatus.NO_ENABLED_TRIGGER_FOR_SOURCEREPO_TYPE);

    List<TriggerDetails> triggerDetails = Arrays.asList(details1, details2);
    filterRequestData.setDetails(triggerDetails);
    webhookEventMappingResponse = filter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getTriggers()).containsExactly(details1);
  }
}
