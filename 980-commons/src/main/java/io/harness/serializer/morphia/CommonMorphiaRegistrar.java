package io.harness.serializer.morphia;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.Encryptable;
import io.harness.context.MdcGlobalContextData;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.security.SimpleEncryption;
import io.harness.tasks.Task;

import java.io.Serializable;
import java.util.Set;

public class CommonMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(Task.class);
    set.add(Serializable.class);
    set.add(Encryptable.class);
    set.add(DecryptableEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("context.MdcGlobalContextData", MdcGlobalContextData.class);
    h.put("limits.impl.model.RateLimit", RateLimit.class);
    h.put("limits.impl.model.StaticLimit", StaticLimit.class);
    h.put("security.SimpleEncryption", SimpleEncryption.class);

    w.put("security.encryption.SimpleEncryption", SimpleEncryption.class);
  }
}
