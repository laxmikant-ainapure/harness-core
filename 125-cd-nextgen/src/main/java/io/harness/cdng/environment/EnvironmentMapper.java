package io.harness.cdng.environment;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.yaml.EnvironmentYaml;

import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class EnvironmentMapper {
  public EnvironmentOutcome toOutcome(@Nonnull EnvironmentYaml environmentYaml) {
    return EnvironmentOutcome.builder()
        .identifier(environmentYaml.getIdentifier())
        .name(environmentYaml.getName() != null ? environmentYaml.getName() : "")
        .description(environmentYaml.getDescription() != null ? environmentYaml.getDescription().getValue() : "")
        .tags(environmentYaml.getTags())
        .environmentType(environmentYaml.getType())
        .build();
  }
}
