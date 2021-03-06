package software.wings.sm.states.spotinst;

import io.harness.context.ContextElementType;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.pms.sdk.core.data.SweepingOutput;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("spotinstTrafficShiftAlbSetupElement")
public class SpotinstTrafficShiftAlbSetupElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private String appId;
  private String envId;
  private String serviceId;
  private String infraMappingId;
  private ElastiGroup newElastiGroupOriginalConfig;
  private ElastiGroup oldElastiGroupOriginalConfig;
  private List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups;
  private String elastigroupNamePrefix;
  private int timeoutIntervalInMin;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.SPOTINST_SERVICE_SETUP;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    return null;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "spotinstTrafficShiftAlbSetupElement";
  }
}
