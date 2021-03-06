package software.wings.beans.yaml;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.persistence.PersistentEntity;
import io.harness.yaml.BaseYaml;

import software.wings.service.impl.yaml.handler.BaseYamlHandler;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * @author rktummala on 10/17/17
 */
@TargetModule(Module._870_YAML_BEANS)
@Data
public class ChangeContext<Y extends BaseYaml> {
  private Change change;
  private YamlType yamlType;
  private Y yaml;
  private BaseYamlHandler yamlSyncHandler;
  private Map<String, String> entityIdMap = new HashMap<>();
  private Map<String, Object> properties = new HashMap<>();
  private PersistentEntity entity;

  public Builder toBuilder() {
    return ChangeContext.Builder.aChangeContext()
        .withChange(getChange())
        .withYamlType(getYamlType())
        .withYaml(getYaml())
        .withYamlSyncHandler(getYamlSyncHandler())
        .withEntityIdMap(entityIdMap)
        .withProperties(properties);
  }

  public static final class Builder<Y extends BaseYaml> {
    private Change change;
    private YamlType yamlType;
    private Y yaml;
    private BaseYamlHandler yamlSyncHandler;
    private Map<String, String> entityIdMap = new HashMap<>();
    private Map<String, Object> properties = new HashMap<>();

    private Builder() {}

    public static Builder aChangeContext() {
      return new Builder();
    }

    public Builder withChange(Change change) {
      this.change = change;
      return this;
    }

    public Builder withYamlType(YamlType yamlType) {
      this.yamlType = yamlType;
      return this;
    }

    public Builder withYaml(Y yaml) {
      this.yaml = yaml;
      return this;
    }

    public Builder withYamlSyncHandler(BaseYamlHandler yamlSyncHandler) {
      this.yamlSyncHandler = yamlSyncHandler;
      return this;
    }

    public Builder withEntityIdMap(Map<String, String> entityIdMap) {
      this.entityIdMap = entityIdMap;
      return this;
    }

    public Builder withProperties(Map<String, Object> properties) {
      this.properties = properties;
      return this;
    }
    public ChangeContext build() {
      ChangeContext changeContext = new ChangeContext();
      changeContext.setChange(change);
      changeContext.setYamlType(yamlType);
      changeContext.setYaml(yaml);
      changeContext.setYamlSyncHandler(yamlSyncHandler);
      changeContext.setEntityIdMap(entityIdMap);
      changeContext.setProperties(properties);
      return changeContext;
    }
  }
}
