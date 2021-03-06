package software.wings.graphql.schema.type.cloudProvider.aws;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLEnum;

@TargetModule(Module._380_CG_GRAPHQL)
public enum QLAwsCredentialsType implements QLEnum {
  EC2_IAM,
  MANUAL;

  @Override
  public String getStringValue() {
    return this.name();
  }
}
