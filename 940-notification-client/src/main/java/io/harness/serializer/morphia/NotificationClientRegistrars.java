package io.harness.serializer.morphia;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;

import com.google.common.collect.ImmutableSet;

public class NotificationClientRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder().add(NotificationBeansKryoRegistrar.class).build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(NotificationClientMorphiaRegistrar.class).build();
}
