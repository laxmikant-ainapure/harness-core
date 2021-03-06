package io.harness.stream;

import static io.harness.stream.AtmosphereBroadcaster.HAZELCAST;
import static io.harness.stream.AtmosphereBroadcaster.REDIS;

import io.harness.hazelcast.HazelcastModule;
import io.harness.redis.RedisConfig;
import io.harness.stream.hazelcast.HazelcastBroadcaster;
import io.harness.stream.redisson.RedissonBroadcaster;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hazelcast.core.HazelcastInstance;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.DefaultBroadcaster;
import org.atmosphere.cpr.DefaultMetaBroadcaster;
import org.atmosphere.cpr.MetaBroadcaster;

public class StreamModule extends AbstractModule {
  private static volatile StreamModule instance;

  public static StreamModule getInstance() {
    if (instance == null) {
      instance = new StreamModule();
    }
    return instance;
  }

  private StreamModule() {}

  @Override
  protected void configure() {
    install(HazelcastModule.getInstance());
  }

  @Provides
  @Singleton
  AtmosphereServlet getAtmosphereServelet(AtmosphereBroadcaster atmosphereBroadcaster,
      Provider<HazelcastInstance> hazelcastInstanceProvider,
      @Named("atmosphere") Provider<RedisConfig> redisConfigProvider) {
    AtmosphereServlet atmosphereServlet = new AtmosphereServlet();
    atmosphereServlet.framework()
        .addInitParameter(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
        .addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true")
        .addInitParameter(ApplicationConfig.ANNOTATION_PACKAGE, getClass().getPackage().getName());

    String broadcasterName;
    if (atmosphereBroadcaster == HAZELCAST) {
      broadcasterName = HazelcastBroadcaster.class.getName();
      HazelcastBroadcaster.HAZELCAST_INSTANCE.set(hazelcastInstanceProvider.get());
    } else if (atmosphereBroadcaster == REDIS) {
      broadcasterName = RedissonBroadcaster.class.getName();
    } else {
      broadcasterName = DefaultBroadcaster.class.getName();
    }
    atmosphereServlet.framework().setDefaultBroadcasterClassName(broadcasterName);
    return atmosphereServlet;
  }

  @Provides
  @Singleton
  BroadcasterFactory getBroadcasterFactory(AtmosphereServlet atmosphereServlet) {
    return atmosphereServlet.framework().getBroadcasterFactory();
  }

  @Provides
  @Singleton
  MetaBroadcaster metaBroadcaster(AtmosphereServlet atmosphereServlet) {
    MetaBroadcaster metaBroadcaster = new DefaultMetaBroadcaster();
    metaBroadcaster.configure(atmosphereServlet.framework().getAtmosphereConfig());
    return metaBroadcaster;
  }
}
