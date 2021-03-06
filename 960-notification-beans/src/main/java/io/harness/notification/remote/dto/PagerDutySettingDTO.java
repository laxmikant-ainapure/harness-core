package io.harness.notification.remote.dto;

import io.harness.notification.NotificationChannelType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PagerDutySettingDTO extends NotificationSettingDTO {
  @Builder
  public PagerDutySettingDTO(String accountId, String recipient) {
    super(accountId, recipient);
  }

  @Override
  public NotificationChannelType getType() {
    return NotificationChannelType.PAGERDUTY;
  }
}
