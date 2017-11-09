package software.wings.beans.trigger;

import static software.wings.beans.trigger.TriggerConditionType.NEW_ARTIFACT;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by sgurubelli on 10/25/17.
 */
@JsonTypeName("NEW_ARTIFACT")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class ArtifactTriggerCondition extends TriggerCondition {
  @NotEmpty private String artifactStreamId;
  private String artifactSourceName;
  private String artifactFilter;

  public ArtifactTriggerCondition() {
    super(NEW_ARTIFACT);
  }

  public ArtifactTriggerCondition(String artifactStreamId, String artifactFilter, String artifactSourceName) {
    this();
    this.artifactStreamId = artifactStreamId;
    this.artifactFilter = artifactFilter;
    this.artifactSourceName = artifactSourceName;
  }
}
