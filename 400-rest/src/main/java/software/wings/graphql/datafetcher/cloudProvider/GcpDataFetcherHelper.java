package software.wings.graphql.datafetcher.cloudProvider;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.GcpConfig;
import software.wings.beans.GcpConfig.GcpConfigBuilder;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.cloudProvider.QLGcpCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateGcpCloudProviderInput;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class GcpDataFetcherHelper {
  @Inject private UsageScopeController usageScopeController;

  public SettingAttribute toSettingAttribute(QLGcpCloudProviderInput input, String accountId) {
    GcpConfigBuilder configBuilder = GcpConfig.builder().accountId(accountId);

    if (input.getSkipValidation().isPresent()) {
      input.getSkipValidation().getValue().ifPresent(configBuilder::skipValidation);
    }

    if (input.getUseDelegate().isPresent()) {
      input.getUseDelegate().getValue().ifPresent(configBuilder::useDelegate);
    }

    if (input.getDelegateSelector().isPresent()) {
      input.getDelegateSelector().getValue().ifPresent(configBuilder::delegateSelector);
    }

    if (input.getServiceAccountKeySecretId().isPresent()) {
      input.getServiceAccountKeySecretId().getValue().ifPresent(configBuilder::encryptedServiceAccountKeyFileContent);
    }

    SettingAttribute.Builder settingAttributeBuilder = SettingAttribute.Builder.aSettingAttribute()
                                                           .withValue(configBuilder.build())
                                                           .withAccountId(accountId)
                                                           .withCategory(SettingAttribute.SettingCategory.SETTING);

    if (input.getName().isPresent()) {
      input.getName().getValue().ifPresent(settingAttributeBuilder::withName);
    }

    return settingAttributeBuilder.build();
  }

  public void updateSettingAttribute(
      SettingAttribute settingAttribute, QLUpdateGcpCloudProviderInput input, String accountId) {
    GcpConfig config = (GcpConfig) settingAttribute.getValue();

    if (input.getSkipValidation().isPresent()) {
      input.getSkipValidation().getValue().ifPresent(config::setSkipValidation);
    }

    if (input.getUseDelegate().isPresent()) {
      input.getUseDelegate().getValue().ifPresent(config::setUseDelegate);
    }

    if (input.getDelegateSelector().isPresent()) {
      input.getDelegateSelector().getValue().ifPresent(config::setDelegateSelector);
    }

    if (input.getServiceAccountKeySecretId().isPresent()) {
      input.getServiceAccountKeySecretId().getValue().ifPresent(config::setEncryptedServiceAccountKeyFileContent);
    }

    settingAttribute.setValue(config);

    if (input.getName().isPresent()) {
      input.getName().getValue().ifPresent(settingAttribute::setName);
    }
  }
}
