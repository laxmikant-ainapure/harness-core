package io.harness.notification.utils;

import static io.harness.NotificationRequest.newBuilder;
import static io.harness.notification.constant.NotificationServiceConstants.TEST_MAIL_TEMPLATE;
import static io.harness.notification.constant.NotificationServiceConstants.TEST_MSTEAMS_TEMPLATE;
import static io.harness.notification.constant.NotificationServiceConstants.TEST_PD_TEMPLATE;
import static io.harness.notification.constant.NotificationServiceConstants.TEST_SLACK_TEMPLATE;

import io.harness.NotificationRequest;
import io.harness.NotificationRequest.ChannelCase;
import io.harness.Team;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.SlackChannel;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationRequestTestUtils {
  public static NotificationRequest getDummyNotificationRequest(ChannelCase channelCase) {
    NotificationRequest.Builder notificationRequestBuilder =
        newBuilder().setAccountId("kmpySmUISimoRrJL6NL73w").setTeam(Team.CD).setId("1");

    switch (channelCase) {
      case EMAIL:
        notificationRequestBuilder.setEmail(NotificationRequest.Email.newBuilder()
                                                .setTemplateId(TEST_MAIL_TEMPLATE)
                                                .putAllTemplateData(Collections.emptyMap())
                                                .addAllUserGroupIds(Collections.singleton("group1"))
                                                .addAllEmailIds(Collections.singleton("email@harness.io")));
        break;
      case SLACK:
        notificationRequestBuilder.setSlack(NotificationRequest.Slack.newBuilder()
                                                .setTemplateId(TEST_SLACK_TEMPLATE)
                                                .putAllTemplateData(Collections.emptyMap())
                                                .addAllUserGroupIds(Collections.singleton("group1"))
                                                .addAllSlackWebHookUrls(Collections.singleton("slack-webhookurl")));
        break;
      case MSTEAM:
        notificationRequestBuilder.setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                                 .setTemplateId(TEST_MSTEAMS_TEMPLATE)
                                                 .putAllTemplateData(Collections.emptyMap())
                                                 .addAllUserGroupIds(Collections.singleton("group1"))
                                                 .addAllMsTeamKeys(Collections.singleton("msteam-webhookurl")));
        break;
      case PAGERDUTY:
        notificationRequestBuilder.setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                                                    .setTemplateId(TEST_PD_TEMPLATE)
                                                    .putAllTemplateData(Collections.emptyMap())
                                                    .addAllUserGroupIds(Collections.singleton("group1"))
                                                    .addAllPagerDutyIntegrationKeys(Collections.singleton("pd-key")));
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + channelCase);
    }
    return notificationRequestBuilder.build();
  }

  public static Notification getDummyNotification(String id) {
    return Notification.builder()
        .id(id)
        .accountIdentifier("kmpySmUISimoRrJL6NL73w")
        .team(Team.OTHER)
        .channel(SlackChannel.builder().slackWebHookUrls(Collections.singletonList("slack-webhookurl")).build())
        .build();
  }
}
