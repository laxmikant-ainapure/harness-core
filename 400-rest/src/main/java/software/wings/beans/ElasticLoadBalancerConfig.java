package software.wings.beans;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.util.Collections.emptyList;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.LoadBalancerConfig;
import software.wings.beans.AwsInfrastructureMapping.AwsRegionDataProvider;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingVariableTypes;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.yaml.setting.LoadBalancerProviderYaml;

import com.amazonaws.regions.Regions;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by peeyushaggarwal on 9/14/16.
 */
@JsonTypeName("ELB")
@Data
@Builder
@ToString(exclude = "secretKey")
@EqualsAndHashCode(callSuper = false)
public class ElasticLoadBalancerConfig extends LoadBalancerConfig implements EncryptableSetting {
  @Attributes(title = "Region", required = true)
  @DefaultValue(AWS_DEFAULT_REGION)
  @EnumData(enumDataProvider = AwsRegionDataProvider.class)
  private Regions region;

  @NotEmpty @Attributes(title = "Elastic Load Balancer Name", required = true) private String loadBalancerName;

  @Attributes(title = "AWS account access key", required = true) @NotEmpty private String accessKey;

  @Attributes(title = "AWS account secret key", required = true)
  @Encrypted(fieldName = "aws_account_secret_key")
  private char[] secretKey;

  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedSecretKey;

  @Attributes(title = "Use Ec2 Iam role") private boolean useEc2IamCredentials;

  /**
   * Instantiates a new Elastic load balancer config.
   */
  public ElasticLoadBalancerConfig() {
    super(SettingVariableTypes.ELB.name());
  }

  public ElasticLoadBalancerConfig(Regions region, String loadBalancerName, String accessKey, char[] secretKey,
      String accountId, String encryptedSecretKey, boolean useEc2IamCredentials) {
    this();
    this.region = region;
    this.loadBalancerName = loadBalancerName;
    this.accessKey = accessKey;
    this.secretKey = secretKey == null ? null : secretKey.clone();
    this.accountId = accountId;
    this.encryptedSecretKey = encryptedSecretKey;
    this.useEc2IamCredentials = useEc2IamCredentials;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return emptyList();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends LoadBalancerProviderYaml {
    private String region;
    private String loadBalancerName;
    private String accessKey;
    private String secretKey;
    private boolean useEc2IamCredentials;

    @Builder
    public Yaml(String type, String harnessApiVersion, String region, String loadBalancerName, String accessKey,
        String secretKey, UsageRestrictions.Yaml usageRestrictions, boolean useEc2IamCredentials) {
      super(type, harnessApiVersion, usageRestrictions);
      this.region = region;
      this.loadBalancerName = loadBalancerName;
      this.accessKey = accessKey;
      this.secretKey = secretKey;
      this.useEc2IamCredentials = useEc2IamCredentials;
    }
  }
}
