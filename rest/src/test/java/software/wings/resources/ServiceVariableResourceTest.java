package software.wings.resources;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.Whitebox.setInternalState;
import static software.wings.beans.Base.GLOBAL_ENV_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.RestResponse;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.Type;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.utils.ResourceTestRule;
import software.wings.utils.WingsTestConstants;

import java.util.UUID;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

/**
 * Created by peeyushaggarwal on 9/27/16.
 */
public class ServiceVariableResourceTest {
  private static final String ACCOUNT_ID = UUID.randomUUID().toString();
  private static final ServiceVariableService VARIABLE_SERVICE = mock(ServiceVariableService.class);
  private static final AppService APP_SERVICE = mock(AppService.class);
  private static final ServiceVariableResource VARIABLE_RESOURCE = new ServiceVariableResource();

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(VARIABLE_RESOURCE).addProvider(WingsExceptionMapper.class).build();

  private static final ServiceVariable SERVICE_VARIABLE = ServiceVariable.builder()
                                                              .envId(GLOBAL_ENV_ID)
                                                              .entityType(EntityType.ENVIRONMENT)
                                                              .entityId(TEMPLATE_ID)
                                                              .templateId(TEMPLATE_ID)
                                                              .type(Type.TEXT)
                                                              .value("8080".toCharArray())
                                                              .accountId(ACCOUNT_ID)
                                                              .build();
  static {
    when(VARIABLE_SERVICE.save(anyObject())).then(AdditionalAnswers.returnsFirstArg());
    when(APP_SERVICE.get(anyString()))
        .thenReturn(Application.Builder.anApplication().withAccountId(ACCOUNT_ID).build());
    setInternalState(VARIABLE_RESOURCE, "serviceVariablesService", VARIABLE_SERVICE);
    setInternalState(VARIABLE_RESOURCE, "appService", APP_SERVICE);
    SERVICE_VARIABLE.setUuid(WingsTestConstants.SERVICE_VARIABLE_ID);
    SERVICE_VARIABLE.setAppId(APP_ID);
  }

  /**
   * Should list variables.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldListVariables() throws Exception {
    PageResponse<ServiceVariable> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(SERVICE_VARIABLE));
    pageResponse.setTotal(1l);
    when(VARIABLE_SERVICE.list(any(PageRequest.class), anyBoolean())).thenReturn(pageResponse);
    RestResponse<PageResponse<ServiceVariable>> restResponse =
        RESOURCES.client()
            .target("/service-variables/?appId=" + APP_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<ServiceVariable>>>() {});
    PageRequest<ServiceVariable> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    verify(VARIABLE_SERVICE).list(pageRequest, true);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }

  /**
   * Should save service variable.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldSaveServiceVariable() throws Exception {
    when(VARIABLE_SERVICE.save(any(ServiceVariable.class))).thenReturn(SERVICE_VARIABLE);
    RestResponse<ServiceVariable> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/?appId=%s", APP_ID))
            .request()
            .post(entity(SERVICE_VARIABLE, APPLICATION_JSON), new GenericType<RestResponse<ServiceVariable>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(ServiceVariable.class);
    verify(VARIABLE_SERVICE).save(SERVICE_VARIABLE);
  }

  /**
   * Should get variable.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldGetVariable() throws Exception {
    when(VARIABLE_SERVICE.get(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID, true)).thenReturn(SERVICE_VARIABLE);
    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, APP_ID))
            .request()
            .get(new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(VARIABLE_SERVICE).get(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID, true);
  }

  /**
   * Should update service variable.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldUpdateServiceVariable() throws Exception {
    when(VARIABLE_SERVICE.update(any(ServiceVariable.class))).thenReturn(SERVICE_VARIABLE);
    RestResponse<ServiceVariable> restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, APP_ID))
            .request()
            .put(entity(SERVICE_VARIABLE, APPLICATION_JSON), new GenericType<RestResponse<ServiceVariable>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(ServiceVariable.class);
    verify(VARIABLE_SERVICE).update(SERVICE_VARIABLE);
  }

  /**
   * Should delete service variable.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldDeleteServiceVariable() throws Exception {
    Response restResponse =
        RESOURCES.client()
            .target(format("/service-variables/%s?appId=%s", WingsTestConstants.SERVICE_VARIABLE_ID, APP_ID))
            .request()
            .delete();
    assertThat(restResponse.getStatus()).isEqualTo(200);
    verify(VARIABLE_SERVICE).delete(APP_ID, WingsTestConstants.SERVICE_VARIABLE_ID);
  }

  /**
   * Shoudl delete by entity.
   *
   * @throws Exception the exception
   */
  @Test
  public void shoudlDeleteByEntity() throws Exception {
    Response restResponse = RESOURCES.client()
                                .target(format("/service-variables/entity/%s?appId=%s", TEMPLATE_ID, APP_ID))
                                .request()
                                .delete();
    assertThat(restResponse.getStatus()).isEqualTo(200);
    verify(VARIABLE_SERVICE).pruneByService(APP_ID, TEMPLATE_ID);
  }
}
