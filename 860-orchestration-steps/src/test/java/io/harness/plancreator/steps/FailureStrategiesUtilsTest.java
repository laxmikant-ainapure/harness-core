package io.harness.plancreator.steps;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHENTICATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHORIZATION_ERROR;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationStepsTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FailureStrategiesUtilsTest extends OrchestrationStepsTestBase {
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapWhenFailureStrategiesAreMissing() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;
    actionConfigCollectionMap = FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, null);
    assertThat(actionConfigCollectionMap).isEmpty();
    actionConfigCollectionMap = FailureStrategiesUtils.priorityMergeFailureStrategies(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertThat(actionConfigCollectionMap).isEmpty();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategies() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder()
                    .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                    .action(
                        RetryFailureActionConfig.builder()
                            .specConfig(
                                RetryFailureSpecConfig.builder().retryCount(2).retryInterval(asList("2", "20")).build())
                            .build())
                    .build())
            .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder()
                    .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                    .action(
                        RetryFailureActionConfig.builder()
                            .specConfig(
                                RetryFailureSpecConfig.builder().retryCount(2).retryInterval(asList("2", "20")).build())
                            .build())
                    .build())
            .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(
        actionConfigCollectionMap.get(
            RetryFailureActionConfig.builder()
                .specConfig(RetryFailureSpecConfig.builder().retryCount(2).retryInterval(asList("2", "20")).build())
                .build()))
        .contains(FailureType.AUTHENTICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategiesWithSameTypeActionButDifferentActionConfiguration() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder()
                    .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                    .action(
                        RetryFailureActionConfig.builder()
                            .specConfig(
                                RetryFailureSpecConfig.builder().retryCount(2).retryInterval(asList("2", "20")).build())
                            .build())
                    .build())
            .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                           .action(RetryFailureActionConfig.builder()
                                       .specConfig(RetryFailureSpecConfig.builder()
                                                       .retryCount(4)
                                                       .retryInterval(Collections.singletonList("2"))
                                                       .build())
                                       .build())
                           .build())
            .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(
        actionConfigCollectionMap.get(
            RetryFailureActionConfig.builder()
                .specConfig(RetryFailureSpecConfig.builder().retryCount(2).retryInterval(asList("2", "20")).build())
                .build()))
        .contains(FailureType.AUTHENTICATION_FAILURE);
    assertThat(actionConfigCollectionMap.get(RetryFailureActionConfig.builder()
                                                 .specConfig(RetryFailureSpecConfig.builder()
                                                                 .retryCount(4)
                                                                 .retryInterval(Collections.singletonList("2"))
                                                                 .build())
                                                 .build()))
        .contains(FailureType.AUTHORIZATION_FAILURE);
  }
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategiesOnOtherErrors() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies =
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder()
                                                     .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                                                     .action(IgnoreFailureActionConfig.builder().build())
                                                     .build())
                                      .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies =
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder()
                                                     .errors(Collections.singletonList(NGFailureType.ANY_OTHER_ERRORS))
                                                     .action(AbortFailureActionConfig.builder().build())
                                                     .build())
                                      .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(actionConfigCollectionMap).isNotEmpty();
    Collection<FailureType> failureTypesAbort =
        actionConfigCollectionMap.get(AbortFailureActionConfig.builder().build());
    assertThat(failureTypesAbort).doesNotContain(FailureType.AUTHENTICATION_FAILURE);
    Collection<FailureType> failureTypesIgnore =
        actionConfigCollectionMap.get(IgnoreFailureActionConfig.builder().build());
    assertThat(failureTypesIgnore).containsOnly(FailureType.AUTHENTICATION_FAILURE);
  }
}