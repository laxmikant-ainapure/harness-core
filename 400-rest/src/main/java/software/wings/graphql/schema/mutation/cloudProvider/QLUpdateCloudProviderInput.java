package software.wings.graphql.schema.mutation.cloudProvider;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.mutation.QLMutationInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLUpdateAwsCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLUpdateK8sCloudProviderInput;
import software.wings.graphql.schema.type.QLCloudProviderType;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Scope(PermissionAttribute.ResourceType.SETTING)
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(Module._380_CG_GRAPHQL)
public class QLUpdateCloudProviderInput implements QLMutationInput {
  private String clientMutationId;

  private String cloudProviderId;
  private QLCloudProviderType cloudProviderType;
  private QLUpdatePcfCloudProviderInput pcfCloudProvider;
  private QLUpdateSpotInstCloudProviderInput spotInstCloudProvider;
  private QLUpdateGcpCloudProviderInput gcpCloudProvider;
  private QLUpdateK8sCloudProviderInput k8sCloudProvider;
  private QLUpdatePhysicalDataCenterCloudProviderInput physicalDataCenterCloudProvider;
  private QLUpdateAzureCloudProviderInput azureCloudProvider;
  private QLUpdateAwsCloudProviderInput awsCloudProvider;
}
