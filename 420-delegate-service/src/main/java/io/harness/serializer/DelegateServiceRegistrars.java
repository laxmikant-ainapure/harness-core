package io.harness.serializer;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateAgentBeansKryoRegister;
import io.harness.serializer.kryo.DelegateServiceKryoRegister;
import io.harness.serializer.kryo.RbacCoreKryoRegistrar;
import io.harness.serializer.morphia.CommonEntitiesMorphiaRegister;
import io.harness.serializer.morphia.DelegateServiceMorphiaRegistrar;
import io.harness.serializer.morphia.FeatureFlagBeansMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(OrchestrationRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .add(CommonEntitiesKryoRegistrar.class)
          .add(DelegateAgentBeansKryoRegister.class)
          .add(DelegateServiceKryoRegister.class)
          .add(RbacCoreKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(DelegateServiceBeansRegistrars.morphiaRegistrars)
          .add(CommonEntitiesMorphiaRegister.class)
          .addAll(OrchestrationRegistrars.morphiaRegistrars)
          .add(DelegateServiceMorphiaRegistrar.class)
          .add(FeatureFlagBeansMorphiaRegistrar.class)
          .build();
}
