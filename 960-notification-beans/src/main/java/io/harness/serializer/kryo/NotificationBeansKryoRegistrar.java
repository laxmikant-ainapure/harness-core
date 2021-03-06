package io.harness.serializer.kryo;

import io.harness.notification.SmtpConfig;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class NotificationBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(SmtpConfig.class, 55299);
  }
}
