package io.harness.cdng.creator;

import io.harness.cdng.environment.EnvironmentOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo.CDPipelineModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo.CDStageModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.InfraExecutionSummary;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.service.steps.ServiceStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngpipeline.artifact.bean.ArtifactOutcome;
import io.harness.ngpipeline.pipeline.executions.beans.ServiceExecutionSummary;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class CDNGModuleInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Inject OutcomeService outcomeService;

  public ServiceExecutionSummary.ArtifactsSummary mapArtifactsOutcomeToSummary(ServiceOutcome serviceOutcome) {
    ServiceExecutionSummary.ArtifactsSummary.ArtifactsSummaryBuilder artifactsSummaryBuilder =
        ServiceExecutionSummary.ArtifactsSummary.builder();

    if (serviceOutcome.getArtifactsResult().getPrimary() != null) {
      artifactsSummaryBuilder.primary(serviceOutcome.getArtifactsResult().getPrimary().getArtifactSummary());
    }

    if (EmptyPredicate.isNotEmpty(serviceOutcome.getArtifactsResult().getSidecars())) {
      artifactsSummaryBuilder.sidecars(serviceOutcome.getArtifactsResult()
                                           .getSidecars()
                                           .values()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .map(ArtifactOutcome::getArtifactSummary)
                                           .collect(Collectors.toList()));
    }

    return artifactsSummaryBuilder.build();
  }

  private Optional<ServiceOutcome> getServiceOutcome(NodeExecutionProto nodeExecutionProto) {
    return outcomeService
        .fetchOutcomes(nodeExecutionProto.getOutcomeRefsList()
                           .stream()
                           .map(ref -> ref.getInstanceId())
                           .collect(Collectors.toList()))
        .stream()
        .filter(outcome -> outcome instanceof ServiceOutcome)
        .map(outcome -> (ServiceOutcome) outcome)
        .findFirst();
  }

  private Optional<EnvironmentOutcome> getEnvironmentOutcome(NodeExecutionProto nodeExecutionProto) {
    return outcomeService
        .fetchOutcomes(nodeExecutionProto.getOutcomeRefsList()
                           .stream()
                           .map(ref -> ref.getInstanceId())
                           .collect(Collectors.toList()))
        .stream()
        .filter(outcome -> outcome instanceof EnvironmentOutcome)
        .map(outcome -> (EnvironmentOutcome) outcome)
        .findFirst();
  }

  private boolean isServiceNodeAndCompleted(PlanNodeProto node, Status status) {
    return Objects.equals(node.getStepType(), ServiceStep.STEP_TYPE) && status == Status.SUCCEEDED;
  }

  private boolean isInfrastructureNodeAndCompleted(PlanNodeProto node, Status status) {
    return Objects.equals(node.getStepType(), InfrastructureStep.STEP_TYPE) && status == Status.SUCCEEDED;
  }

  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(NodeExecutionProto nodeExecutionProto) {
    CDPipelineModuleInfoBuilder cdPipelineModuleInfoBuilder = CDPipelineModuleInfo.builder();
    if (isServiceNodeAndCompleted(nodeExecutionProto.getNode(), nodeExecutionProto.getStatus())) {
      Optional<ServiceOutcome> serviceOutcome = getServiceOutcome(nodeExecutionProto);
      serviceOutcome.ifPresent(outcome
          -> cdPipelineModuleInfoBuilder.serviceDefinitionType(outcome.getServiceDefinitionType())
                 .serviceIdentifier(outcome.getIdentifier()));
    }
    if (isInfrastructureNodeAndCompleted(nodeExecutionProto.getNode(), nodeExecutionProto.getStatus())) {
      List<Outcome> outcomes = outcomeService.fetchOutcomes(nodeExecutionProto.getOutcomeRefsList()
                                                                .stream()
                                                                .map(StepOutcomeRef::getInstanceId)
                                                                .collect(Collectors.toList()));
      for (Outcome outcome : outcomes) {
        if (outcome instanceof EnvironmentOutcome) {
          EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) outcome;
          cdPipelineModuleInfoBuilder.envIdentifier(environmentOutcome.getIdentifier())
              .environmentType(environmentOutcome.getEnvironmentType());
        }
        if (outcome instanceof InfrastructureOutcome) {
          InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcome;
          cdPipelineModuleInfoBuilder.infrastructureType(infrastructureOutcome.getKind());
        }
      }
    }
    return cdPipelineModuleInfoBuilder.build();
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(NodeExecutionProto nodeExecutionProto) {
    CDStageModuleInfoBuilder cdStageModuleInfoBuilder = CDStageModuleInfo.builder();
    if (isServiceNodeAndCompleted(nodeExecutionProto.getNode(), nodeExecutionProto.getStatus())) {
      Optional<ServiceOutcome> serviceOutcome = getServiceOutcome(nodeExecutionProto);
      serviceOutcome.ifPresent(outcome
          -> cdStageModuleInfoBuilder.serviceInfo(ServiceExecutionSummary.builder()
                                                      .identifier(outcome.getIdentifier())
                                                      .displayName(outcome.getName())
                                                      .deploymentType(outcome.getServiceDefinitionType())
                                                      .artifacts(mapArtifactsOutcomeToSummary(outcome))
                                                      .build()));
    }
    if (isInfrastructureNodeAndCompleted(nodeExecutionProto.getNode(), nodeExecutionProto.getStatus())) {
      Optional<EnvironmentOutcome> environmentOutcome = getEnvironmentOutcome(nodeExecutionProto);
      environmentOutcome.ifPresent(outcome
          -> cdStageModuleInfoBuilder.infraExecutionSummary(
              InfraExecutionSummary.builder().identifier(outcome.getIdentifier()).name(outcome.getName()).build()));
    }
    return cdStageModuleInfoBuilder.build();
  }
}
