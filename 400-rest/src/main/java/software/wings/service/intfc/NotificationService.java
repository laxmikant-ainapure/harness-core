package software.wings.service.intfc;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;

import software.wings.beans.Notification;
import software.wings.beans.NotificationAction.NotificationActionType;
import software.wings.beans.NotificationRule;
import software.wings.service.intfc.ownership.OwnedByApplication;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 7/22/16.
 */
public interface NotificationService extends OwnedByApplication {
  /**
   * List page response.
   *
   * @param pageRequest the page request
   * @return the page response
   */
  PageResponse<Notification> list(PageRequest<Notification> pageRequest);

  /**
   * Get notification.
   *
   * @param appId          the app id
   * @param notificationId the notification id
   * @return the notification
   */
  Notification get(@NotEmpty String appId, @NotEmpty String notificationId);

  /**
   * Act notification.
   *
   * @param appId          the app id
   * @param notificationId the notification id
   * @param actionType     the action type
   * @return the notification
   */
  Notification act(@NotEmpty String appId, @NotEmpty String notificationId, @NotNull NotificationActionType actionType);

  /**
   * Send notification async.
   *
   * @param notification      the notification
   * @param notificationRules the notification rules
   */
  @ValidationGroups(Create.class)
  void sendNotificationAsync(@Valid Notification notification, List<NotificationRule> notificationRules);

  /**
   * Send notification async.
   *
   * @param notification the notification
   */
  @ValidationGroups(Create.class) void sendNotificationAsync(@Valid Notification notification);

  /**
   * Mark notification completed.
   *
   * @param appId          the app id
   * @param notificationId the notification id
   */
  void markNotificationCompleted(@NotEmpty String appId, @NotEmpty String notificationId);

  void sendNotificationToTriggeredByUserOnly(Notification notification, EmbeddedUser embeddedUser);
}
