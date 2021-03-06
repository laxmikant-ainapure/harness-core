package io.harness.delegate.beans.ci;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIK8CleanupTaskParams implements CICleanupTaskParams {
  @NotNull private ConnectorDetails k8sConnector;
  @NotNull private List<String> podNameList;
  @NotNull private List<String> serviceNameList;
  @NotNull private String namespace;
  @Builder.Default private static final CICleanupTaskParams.Type type = Type.GCP_K8;

  @Override
  public Type getType() {
    return type;
  }
}
