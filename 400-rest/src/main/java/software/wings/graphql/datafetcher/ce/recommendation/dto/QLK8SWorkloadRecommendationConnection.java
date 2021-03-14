package software.wings.graphql.datafetcher.ce.recommendation.dto;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLObject;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@Scope(PermissionAttribute.ResourceType.K8S_RECOMMENDATION)
@TargetModule(Module._375_CE_GRAPHQL)
public class QLK8SWorkloadRecommendationConnection implements QLObject {
  QLPageInfo pageInfo;
  @Singular List<QLK8sWorkloadRecommendation> nodes;
}
