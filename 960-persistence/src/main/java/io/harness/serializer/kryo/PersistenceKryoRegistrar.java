package io.harness.serializer.kryo;

import io.harness.beans.EmbeddedUser;
import io.harness.cache.VersionedKey;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class PersistenceKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(EmbeddedUser.class, 5021);
    kryo.register(VersionedKey.class, 5015);
  }
}
