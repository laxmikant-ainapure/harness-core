package software.wings.beans;

import io.harness.yaml.BaseYaml;

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by sgurubelli on 8/11/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateExpression {
  private String fieldName;
  private String expression;
  @Default private boolean expressionAllowed = true; // Can this template expression can contain other expression
  private String description;
  private boolean mandatory;
  @Default private Map<String, Object> metadata = new HashMap<>();

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYaml {
    private String fieldName;
    private String expression;
    private List<NameValuePair.Yaml> metadata = Lists.newArrayList();

    public static final class Builder {
      private String fieldName;
      private String expression;
      private List<NameValuePair.Yaml> metadata = Lists.newArrayList();

      private Builder() {}

      public static Builder aYaml() {
        return new Builder();
      }

      public Builder withFieldName(String fieldName) {
        this.fieldName = fieldName;
        return this;
      }

      public Builder withExpression(String expression) {
        this.expression = expression;
        return this;
      }

      public Builder withMetadata(List<NameValuePair.Yaml> metadata) {
        this.metadata = metadata;
        return this;
      }

      public Builder but() {
        return aYaml().withFieldName(fieldName).withExpression(expression).withMetadata(metadata);
      }

      public Yaml build() {
        Yaml yaml = new Yaml();
        yaml.setFieldName(fieldName);
        yaml.setExpression(expression);
        yaml.setMetadata(metadata);
        return yaml;
      }
    }
  }
}
