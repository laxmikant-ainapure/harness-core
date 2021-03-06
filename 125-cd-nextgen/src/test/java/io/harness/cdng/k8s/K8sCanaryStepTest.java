package io.harness.cdng.k8s;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.NGInstanceUnitType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class K8sCanaryStepTest extends AbstractK8sStepExecutorTestBase {
  @InjectMocks private K8sCanaryStep k8sCanaryStep;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    CountInstanceSelection instanceSelection = new CountInstanceSelection();
    instanceSelection.setCount(ParameterField.createValueField(10));
    K8sCanaryStepParameters stepParameters = new K8sCanaryStepParameters();
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));
    stepParameters.setTimeout(ParameterField.createValueField("30m"));
    stepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(instanceSelection).build());

    K8sCanaryDeployRequest request = executeTask(stepParameters, K8sCanaryDeployRequest.class);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.getInstances()).isEqualTo(10);
    assertThat(request.getInstanceUnitType()).isEqualTo(NGInstanceUnitType.COUNT);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getManifestDelegateConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.CANARY_DEPLOY);
    assertThat(request.isSkipDryRun()).isTrue();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskNullParameterFields() {
    PercentageInstanceSelection instanceSelection = new PercentageInstanceSelection();
    instanceSelection.setPercentage(ParameterField.createValueField(90));
    K8sCanaryStepParameters stepParameters = new K8sCanaryStepParameters();
    stepParameters.setSkipDryRun(ParameterField.ofNull());
    stepParameters.setTimeout(ParameterField.ofNull());
    stepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Percentage).spec(instanceSelection).build());

    K8sCanaryDeployRequest request = executeTask(stepParameters, K8sCanaryDeployRequest.class);
    assertThat(request.isSkipDryRun()).isFalse();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(K8sStepHelper.getTimeout(stepParameters));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateMissingInstanceSelection() {
    K8sCanaryStepParameters canaryStepParameters = K8sCanaryStepParameters.infoBuilder().build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");

    canaryStepParameters.setInstanceSelection(InstanceSelectionWrapper.builder().build());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");

    canaryStepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).build());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateMissingInstanceSelectionValue() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    InstanceSelectionWrapper instanceSelection =
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(new CountInstanceSelection()).build();
    K8sCanaryStepParameters canaryStepParameters =
        K8sCanaryStepParameters.infoBuilder().instanceSelection(instanceSelection).build();
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection count value is mandatory");

    instanceSelection.setType(K8sInstanceUnitType.Percentage);
    instanceSelection.setSpec(new PercentageInstanceSelection());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection percentage value is mandatory");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateInvalidInstanceSelectionValue() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    CountInstanceSelection countSpec = new CountInstanceSelection();
    PercentageInstanceSelection percentageSpec = new PercentageInstanceSelection();
    countSpec.setCount(ParameterField.createValueField(0));
    percentageSpec.setPercentage(ParameterField.createValueField(0));
    InstanceSelectionWrapper instanceSelection =
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(countSpec).build();
    K8sCanaryStepParameters canaryStepParameters =
        K8sCanaryStepParameters.infoBuilder().instanceSelection(instanceSelection).build();
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection count value cannot be less than 1");

    instanceSelection.setType(K8sInstanceUnitType.Percentage);
    instanceSelection.setSpec(percentageSpec);

    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, canaryStepParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection percentage value cannot be less than 1");
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return k8sCanaryStep;
  }
}