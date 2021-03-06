package io.harness.cvng.core.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.cvng.exception.ValidationError;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class SplunkResourceTest extends CvNextGenTestBase {
  @Inject private static SplunkResource splunkResource = new SplunkResource();
  @Mock private SplunkService splunkService;
  private String accountId;
  private String connectorId;
  private SplunkValidationResponse splunkValidationResponse;
  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(splunkResource).build();
  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    splunkValidationResponse =
        SplunkValidationResponse.builder().queryDurationMillis(Duration.ofDays(3).toMillis()).build();
    FieldUtils.writeField(splunkResource, "splunkService", splunkService, true);
    when(splunkService.getValidationResponse(
             eq(accountId), eq(connectorId), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(splunkValidationResponse);
    this.accountId = generateUuid();
    this.connectorId = generateUuid();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse_validResponse() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/splunk/validation")
                            .queryParam("accountId", accountId)
                            .queryParam("connectorIdentifier", connectorId)
                            .queryParam("orgIdentifier", "orgIdentifier")
                            .queryParam("projectIdentifier", "project")
                            .queryParam("requestGuid", generateUuid())
                            .queryParam("query", "exception")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse_nullQueryParam() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/splunk/validation")
                            .queryParam("accountId", accountId)
                            .queryParam("connectorIdentifier", connectorId)
                            .queryParam("requestGuid", generateUuid())
                            .queryParam("orgIdentifier", "orgIdentifier")
                            .queryParam("projectIdentifier", "project")
                            .queryParam("query", null)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    List<ValidationError> validationError = response.readEntity(new GenericType<List<ValidationError>>() {});
    assertThat(validationError).hasSize(2);
    assertThat(validationError.get(0).getField()).isEqualTo("query");
    assertThat(validationError.get(1).getField()).isEqualTo("query");

    assertThat(asList(validationError.get(0).getMessage(), validationError.get(1).getMessage()))
        .containsAll(asList("may not be null", "may not be empty"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidationResponse_emptyQuery() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/splunk/validation")
                            .queryParam("accountId", accountId)
                            .queryParam("connectorIdentifier", connectorId)
                            .queryParam("requestGuid", generateUuid())
                            .queryParam("orgIdentifier", "orgIdentifier")
                            .queryParam("query", "")
                            .queryParam("projectIdentifier", "project")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    List<ValidationError> validationError = response.readEntity(new GenericType<List<ValidationError>>() {});
    assertThat(validationError.get(0).getField()).isEqualTo("query");
    assertThat(validationError.get(0).getMessage()).isEqualTo("may not be empty");
  }
}
