package io.harness.delegate.k8s;

import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesContainerService;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8sSwapServiceSelectorsBaseHandlerTest extends CategoryTest {
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private LogCallback logCallback;

  @InjectMocks private K8sSwapServiceSelectorsBaseHandler k8sSwapServiceSelectorsBaseHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private Service createService(String serviceName, Map<String, String> labelSelectors) {
    ServiceSpecBuilder spec = new ServiceSpecBuilder().withSelector(labelSelectors);

    return new ServiceBuilder().withNewMetadata().withName(serviceName).endMetadata().withSpec(spec.build()).build();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void smokeTest() {
    Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    Service service2 = createService("service2", ImmutableMap.of("label", "B"));

    when(kubernetesContainerService.getServiceFabric8(any(), eq(service1.getMetadata().getName())))
        .thenReturn(service1);
    when(kubernetesContainerService.getServiceFabric8(any(), eq(service2.getMetadata().getName())))
        .thenReturn(service2);
    when(kubernetesContainerService.createOrReplaceService(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[1]);

    boolean success =
        k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback);
    assertThat(success).isTrue();

    ArgumentCaptor<Service> serviceArgumentCaptor = ArgumentCaptor.forClass(Service.class);

    verify(kubernetesContainerService, times(2)).getServiceFabric8(any(), any());

    verify(kubernetesContainerService, times(2)).createOrReplaceService(eq(null), serviceArgumentCaptor.capture());

    Service updatedService1 = serviceArgumentCaptor.getAllValues().get(0);
    assertThat(updatedService1.getMetadata().getName()).isEqualTo("service1");
    assertThat(updatedService1.getSpec().getSelector().get("label")).isEqualTo("B");

    Service updatedService2 = serviceArgumentCaptor.getAllValues().get(1);
    assertThat(updatedService2.getMetadata().getName()).isEqualTo("service2");
    assertThat(updatedService2.getSpec().getSelector().get("label")).isEqualTo("A");
  }
}
