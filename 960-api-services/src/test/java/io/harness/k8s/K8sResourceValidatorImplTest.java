package io.harness.k8s;

import static io.harness.k8s.K8sResourceValidator.DENIED_RESPOSE_FORMAT;
import static io.harness.rule.OwnerRule.UTSAV;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.rule.Owner;
import io.harness.threading.CurrentThreadExecutor;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1APIGroupBuilder;
import io.kubernetes.client.openapi.models.V1APIGroupListBuilder;
import io.kubernetes.client.openapi.models.V1ResourceAttributes;
import io.kubernetes.client.openapi.models.V1ResourceAttributesBuilder;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReviewSpec;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.util.ClientBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class K8sResourceValidatorImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  @Mock AuthorizationV1Api authorizationV1Api;
  @InjectMocks K8sResourceValidatorImpl k8sResourceValidator;

  private V1SubjectAccessReviewStatus v1SubjectAccessReviewStatus;
  private V1ResourceAttributes v1ResourceAttributes;
  private V1SelfSubjectAccessReviewSpec v1SelfSubjectAccessReviewSpec;
  private V1SelfSubjectAccessReview v1SelfSubjectAccessReview;
  private List<V1ResourceAttributes> v1ResourceAttributesList;
  private final ExecutorService executorService = new CurrentThreadExecutor();
  private ApiClient apiClient;

  final String GROUP = "apps";
  final String VERB = "delete";
  final String RESOURCE = "pods";
  final String REASON = "REASON";

  @Before
  public void setup() throws ApiException {
    on(k8sResourceValidator).set("executorService", executorService);

    apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build();

    v1ResourceAttributes =
        new V1ResourceAttributesBuilder().withGroup(GROUP).withResource(RESOURCE).withVerb(VERB).build();

    v1SubjectAccessReviewStatus = new V1SubjectAccessReviewStatus().allowed(true);
    v1SelfSubjectAccessReviewSpec = new V1SelfSubjectAccessReviewSpec().resourceAttributes(v1ResourceAttributes);

    v1SelfSubjectAccessReview =
        new V1SelfSubjectAccessReview().spec(v1SelfSubjectAccessReviewSpec).status(v1SubjectAccessReviewStatus);

    v1ResourceAttributesList = Collections.singletonList(v1ResourceAttributes);

    doReturn(v1SelfSubjectAccessReview)
        .when(authorizationV1Api)
        .createSelfSubjectAccessReview(any(V1SelfSubjectAccessReview.class), any(), any(), any());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithStringInput() throws ApiException {
    V1SubjectAccessReviewStatus status = k8sResourceValidator.validate(authorizationV1Api, GROUP, VERB, RESOURCE);
    assertThat(status).isNotNull();
    assertThat(status.getAllowed()).isTrue();
    assertThat(status.getReason()).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithV1ResourceAttributesPermissionGranted() throws ApiException {
    V1SubjectAccessReviewStatus status = k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributes);
    assertThat(status).isNotNull();
    assertThat(status.getAllowed()).isTrue();
    assertThat(status.getReason()).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithV1ResourceAttributesPermissionNotGranted() throws ApiException {
    v1SubjectAccessReviewStatus = v1SubjectAccessReviewStatus.allowed(false).reason(REASON);

    V1SubjectAccessReviewStatus status = k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributes);
    assertThat(status).isNotNull();
    assertThat(status.getAllowed()).isFalse();
    assertThat(status.getReason()).isEqualTo(REASON);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithListV1ResourceAttributesNotGranted() {
    v1SubjectAccessReviewStatus = v1SubjectAccessReviewStatus.allowed(false).reason(REASON);

    List<V1SubjectAccessReviewStatus> result =
        k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributesList, 10);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getAllowed()).isFalse();
    assertThat(result.get(0).getReason()).isEqualTo(REASON);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithListV1ResourceAttributesGranted() {
    List<V1SubjectAccessReviewStatus> result =
        k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributesList, 10);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getAllowed()).isTrue();
    assertThat(result.get(0).getReason()).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void validateWithListV1ResourceAttributesShouldCompleteWithoutException() {
    k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributesList, -1);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldExtractResponseAsStringWhenAllPermissionGranted() {
    String response = K8sResourceValidatorImpl.buildResponse(
        Collections.singletonList(v1ResourceAttributes), Collections.singletonList(v1SubjectAccessReviewStatus));

    assertThat(response).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldBuildResponseAsStringWhenOnePermissionNotGranted() {
    v1SubjectAccessReviewStatus = v1SubjectAccessReviewStatus.allowed(false).reason(REASON);

    String response = K8sResourceValidatorImpl.buildResponse(
        v1ResourceAttributesList, Collections.singletonList(v1SubjectAccessReviewStatus));

    assertThat(response).isNotNull().isEqualTo(String.format(DENIED_RESPOSE_FORMAT, v1ResourceAttributes.getVerb(),
        v1ResourceAttributes.getResource(), v1ResourceAttributes.getGroup()));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldBuildResponseAsStringWhenOnePermissionGranted() {
    String response = K8sResourceValidatorImpl.buildResponse(
        Collections.singletonList(v1ResourceAttributes), Collections.singletonList(v1SubjectAccessReviewStatus));

    assertThat(response).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldBuildv1ResourceAttributesList() {
    List<V1ResourceAttributes> list = K8sResourceValidatorImpl.v1ResourceAttributesListBuilder(
        new String[] {GROUP}, new String[] {RESOURCE, RESOURCE}, new String[] {VERB, VERB, VERB});

    assertThat(list).isNotNull();
    assertThat(list.size()).isEqualTo(6);
    assertThat(list.get(0)).isEqualTo(v1ResourceAttributes);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldHandleNullAsValidateError() throws ApiException {
    doThrow(new ApiException("")).when(authorizationV1Api).createSelfSubjectAccessReview(any(), any(), any(), any());
    List<V1SubjectAccessReviewStatus> statuses =
        k8sResourceValidator.validate(authorizationV1Api, v1ResourceAttributesList, 10);

    String response = K8sResourceValidatorImpl.buildResponse(Collections.singletonList(v1ResourceAttributes), statuses);

    assertThat(statuses).isNotNull();
    assertThat(statuses.size()).isEqualTo(1);
    assertThat(response).isNotNull().isEqualTo(DENIED_RESPOSE_FORMAT, v1ResourceAttributes.getVerb(),
        v1ResourceAttributes.getResource(), v1ResourceAttributes.getGroup());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateMetricsServer() throws ApiException {
    stubFor(get(urlPathEqualTo("/apis"))
                .willReturn(aResponse().withStatus(200).withBody(new Gson().toJson(
                    new V1APIGroupListBuilder()
                        .withGroups(ImmutableList.of(new V1APIGroupBuilder().withName("metrics.k8s.io").build()))
                        .build()))));

    assertThat(k8sResourceValidator.validateMetricsServer(apiClient)).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenMetricsServerAbsent() throws ApiException {
    stubFor(get(urlPathEqualTo("/apis"))
                .willReturn(aResponse().withStatus(200).withBody(
                    new Gson().toJson(new V1APIGroupListBuilder().withGroups(new ArrayList<>()).build()))));

    assertThat(k8sResourceValidator.validateMetricsServer(apiClient)).isFalse();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void throwErrorWhenValidatingMetricServer() {
    stubFor(get(urlPathEqualTo("/apis")).willReturn(aResponse().withStatus(404).withBody("404 page not found")));

    assertThatThrownBy(() -> k8sResourceValidator.validateMetricsServer(apiClient))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Not Found");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEPermissions2AllPermissionsGranted() {
    stubFor(post(urlPathEqualTo("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews"))
                .willReturn(aResponse().withStatus(200).withBody(new Gson().toJson(v1SelfSubjectAccessReview))));

    List<CEK8sDelegatePrerequisite.Rule> requiredPermissions = k8sResourceValidator.validateCEPermissions2(apiClient);

    WireMock.verify(postRequestedFor(urlPathEqualTo("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")));
    assertThat(requiredPermissions).isNotNull();
    assertThat(requiredPermissions).isEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEPermissions2NoneGranted() {
    v1SubjectAccessReviewStatus = v1SubjectAccessReviewStatus.allowed(false).reason(REASON);

    stubFor(post(urlPathEqualTo("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews"))
                .willReturn(aResponse().withStatus(200).withBody(new Gson().toJson(v1SelfSubjectAccessReview))));

    List<CEK8sDelegatePrerequisite.Rule> requiredPermissions = k8sResourceValidator.validateCEPermissions2(apiClient);

    WireMock.verify(postRequestedFor(urlPathEqualTo("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")));
    assertThat(requiredPermissions).isNotNull();
    assertThat(requiredPermissions).isNotEmpty();
    assertThat(requiredPermissions.get(0).getMessage()).isEqualTo(REASON);
  }
}
