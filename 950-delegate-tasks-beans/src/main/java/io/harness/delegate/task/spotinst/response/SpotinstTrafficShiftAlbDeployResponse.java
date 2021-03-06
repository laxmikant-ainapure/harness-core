package io.harness.delegate.task.spotinst.response;

import com.amazonaws.services.ec2.model.Instance;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpotinstTrafficShiftAlbDeployResponse implements SpotInstTaskResponse {
  private List<Instance> ec2InstancesAdded;
  private List<Instance> ec2InstancesExisting;
}
