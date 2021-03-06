package io.harness.batch.processing.config;

import io.harness.govern.ProviderModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Set;

public class BatchProcessingRegistrarsModule extends ProviderModule {
  @Provides
  @Singleton
  Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return Collections.emptySet();
  }

  @Provides
  @Singleton
  Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ManagerRegistrars.morphiaRegistrars;
  }
}
