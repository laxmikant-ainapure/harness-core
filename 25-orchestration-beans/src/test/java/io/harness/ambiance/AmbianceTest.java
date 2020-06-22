package io.harness.ambiance;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;

import io.harness.OrchestrationBeansTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.state.StepType;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

public class AmbianceTest extends OrchestrationBeansTest {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String EXECUTION_INSTANCE_ID = generateUuid();
  private static final String PHASE_RUNTIME_ID = generateUuid();
  private static final String PHASE_SETUP_ID = generateUuid();
  private static final String SECTION_RUNTIME_ID = generateUuid();
  private static final String SECTION_SETUP_ID = generateUuid();

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDeepCopy() {
    Ambiance ambiance = buildAmbiance();
    Ambiance copy = ambiance.deepCopy();

    assertThat(copy).isNotNull();
    assertThat(System.identityHashCode(copy.getLevels())).isNotEqualTo(System.identityHashCode(ambiance.getLevels()));
    assertThat(copy.getSetupAbstractions()).isEqualTo(ambiance.getSetupAbstractions());
    assertThat(copy.getLevels()).isEqualTo(ambiance.getLevels());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestAddLevelExecution() {
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    Level stepLevel = Level.builder().runtimeId(runtimeId).setupId(setupId).build();
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevels()).hasSize(2);
    ambiance.addLevel(stepLevel);
    assertThat(ambiance.getLevels()).hasSize(3);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCloneForNext() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevels()).hasSize(2);

    Ambiance clonedAmbiance = ambiance.cloneForFinish();
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(1);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldTestCloneForLevelsToKeep() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevels()).hasSize(2);

    Ambiance clonedAmbiance = ambiance.clone(0);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(0);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);

    clonedAmbiance = ambiance.clone(1);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(1);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);

    clonedAmbiance = ambiance.clone(2);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(2);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);

    clonedAmbiance = ambiance.clone(3);
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(2);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCloneForChild() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.getLevels()).hasSize(2);

    Ambiance clonedAmbiance = ambiance.cloneForChild();
    assertThat(clonedAmbiance).isNotNull();
    assertThat(clonedAmbiance.getLevels()).hasSize(2);
    assertThat(clonedAmbiance.getPlanExecutionId()).isEqualTo(EXECUTION_INSTANCE_ID);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestObtainCurrentRuntimeId() {
    Ambiance ambiance = buildAmbiance();
    assertThat(ambiance.obtainCurrentRuntimeId()).isEqualTo(SECTION_RUNTIME_ID);
  }

  private Ambiance buildAmbiance() {
    Level phaseLevel = Level.builder()
                           .runtimeId(PHASE_RUNTIME_ID)
                           .setupId(PHASE_SETUP_ID)
                           .stepType(StepType.builder().type("PHASE").build())
                           .build();
    Level sectionLevel = Level.builder()
                             .runtimeId(SECTION_RUNTIME_ID)
                             .setupId(SECTION_SETUP_ID)
                             .stepType(StepType.builder().type("SECTION").build())
                             .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    return Ambiance.builder()
        .planExecutionId(EXECUTION_INSTANCE_ID)
        .setupAbstractions(ImmutableMap.of("accountId", ACCOUNT_ID, "appId", APP_ID))
        .levels(levels)
        .build();
  }
}