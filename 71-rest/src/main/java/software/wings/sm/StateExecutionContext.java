package software.wings.sm;

import io.harness.delegate.task.shell.ScriptType;
import lombok.Builder;
import lombok.Value;
import software.wings.beans.artifact.Artifact;

import java.util.List;
import java.util.Map;

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
  private Map<String, Artifact> multiArtifacts;
  private String artifactFileName;
}
