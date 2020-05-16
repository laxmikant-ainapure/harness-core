package io.harness.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import com.google.common.collect.ImmutableMap;

import io.harness.ambiance.Ambiance;
import io.harness.ambiance.Level;

import java.util.ArrayList;
import java.util.List;

public class AmbianceTestUtils {
  public static final String ACCOUNT_ID = generateUuid();
  public static final String APP_ID = generateUuid();
  public static final String EXECUTION_INSTANCE_ID = generateUuid();
  public static final String PHASE_RUNTIME_ID = generateUuid();
  public static final String PHASE_SETUP_ID = generateUuid();
  public static final String SECTION_RUNTIME_ID = generateUuid();
  public static final String SECTION_SETUP_ID = generateUuid();

  public static Ambiance buildAmbiance() {
    Level phaseLevel = Level.builder().runtimeId(PHASE_RUNTIME_ID).setupId(PHASE_SETUP_ID).build();
    Level sectionLevel = Level.builder().runtimeId(SECTION_RUNTIME_ID).setupId(SECTION_SETUP_ID).build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    return Ambiance.builder()
        .planExecutionId(EXECUTION_INSTANCE_ID)
        .setupAbstractions(
            ImmutableMap.<String, String>builder().put("accountId", ACCOUNT_ID).put("appId", APP_ID).build())
        .levels(levels)
        .build();
  }
}
