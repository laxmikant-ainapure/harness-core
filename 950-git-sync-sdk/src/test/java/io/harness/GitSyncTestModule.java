package io.harness;

import io.harness.gitsync.persistance.SpringPersistenceModule;
import io.harness.govern.ProviderModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoRegistrar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

public class GitSyncTestModule extends AbstractModule {
  private final GitSyncTestConfiguration config;

  public GitSyncTestModule(GitSyncTestConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(MongoModule.getInstance());
    bind(HPersistence.class).to(MongoPersistence.class);
    install(new SpringPersistenceModule());

    install(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return config.getMongoConfig();
      }
      @Provides
      @Singleton
      public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }
      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return Collections.emptyMap();
      }
    });
  }
}