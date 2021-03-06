package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

import software.wings.beans.artifact.Artifact;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class StateExecutionContext {
  private StateExecutionData stateExecutionData;
  private Artifact artifact;
  private boolean adoptDelegateDecryption;
  private int expressionFunctorToken;
  List<ContextElement> contextElements;
  private ScriptType scriptType;

  // needed for multi artifact support
  private String artifactFileName;
}
