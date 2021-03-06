package io.harness.beans.yaml.extended;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("Text")
@TypeAlias("customTextVariable")
public class CustomTextVariable implements CustomVariable {
  @Builder.Default @NotNull Type type = Type.TEXT;
  @NotNull String name;
  @NotNull String value;
}
