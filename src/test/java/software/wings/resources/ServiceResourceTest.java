package software.wings.resources;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.wings.beans.ArtifactSource.ArtifactType.JAR;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.Service.Builder.aService;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import software.wings.beans.AppContainer;
import software.wings.beans.Graph;
import software.wings.beans.RestResponse;
import software.wings.beans.SearchFilter.Operator;
import software.wings.beans.Service;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.ServiceResourceService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

// TODO: Auto-generated Javadoc

/**
 * Created by anubhaw on 5/23/16.
 */
public class ServiceResourceTest {
  public static final String APP_ID = "APP_ID";
  private static final ServiceResourceService RESOURCE_SERVICE = mock(ServiceResourceService.class);

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
                                                       .addResource(new ServiceResource(RESOURCE_SERVICE))
                                                       .addProvider(WingsExceptionMapper.class)
                                                       .build();
  private static final String SERVICE_ID = "SERVICE_ID";
  private static final Service aSERVICE =
      aService()
          .withAppId(APP_ID)
          .withName("NAME")
          .withDescription("DESCRIPTION")
          .withArtifactType(JAR)
          .withAppContainer(AppContainer.AppContainerBuilder.anAppContainer().withAppId(APP_ID).build())
          .build();

  /**
   * Should list services.
   */
  @Test
  public void shouldListServices() {
    PageResponse<Service> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(aSERVICE));
    pageResponse.setTotal(1);
    when(RESOURCE_SERVICE.list(any(PageRequest.class))).thenReturn(pageResponse);
    RestResponse<PageResponse<Service>> restResponse =
        RESOURCES.client()
            .target("/services/?appId=" + APP_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<Service>>>() {});
    PageRequest<Service> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("50");
    pageRequest.addFilter("appId", APP_ID, Operator.EQ);
    verify(RESOURCE_SERVICE).list(pageRequest);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }

  /**
   * Should get service.
   */
  @Test
  public void shouldGetService() {
    when(RESOURCE_SERVICE.get(APP_ID, SERVICE_ID)).thenReturn(aSERVICE);
    RestResponse<Service> restResponse = RESOURCES.client()
                                             .target(format("/services/%s?appId=%s", SERVICE_ID, APP_ID))
                                             .request()
                                             .get(new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(RESOURCE_SERVICE).get(APP_ID, SERVICE_ID);
  }

  /**
   * Should save service.
   */
  @Test
  public void shouldSaveService() {
    when(RESOURCE_SERVICE.save(any(Service.class))).thenReturn(aSERVICE);
    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/services/?appId=%s", APP_ID))
            .request()
            .post(Entity.entity(aSERVICE, APPLICATION_JSON), new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(RESOURCE_SERVICE).save(aSERVICE);
  }

  /**
   * Should update service.
   */
  @Test
  public void shouldUpdateService() {
    Service service = aService().withAppId(APP_ID).withUuid(SERVICE_ID).build();
    when(RESOURCE_SERVICE.update(any(Service.class))).thenReturn(service);
    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/services/%s?appId=%s", SERVICE_ID, APP_ID))
            .request()
            .put(Entity.entity(service, APPLICATION_JSON), new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(RESOURCE_SERVICE).update(service);
  }

  /**
   * Should delete service.
   */
  @Test
  public void shouldDeleteService() {
    Response restResponse =
        RESOURCES.client().target(format("/services/%s?appId=%s", SERVICE_ID, APP_ID)).request().delete();
    assertThat(restResponse.getStatus()).isEqualTo(200);
    verify(RESOURCE_SERVICE).delete(APP_ID, SERVICE_ID);
  }

  /**
   * Should add command.
   */
  @Test
  public void shouldAddCommand() {
    when(RESOURCE_SERVICE.addCommand(eq(APP_ID), eq(SERVICE_ID), any(Graph.class))).thenReturn(aSERVICE);

    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/services/%s/commands?appId=%s", SERVICE_ID, APP_ID))
            .request()
            .post(Entity.entity(aGraph().build(), APPLICATION_JSON), new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(RESOURCE_SERVICE).addCommand(eq(APP_ID), eq(SERVICE_ID), any(Graph.class));
  }

  /**
   * Should delete command.
   */
  @Test
  public void shouldDeleteCommand() {
    when(RESOURCE_SERVICE.deleteCommand(APP_ID, SERVICE_ID, "START")).thenReturn(aSERVICE);

    RestResponse<Service> restResponse =
        RESOURCES.client()
            .target(format("/services/%s/commands/%s?appId=%s", SERVICE_ID, "START", APP_ID))
            .request()
            .delete(new GenericType<RestResponse<Service>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Service.class);
    verify(RESOURCE_SERVICE).deleteCommand(APP_ID, SERVICE_ID, "START");
  }
}
