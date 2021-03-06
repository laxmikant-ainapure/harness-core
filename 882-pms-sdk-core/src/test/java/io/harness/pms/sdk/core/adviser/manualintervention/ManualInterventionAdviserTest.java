package io.harness.pms.sdk.core.adviser.manualintervention;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.AdvisingEvent.AdvisingEventBuilder;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class ManualInterventionAdviserTest extends PmsSdkCoreTestBase {
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE = StepType.newBuilder().setType("DUMMY").build();

  @InjectMocks @Inject ManualInterventionAdviser manualInterventionAdviser;

  @Inject KryoSerializer kryoSerializer;

  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = AmbianceTestUtils.buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .addLevels(Level.newBuilder()
                                  .setSetupId(NODE_SETUP_ID)
                                  .setRuntimeId(NODE_EXECUTION_ID)
                                  .setIdentifier(NODE_IDENTIFIER)
                                  .setStepType(DUMMY_STEP_TYPE)
                                  .build())
                   .build();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestOnAdviseEvent() {
    NodeExecutionProto nodeExecutionProto = NodeExecutionProto.newBuilder().setAmbiance(ambiance).build();
    AdvisingEvent advisingEvent =
        AdvisingEvent.<ManualInterventionAdviserParameters>builder()
            .nodeExecution(nodeExecutionProto)
            .toStatus(Status.FAILED)
            .adviserParameters(kryoSerializer.asBytes(ManualInterventionAdviserParameters.builder().build()))
            .build();
    AdviserResponse adviserResponse = manualInterventionAdviser.onAdviseEvent(advisingEvent);
    assertThat(adviserResponse.getType()).isEqualTo(AdviseType.INTERVENTION_WAIT);
    assertThat(adviserResponse.getInterventionWaitAdvise()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCanAdvise() {
    AdvisingEventBuilder advisingEventBuilder =
        AdvisingEvent.builder()
            .toStatus(Status.FAILED)
            .fromStatus(Status.RUNNING)
            .adviserParameters(
                kryoSerializer.asBytes(ManualInterventionAdviserParameters.builder()
                                           .applicableFailureTypes(EnumSet.of(FailureType.AUTHENTICATION_FAILURE))
                                           .build()));

    NodeExecutionProto nodeExecutionAuthFail =
        NodeExecutionProto.newBuilder()
            .setAmbiance(ambiance)
            .setFailureInfo(FailureInfo.newBuilder()
                                .setErrorMessage("Auth Error")
                                .addAllFailureTypes(EnumSet.of(FailureType.AUTHENTICATION_FAILURE))
                                .build())
            .build();

    AdvisingEvent authFailEvent = advisingEventBuilder.nodeExecution(nodeExecutionAuthFail).build();

    boolean canAdvise = manualInterventionAdviser.canAdvise(authFailEvent);
    assertThat(canAdvise).isTrue();

    NodeExecutionProto nodeExecutionAppFail =
        NodeExecutionProto.newBuilder()
            .setAmbiance(ambiance)
            .setFailureInfo(FailureInfo.newBuilder()
                                .setErrorMessage("Application Error")
                                .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                                .build())
            .build();

    AdvisingEvent appFailEvent = advisingEventBuilder.nodeExecution(nodeExecutionAppFail).build();
    canAdvise = manualInterventionAdviser.canAdvise(appFailEvent);
    assertThat(canAdvise).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCanAdviseWithFromStatus() {
    NodeExecutionProto nodeExecutionProto = NodeExecutionProto.newBuilder().setAmbiance(ambiance).build();
    AdvisingEventBuilder advisingEventBuilder =
        AdvisingEvent.builder()
            .nodeExecution(nodeExecutionProto)
            .toStatus(Status.FAILED)
            .fromStatus(Status.INTERVENTION_WAITING)
            .adviserParameters(
                kryoSerializer.asBytes(ManualInterventionAdviserParameters.builder()
                                           .applicableFailureTypes(EnumSet.of(FailureType.AUTHENTICATION_FAILURE))
                                           .build()));

    boolean canAdvise = manualInterventionAdviser.canAdvise(advisingEventBuilder.build());
    assertThat(canAdvise).isFalse();
  }
}
