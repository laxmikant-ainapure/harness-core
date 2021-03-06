package io.harness.cdng.k8s;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sStepParametersCommandUnitsTest extends CategoryTest {
  private static final String[] DEFAULT_COMMAND_UNITS_NAME =
      new String[] {K8sCommandUnitConstants.FetchFiles, K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Prepare,
          K8sCommandUnitConstants.Apply, K8sCommandUnitConstants.WaitForSteadyState, K8sCommandUnitConstants.WrapUp};

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sDefaultCommandUnitsName() {
    assertCommandUnitsName(new K8sRollingStepParameters(), DEFAULT_COMMAND_UNITS_NAME);
    assertCommandUnitsName(new K8sCanaryStepParameters(), DEFAULT_COMMAND_UNITS_NAME);
    assertCommandUnitsName(new K8sBlueGreenStepParameters(), DEFAULT_COMMAND_UNITS_NAME);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sApplyParameters() {
    assertCommandUnitsName(new K8sApplyStepParameters(), DEFAULT_COMMAND_UNITS_NAME);
    assertCommandUnitsName(
        K8sApplyStepParameters.infoBuilder().skipSteadyStateCheck(ParameterField.createValueField(true)).build(),
        K8sCommandUnitConstants.FetchFiles, K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Prepare,
        K8sCommandUnitConstants.Apply, K8sCommandUnitConstants.WrapUp);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sBGSwapServicesParameters() {
    assertCommandUnitsName(new K8sBGSwapServicesStepParameters(), K8sCommandUnitConstants.SwapServiceSelectors);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sDeleteParameters() {
    assertCommandUnitsName(new K8sDeleteStepParameters(), K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sCanaryDeleteParameters() {
    assertCommandUnitsName(
        new K8sCanaryDeleteStepParameters(), K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8RollingRollbackParameters() {
    assertCommandUnitsName(new K8sRollingRollbackStepParameters(), K8sCommandUnitConstants.Init,
        K8sCommandUnitConstants.Rollback, K8sCommandUnitConstants.WaitForSteadyState);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testK8sScaleParameters() {
    assertCommandUnitsName(new K8sScaleStepParameter(), K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Scale,
        K8sCommandUnitConstants.WaitForSteadyState);
  }

  private void assertCommandUnitsName(K8sStepParameters stepParameters, String... expectedCommandUnits) {
    assertThat(stepParameters.getCommandUnits()).containsExactly(expectedCommandUnits);
  }
}