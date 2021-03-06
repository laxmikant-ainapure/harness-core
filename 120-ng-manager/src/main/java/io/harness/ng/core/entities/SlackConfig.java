package io.harness.ng.core.entities;

import static io.harness.notification.NotificationChannelType.SLACK;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SLACK")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackConfig extends NotificationSettingConfig {
  String slackWebhookUrl;

  @Builder
  public SlackConfig(String slackWebhookUrl) {
    this.slackWebhookUrl = slackWebhookUrl;
    this.type = SLACK;
  }
}
