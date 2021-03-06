package io.harness.pms.sdk.core.plan.creation.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.data.structure.CollectionUtils;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.sdk.PmsSdkModuleUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class PlanNodeProtoMapper {
  @Inject @Named(PmsSdkModuleUtils.SDK_SERVICE_NAME) String serviceName;

  public PlanNodeProto toPlanNodeProtoWithDecoratedFields(PlanNode node) {
    PlanNodeProto.Builder builder =
        PlanNodeProto.newBuilder()
            .setUuid(node.getUuid())
            .setName(isEmpty(node.getName()) ? "" : node.getName())
            .setStepType(node.getStepType())
            .setIdentifier(isEmpty(node.getIdentifier()) ? "" : node.getIdentifier())
            .setStepParameters(node.getStepParameters() == null
                    ? ""
                    : RecastOrchestrationUtils.toDocumentJson(node.getStepParameters()))
            .addAllRebObjects(CollectionUtils.emptyIfNull(node.getRefObjects()))
            .addAllAdviserObtainments(CollectionUtils.emptyIfNull(node.getAdviserObtainments()))
            .addAllFacilitatorObtainments(CollectionUtils.emptyIfNull(node.getFacilitatorObtainments()))
            .setSkipExpressionChain(node.isSkipExpressionChain())
            .setSkipType(node.getSkipGraphType())
            .setServiceName(serviceName)
            .addAllTimeoutObtainments(CollectionUtils.emptyIfNull(node.getTimeoutObtainments()));
    if (node.getSkipCondition() != null) {
      builder.setSkipCondition(node.getSkipCondition());
    }
    if (node.getGroup() != null) {
      builder.setGroup(node.getGroup());
    }
    return builder.build();
  }

  // NOTE: Only there to support current gen. Use toPlanNodeProtoWithServiceName instead.
  public static PlanNodeProto toPlanNodeProto(PlanNode node) {
    PlanNodeProto.Builder builder =
        PlanNodeProto.newBuilder()
            .setUuid(node.getUuid())
            .setName(isEmpty(node.getName()) ? "" : node.getName())
            .setStepType(node.getStepType())
            .setIdentifier(isEmpty(node.getIdentifier()) ? "" : node.getIdentifier())
            .setStepParameters(node.getStepParameters() == null
                    ? ""
                    : RecastOrchestrationUtils.toDocumentJson(node.getStepParameters()))
            .addAllRebObjects(CollectionUtils.emptyIfNull(node.getRefObjects()))
            .addAllAdviserObtainments(CollectionUtils.emptyIfNull(node.getAdviserObtainments()))
            .addAllFacilitatorObtainments(CollectionUtils.emptyIfNull(node.getFacilitatorObtainments()))
            .addAllTimeoutObtainments(CollectionUtils.emptyIfNull(node.getTimeoutObtainments()))
            .setSkipExpressionChain(node.isSkipExpressionChain())
            .setSkipType(node.getSkipGraphType())
            .addAllTimeoutObtainments(CollectionUtils.emptyIfNull(node.getTimeoutObtainments()));
    if (node.getSkipCondition() != null) {
      builder.setSkipCondition(node.getSkipCondition());
    }
    if (node.getGroup() != null) {
      builder.setGroup(node.getGroup());
    }
    return builder.build();
  }
}
