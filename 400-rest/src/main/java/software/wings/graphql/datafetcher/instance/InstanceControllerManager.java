package software.wings.graphql.datafetcher.instance;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.graphql.schema.type.instance.QLInstance;
import software.wings.graphql.schema.type.instance.QLInstanceType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jsonwebtoken.lang.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class InstanceControllerManager {
  private static Map<Class<? extends InstanceInfo>, QLInstanceType> instanceTypeMap = new HashMap<>();
  static {
    Collections.arrayToList(QLInstanceType.values())
        .forEach(type
            -> ((QLInstanceType) type)
                   .getInstanceInfos()
                   .forEach(clazz -> instanceTypeMap.put(clazz, (QLInstanceType) type)));
  }

  @Inject
  private Map<Class, software.wings.graphql.datafetcher.instance.instanceInfo.InstanceController>
      instanceInfoControllerMap;

  public QLInstance getQLInstance(@NotNull Instance instance) {
    final software.wings.graphql.datafetcher.instance.instanceInfo.InstanceController instanceController =
        instanceInfoControllerMap.get(instance.getInstanceInfo().getClass());
    if (instanceController != null) {
      return instanceController.populateInstance(instance);
    } else {
      log.error("No InstanceInfoMapping found for InstanceInfo : " + instance.getInstanceInfo().getClass());
      return null;
    }
  }
}
