package software.wings.resources;

import static software.wings.beans.Base.GLOBAL_APP_ID;
import static software.wings.beans.SearchFilter.Operator.EQ;
import static software.wings.beans.SortOrder.Builder.aSortOrder;

import com.google.common.base.Strings;
import com.google.inject.Inject;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import software.wings.beans.Notification;
import software.wings.beans.NotificationAction.NotificationActionType;
import software.wings.beans.RestResponse;
import software.wings.beans.SortOrder.OrderType;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.NotificationService;

import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Created by anubhaw on 7/22/16.
 */
@Api("/notifications")
@Path("/notifications")
@AuthRule
@Produces("application/json")
@Timed
@ExceptionMetered
public class NotificationResource {
  @Inject private NotificationService notificationService;

  /**
   * List rest response.
   *
   * @param appId       the app id
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  public RestResponse<PageResponse<Notification>> list(
      @QueryParam("appId") String appId, @BeanParam PageRequest<Notification> pageRequest) {
    if (!Strings.isNullOrEmpty(appId)) {
      pageRequest.addFilter("appId", appId, EQ);
    }
    pageRequest.addOrder(aSortOrder().withField("complete", OrderType.ASC).build());
    return new RestResponse<>(notificationService.list(pageRequest));
  }

  /**
   * Get rest response.
   *
   * @param appId          the app id
   * @param notificationId the notification id
   * @return the rest response
   */
  @GET
  @Path("{notificationId}")
  public RestResponse<Notification> get(@DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId,
      @QueryParam("notificationId") String notificationId) {
    return new RestResponse<>(notificationService.get(appId, notificationId));
  }

  /**
   * Update rest response.
   *
   * @param appId          the app id
   * @param notificationId the notification id
   * @return the rest response
   */
  @POST
  @Path("{notificationId}/action/{type}")
  public RestResponse<Notification> act(@DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId,
      @QueryParam("notificationId") String notificationId, @QueryParam("type") NotificationActionType actionType) {
    return new RestResponse<>(notificationService.act(appId, notificationId, actionType));
  }
}
