package software.wings.beans.alert;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@Builder
@TargetModule(Module._970_API_SERVICES_BEANS)
public class DeploymentFreezeEventAlert implements AlertData {
  String deploymentFreezeName;
  String deploymentFreezeId;
  EventType freezeEventType;
  public enum EventType {
    ACTIVATION("activated"),
    DEACTIVATION("de-activated");
    private final String verb;
    EventType(String verb) {
      this.verb = verb;
    }
  }

  @Override
  public boolean matches(AlertData alertData) {
    if (!(alertData instanceof DeploymentFreezeEventAlert)) {
      return false;
    }
    DeploymentFreezeEventAlert otherAlert = (DeploymentFreezeEventAlert) alertData;
    return StringUtils.equals(otherAlert.getDeploymentFreezeId(), deploymentFreezeId);
  }

  @Override
  public String buildTitle() {
    return String.format("Deployment Freeze Window %s has been %s.", deploymentFreezeName, freezeEventType.verb);
  }
}
