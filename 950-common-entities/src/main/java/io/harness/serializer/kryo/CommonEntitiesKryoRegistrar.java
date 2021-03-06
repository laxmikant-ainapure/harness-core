package io.harness.serializer.kryo;

import io.harness.beans.EnvironmentType;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class CommonEntitiesKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(EnvironmentType.class, 7398);
  }
}