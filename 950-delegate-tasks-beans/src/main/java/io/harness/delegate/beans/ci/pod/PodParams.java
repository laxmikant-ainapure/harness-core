package io.harness.delegate.beans.ci.pod;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public abstract class PodParams<T extends ContainerParams> {
  @NonNull private String name;
  @NonNull private String namespace;
  private Map<String, String> labels;
  private List<T> containerParamsList;
  private List<T> initContainerParamsList;
  private List<PVCParams> pvcParamList;
  private List<HostAliasParams> hostAliasParamsList;

  public abstract PodParams.Type getType();

  public enum Type { K8 }
}
