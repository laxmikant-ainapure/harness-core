package io.harness.notification.module;

import io.harness.govern.ProviderModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.notification.NotificationClientApplicationConfiguration;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.morphia.NotificationClientMorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.mongodb.morphia.converters.TypeConverter;

public class NotificationClientApplicationModule extends AbstractModule {
  private final NotificationClientApplicationConfiguration appConfig;

  public NotificationClientApplicationModule(NotificationClientApplicationConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NotificationClientApplicationConfiguration.class).toInstance(appConfig);
    install(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return appConfig.getMongoConfig();
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .add(NotificationClientMorphiaRegistrar.class)
            .build();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return Collections.emptyMap();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }
    });

    install(MongoModule.getInstance());
    bind(HPersistence.class).to(MongoPersistence.class);
    install(new NotificationClientModule(this.appConfig.getNotificationClientConfiguration()));
  }
}
