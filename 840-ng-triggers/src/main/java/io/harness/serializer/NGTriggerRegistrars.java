package io.harness.serializer;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.NGTriggerKryoRegistrar;
import io.harness.serializer.morphia.NGTriggerMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NGTriggerRegistrars {
  public final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(NGTriggerKryoRegistrar.class)
          .build();

  public final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .add(NGTriggerMorphiaRegistrar.class)
          .build();
}
