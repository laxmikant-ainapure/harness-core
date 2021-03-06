package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.trigger.WebhookSource.GitLabEventType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@JsonTypeName("GITLAB")
@Value
@Builder
public class GitLabsPayloadSource implements PayloadSource {
  @NotNull private Type type = Type.GITLAB;
  private List<GitLabEventType> gitLabEventTypes;
  private List<CustomPayloadExpression> customPayloadExpressions;
}
