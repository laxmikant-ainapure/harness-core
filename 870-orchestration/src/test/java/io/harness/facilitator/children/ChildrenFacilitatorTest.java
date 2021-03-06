package io.harness.facilitator.children;

import static io.harness.pms.contracts.execution.ExecutionMode.CHILDREN;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.facilitator.DefaultFacilitatorParams;
import io.harness.pms.sdk.core.facilitator.FacilitatorResponse;
import io.harness.pms.sdk.core.facilitator.chilidren.ChildrenFacilitator;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChildrenFacilitatorTest extends OrchestrationTestBase {
  @Inject private ChildrenFacilitator childrenFacilitator;
  @Inject private KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFacilitate() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    byte[] parameters = kryoSerializer.asBytes(DefaultFacilitatorParams.builder().build());
    FacilitatorResponse response = childrenFacilitator.facilitate(ambiance, null, parameters, null);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionMode()).isEqualTo(CHILDREN);
    assertThat(response.getInitialWait()).isEqualTo(Duration.ofSeconds(0));
  }
}
