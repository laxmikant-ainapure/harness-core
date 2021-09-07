package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.BuilderFactory.Context;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceRef;
import io.harness.cvng.core.entities.ServiceDependency;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceDependencyServiceImplTest extends CvNextGenTestBase {
  @Inject private ServiceDependencyService serviceDependencyService;

  private BuilderFactory builderFactory;
  private Context context;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    context = builderFactory.getContext();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateOrDelete_empty() {
    createOrDeleteFromContext(context, new HashSet<>());
    Set<ServiceRef> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateOrDelete_create() {
    Set<ServiceRef> serviceRefs =
        Sets.newHashSet(ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    createOrDeleteFromContext(context, serviceRefs);
    Set<ServiceRef> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(serviceRefs);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateOrDelete_existingDependencies() {
    List<ServiceRef> serviceRefs = generateRandomRefs(4);
    createOrDeleteFromContext(context, new HashSet<>(serviceRefs));
    Set<ServiceRef> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(new HashSet<>(serviceRefs));

    serviceRefs = Lists.newArrayList(
        ServiceRef.builder().monitoredServiceIdentifier(serviceRefs.get(0).getMonitoredServiceIdentifier()).build(),
        ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
        ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    createOrDeleteFromContext(context, new HashSet<>(serviceRefs));
    Set<ServiceRef> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>(serviceRefs));
    assertThat(updatedRefs).isNotEqualTo(newRefs);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testDeleteDependenciesForService() {
    Set<ServiceRef> serviceRefs = new HashSet<>(generateRandomRefs(3));
    createOrDeleteFromContext(context, serviceRefs);
    Set<ServiceRef> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(serviceRefs);

    serviceDependencyService.deleteDependenciesForService(context.getProjectParams(), context.getServiceIdentifier());
    Set<ServiceRef> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetServiceDependencies() {
    Set<ServiceRef> serviceRefs = new HashSet<>(generateRandomRefs(3));
    createOrDeleteFromContext(context, serviceRefs);

    List<ServiceDependency> serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);
  }

  private void createOrDeleteFromContext(Context context, Set<ServiceRef> serviceRefs) {
    serviceDependencyService.updateDependencies(
        context.getProjectParams(), context.getServiceIdentifier(), serviceRefs);
  }

  private List<ServiceRef> generateRandomRefs(int num) {
    List<ServiceRef> random = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      random.add(ServiceRef.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    }
    return random;
  }
}
