package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class PipelineStageGroupedInfo {
  String name;
  @Singular List<String> pipelineStageElementNames;
  int parallelIndex;
}
