package software.wings.yaml.handler.connectors.configyamlhandlers;

import software.wings.beans.SettingAttribute;
import software.wings.service.impl.yaml.handler.setting.SettingValueYamlHandler;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SettingValueYamlConfig {
  private String name;
  private String yamlDirPath;
  private String invalidYamlContent;
  private SettingValueYamlHandler yamlHandler;
  private SettingAttribute settingAttributeSaved;
  private Class configclazz;
  private String updateMethodName;
  private String currentFieldValue;
  private Class yamlClass;
}
