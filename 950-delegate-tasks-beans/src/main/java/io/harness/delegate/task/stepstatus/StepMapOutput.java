package io.harness.delegate.task.stepstatus;

import static io.harness.delegate.task.stepstatus.StepOutput.Type.MAP;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@JsonTypeName("MAP")
public class StepMapOutput implements StepOutput {
  @Singular("output") Map<String, String> map;
  @Override
  public StepOutput.Type getType() {
    return MAP;
  }
}
