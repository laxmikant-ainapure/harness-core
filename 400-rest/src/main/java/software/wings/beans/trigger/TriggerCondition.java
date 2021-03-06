package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * Created by sgurubelli on 10/25/17.
 */
@OwnedBy(CDC)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "conditionType", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ArtifactTriggerCondition.class, name = "NEW_ARTIFACT")
  , @JsonSubTypes.Type(value = PipelineTriggerCondition.class, name = "PIPELINE_COMPLETION"),
      @JsonSubTypes.Type(value = ScheduledTriggerCondition.class, name = "SCHEDULED"),
      @JsonSubTypes.Type(value = WebHookTriggerCondition.class, name = "WEBHOOK"),
      @JsonSubTypes.Type(value = NewInstanceTriggerCondition.class, name = "NEW_INSTANCE"),
      @JsonSubTypes.Type(value = ManifestTriggerCondition.class, name = "NEW_MANIFEST")
})
@Data
@FieldNameConstants(innerTypeName = "TriggerConditionKeys")
public abstract class TriggerCondition {
  @NotNull private TriggerConditionType conditionType;
  private String conditionDisplayName;

  public TriggerCondition(TriggerConditionType conditionType) {
    this.conditionType = conditionType;
  }

  public TriggerCondition(TriggerConditionType conditionType, String conditionDisplayName) {
    this.conditionType = conditionType;
    this.conditionDisplayName = conditionDisplayName;
  }
}
