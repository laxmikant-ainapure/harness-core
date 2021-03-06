package io.harness.ng.core.entities;

import static io.harness.notification.NotificationChannelType.MSTEAMS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("MSTEAMS")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MicrosoftTeamsConfig extends NotificationSettingConfig {
  String microsoftTeamsWebhookUrl;

  @Builder
  public MicrosoftTeamsConfig(String microsoftTeamsWebhookUrl) {
    this.microsoftTeamsWebhookUrl = microsoftTeamsWebhookUrl;
    this.type = MSTEAMS;
  }
}