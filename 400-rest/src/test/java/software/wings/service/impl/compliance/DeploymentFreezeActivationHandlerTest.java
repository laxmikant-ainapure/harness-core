package software.wings.service.impl.compliance;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.mockito.Matchers.any;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(Module._950_EVENTS_API)
public class DeploymentFreezeActivationHandlerTest extends WingsBaseTest {
  @Mock DeploymentFreezeUtils deploymentFreezeUtils;
  @Inject @InjectMocks DeploymentFreezeActivationHandler deploymentFreezeActivationHandler;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDoNothingForNoFreezeWindows() {
    deploymentFreezeActivationHandler.handle(GovernanceConfig.builder().build());
    Mockito.verify(deploymentFreezeUtils, Mockito.never()).handleActivationEvent(any(), Matchers.anyString());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDoNothingForNoMatchingWindows() {
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .timeRangeBasedFreezeConfigs(Arrays.asList(
                TimeRangeBasedFreezeConfig.builder().timeRange(new TimeRange(1000, 2000, "Asia/Kolkatta")).build()))
            .build();
    deploymentFreezeActivationHandler.handle(governanceConfig);
    Mockito.verify(deploymentFreezeUtils, Mockito.never()).handleActivationEvent(any(), Matchers.anyString());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldHandleActivationEventForMatchingWindows() {
    long currentTime = System.currentTimeMillis();
    TimeRangeBasedFreezeConfig window1 =
        TimeRangeBasedFreezeConfig.builder().timeRange(new TimeRange(1000, 2000, "Asia/Kolkatta")).build();
    TimeRangeBasedFreezeConfig window2 = TimeRangeBasedFreezeConfig.builder()
                                             .timeRange(new TimeRange(currentTime, currentTime + 2000, "Asia/Kolkatta"))
                                             .build();
    TimeRangeBasedFreezeConfig window3 = TimeRangeBasedFreezeConfig.builder()
                                             .timeRange(new TimeRange(currentTime, currentTime + 2000, "Asia/Kolkatta"))
                                             .build();
    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(Arrays.asList(window1, window2, window3))
                                            .build();
    deploymentFreezeActivationHandler.handle(governanceConfig);
    Mockito.verify(deploymentFreezeUtils, Mockito.times(2)).handleActivationEvent(any(), Matchers.eq(ACCOUNT_ID));
  }
}