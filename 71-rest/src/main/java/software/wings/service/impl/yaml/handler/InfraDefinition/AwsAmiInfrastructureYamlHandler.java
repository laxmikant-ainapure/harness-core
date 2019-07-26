package software.wings.service.impl.yaml.handler.InfraDefinition;

import static java.lang.String.format;
import static software.wings.utils.Validator.notNullCheck;

import com.google.inject.Inject;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsAmiInfrastructure.Yaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.intfc.SettingsService;

import java.util.List;

public class AwsAmiInfrastructureYamlHandler
    extends CloudProviderInfrastructureYamlHandler<Yaml, AwsAmiInfrastructure> {
  @Inject private SettingsService settingsService;
  @Override
  public Yaml toYaml(AwsAmiInfrastructure bean, String appId) {
    SettingAttribute cloudProvider = settingsService.get(bean.getCloudProviderId());
    return Yaml.builder()
        .autoScalingGroupName(bean.getAutoScalingGroupName())
        .classicLoadBalancers(bean.getClassicLoadBalancers())
        .hostNameConvention(bean.getHostNameConvention())
        .region(bean.getRegion())
        .stageClassicLoadBalancers(bean.getStageClassicLoadBalancers())
        .stageTargetGroupArns(bean.getStageTargetGroupArns())
        .targetGroupArns(bean.getTargetGroupArns())
        .cloudProviderName(cloudProvider.getName())
        .type(InfrastructureType.AWS_AMI)
        .build();
  }

  @Override
  public AwsAmiInfrastructure upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    AwsAmiInfrastructure bean = AwsAmiInfrastructure.builder().build();
    toBean(bean, changeContext);
    return bean;
  }

  private void toBean(AwsAmiInfrastructure bean, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(format("Cloud Provider with name %s does not exist", yaml.getCloudProviderName()), cloudProvider);
    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setAutoScalingGroupName(yaml.getAutoScalingGroupName());
    bean.setClassicLoadBalancers(yaml.getClassicLoadBalancers());
    bean.setHostNameConvention(yaml.getHostNameConvention());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
