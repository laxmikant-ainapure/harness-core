package io.harness.notification.channeldetails;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.NotificationRequest;
import io.harness.Team;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@EqualsAndHashCode(callSuper = true)
public class PagerDutyChannel extends NotificationChannel {
  List<String> integrationKeys;

  @Builder
  public PagerDutyChannel(String accountId, List<String> userGroupIds, String templateId,
      Map<String, String> templateData, Team team, List<String> integrationKeys) {
    super(accountId, userGroupIds, templateId, templateData, team);
    this.integrationKeys = integrationKeys;
  }

  @Override
  public NotificationRequest buildNotificationRequest() {
    NotificationRequest.Builder builder = NotificationRequest.newBuilder();
    String notificationId = generateUuid();
    return builder.setId(notificationId)
        .setAccountId(accountId)
        .setTeam(team)
        .setPagerDuty(builder.getPagerDutyBuilder()
                          .addAllPagerDutyIntegrationKeys(integrationKeys)
                          .setTemplateId(templateId)
                          .putAllTemplateData(templateData)
                          .addAllUserGroupIds(userGroupIds))
        .build();
  }
}
