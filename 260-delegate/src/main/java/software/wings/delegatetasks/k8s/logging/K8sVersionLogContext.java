package software.wings.delegatetasks.k8s.logging;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@TargetModule(Module._930_DELEGATE_TASKS)
public class K8sVersionLogContext extends AutoLogContext {
  public static final String CLOUD_PROVIDER = "cloudProvider";
  public static final String VERSION = "version";
  public static final String CC_ENABLED = "ccEnabled";

  public K8sVersionLogContext(String cloudProvider, String version, boolean ccEnabled, OverrideBehavior behavior) {
    super(ImmutableMap.of(CLOUD_PROVIDER, cloudProvider, VERSION, version, CC_ENABLED, String.valueOf(ccEnabled)),
        behavior);
  }
}
