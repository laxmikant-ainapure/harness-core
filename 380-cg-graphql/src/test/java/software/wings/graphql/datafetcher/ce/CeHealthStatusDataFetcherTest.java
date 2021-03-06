package software.wings.graphql.datafetcher.ce;

import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.health.CEClusterHealth;
import io.harness.ccm.health.CEError;
import io.harness.ccm.health.CEHealthStatus;
import io.harness.ccm.health.HealthStatusService;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.cloudprovider.CEHealthStatusDTO;
import software.wings.graphql.schema.type.cloudProvider.QLKubernetesClusterCloudProvider;

import graphql.schema.DataFetchingEnvironment;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CeHealthStatusDataFetcherTest extends CategoryTest {
  private String cloudProviderId = "CLOUD_PROVIDER_ID";
  private String clusterId = "CLUSTER_ID";
  private String errorMessage = String.format(CEError.PERPETUAL_TASK_NOT_ASSIGNED.getMessage(), clusterId);

  private CEClusterHealth clusterHealthStatus = CEClusterHealth.builder().messages(Arrays.asList(errorMessage)).build();

  private List<CEClusterHealth> clusterHealthStatusList = Arrays.asList(clusterHealthStatus);
  private CEHealthStatus ceHealthStatus =
      CEHealthStatus.builder().isHealthy(false).clusterHealthStatusList(clusterHealthStatusList).build();

  @Mock private DataFetchingEnvironment environment;
  private QLKubernetesClusterCloudProvider k8sCloudProvider =
      QLKubernetesClusterCloudProvider.builder().id(cloudProviderId).build();
  private CEHealthStatusDTO ceHealthStatusDTO;

  @Mock private HealthStatusService healthStatusService;
  @InjectMocks CeHealthStatusDataFetcher ceHealthStatusDataFetcher;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    ceHealthStatusDTO =
        CEHealthStatusDTO.builder().isHealthy(false).clusterHealthStatusList(clusterHealthStatusList).build();
    when(environment.getSource()).thenReturn(k8sCloudProvider);
    when(healthStatusService.getHealthStatus(eq(cloudProviderId), eq(false))).thenReturn(ceHealthStatus);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetCEHealthStatusDTO() throws Exception {
    CEHealthStatusDTO healthStatusDTO = ceHealthStatusDataFetcher.get(environment);
    verify(healthStatusService).getHealthStatus(eq(cloudProviderId), eq(false));
    assertThat(healthStatusDTO).isEqualToComparingFieldByField(ceHealthStatusDTO);
  }
}
