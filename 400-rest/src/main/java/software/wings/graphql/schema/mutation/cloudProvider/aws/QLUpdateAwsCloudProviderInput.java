package software.wings.graphql.schema.mutation.cloudProvider.aws;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.utils.RequestField;

import software.wings.graphql.schema.type.cloudProvider.aws.QLAwsCredentialsType;
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
public class QLUpdateAwsCloudProviderInput {
  private RequestField<String> name;

  private RequestField<QLAwsCredentialsType> credentialsType;
  private RequestField<QLUpdateEc2IamCredentials> ec2IamCredentials;
  private RequestField<QLUpdateAwsManualCredentials> manualCredentials;

  private RequestField<QLUpdateAwsCrossAccountAttributes> crossAccountAttributes;
  private RequestField<String> defaultRegion;
}
