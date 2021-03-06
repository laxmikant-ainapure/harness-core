package io.harness.yaml;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.list;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.map;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.number;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

public class TestClassWithManyFields {
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PUBLIC)
  public static class ClassWithoutApiModelOverride1 extends TestAbstractClass {
    String testString;
    @YamlSchemaTypes(value = {list, map}, defaultType = list) TestRandomClass1 testRandomClass1;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PUBLIC)
  public static class ClassWhichContainsInterface1 {
    @NotNull Types1 type;
    @JsonProperty("spec")
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
    TestAbstractClass abstractClass;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PUBLIC)
  @JsonSubTypes(
      { @JsonSubTypes.Type(value = ClassWithoutApiModelOverride1.class, name = "ClassWithoutApiModelOverride1") })
  public static class TestAbstractClass {
    @YamlSchemaTypes({string, number}) TestRandomClass2 abstractClass1;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PUBLIC)
  public static class TestRandomClass1 {
    int testR11;
    int testR12;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PUBLIC)
  public static class TestRandomClass2 {
    int testR21;
    int testR22;
    TestRandomClass1 testRandomClass1;
  }

  public enum Types1 { ClassWithoutApiModelOverride1 }
}
