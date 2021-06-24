package io.harness.ng;

import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.audit.ResourceTypeConstants.API_KEY;
import static io.harness.audit.ResourceTypeConstants.CONNECTOR;
import static io.harness.audit.ResourceTypeConstants.DELEGATE_CONFIGURATION;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT;
import static io.harness.audit.ResourceTypeConstants.ORGANIZATION;
import static io.harness.audit.ResourceTypeConstants.PROJECT;
import static io.harness.audit.ResourceTypeConstants.SECRET;
import static io.harness.audit.ResourceTypeConstants.SERVICE;
import static io.harness.audit.ResourceTypeConstants.SERVICE_ACCOUNT;
import static io.harness.audit.ResourceTypeConstants.TOKEN;
import static io.harness.audit.ResourceTypeConstants.USER;
import static io.harness.audit.ResourceTypeConstants.USER_GROUP;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.FEATURE_FLAG_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import static java.lang.Boolean.TRUE;

import io.harness.AccessControlClientModule;
import io.harness.Microservice;
import io.harness.OrchestrationModule;
import io.harness.OrchestrationModuleConfig;
import io.harness.OrchestrationStepsModule;
import io.harness.YamlBaseUrlServiceImpl;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.account.AbstractAccountModule;
import io.harness.account.AccountClientModule;
import io.harness.account.AccountConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.cdng.NGModule;
import io.harness.cdng.expressions.CDExpressionEvaluatorProvider;
import io.harness.cdng.fileservice.FileServiceClient;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.connector.ConnectorModule;
import io.harness.connector.events.ConnectorEventHandler;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.encryptors.Encryptors;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.encryptors.clients.GcpKmsEncryptor;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.file.NGFileServiceModule;
import io.harness.gitsync.GitSyncModule;
import io.harness.gitsync.core.runnable.HarnessToGitPushMessageListener;
import io.harness.gitsync.core.webhook.createbranchevent.GitBranchHookEventStreamListener;
import io.harness.gitsync.core.webhook.pushevent.GitPushEventStreamListener;
import io.harness.govern.ProviderModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.licensing.LicenseModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.modules.ModulesClientModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.accesscontrol.migrations.AccessControlMigrationModule;
import io.harness.ng.accesscontrol.mockserver.MockRoleAssignmentModule;
import io.harness.ng.accesscontrol.user.AggregateUserService;
import io.harness.ng.accesscontrol.user.AggregateUserServiceImpl;
import io.harness.ng.authenticationsettings.AuthenticationSettingsModule;
import io.harness.ng.cdOverview.service.CDOverviewDashboardService;
import io.harness.ng.cdOverview.service.CDOverviewDashboardServiceImpl;
import io.harness.ng.core.CoreModule;
import io.harness.ng.core.DefaultOrganizationModule;
import io.harness.ng.core.InviteModule;
import io.harness.ng.core.NGAggregateModule;
import io.harness.ng.core.SecretManagementModule;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.DelegateProfileManagerNgService;
import io.harness.ng.core.api.NGModulesService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.api.impl.ApiKeyServiceImpl;
import io.harness.ng.core.api.impl.DelegateProfileManagerNgServiceImpl;
import io.harness.ng.core.api.impl.NGModulesServiceImpl;
import io.harness.ng.core.api.impl.NGSecretServiceV2Impl;
import io.harness.ng.core.api.impl.TokenServiceImpl;
import io.harness.ng.core.api.impl.UserGroupServiceImpl;
import io.harness.ng.core.encryptors.NGManagerKmsEncryptor;
import io.harness.ng.core.encryptors.NGManagerVaultEncryptor;
import io.harness.ng.core.entityactivity.event.EntityActivityCrudEventMessageListener;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageModule;
import io.harness.ng.core.entitysetupusage.event.SetupUsageChangeEventMessageListener;
import io.harness.ng.core.entitysetupusage.event.SetupUsageChangeEventMessageProcessor;
import io.harness.ng.core.event.ConnectorEntityCRUDStreamListener;
import io.harness.ng.core.event.ConnectorFeatureFlagStreamListener;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.event.MessageProcessor;
import io.harness.ng.core.event.OrganizationEntityCRUDStreamListener;
import io.harness.ng.core.event.OrganizationFeatureFlagStreamListener;
import io.harness.ng.core.event.ProjectEntityCRUDStreamListener;
import io.harness.ng.core.event.SecretEntityCRUDStreamListener;
import io.harness.ng.core.event.UserMembershipStreamListener;
import io.harness.ng.core.impl.OrganizationServiceImpl;
import io.harness.ng.core.impl.ProjectServiceImpl;
import io.harness.ng.core.outbox.ApiKeyEventHandler;
import io.harness.ng.core.outbox.DelegateProfileEventHandler;
import io.harness.ng.core.outbox.EnvironmentEventHandler;
import io.harness.ng.core.outbox.NextGenOutboxEventHandler;
import io.harness.ng.core.outbox.OrganizationEventHandler;
import io.harness.ng.core.outbox.ProjectEventHandler;
import io.harness.ng.core.outbox.SecretEventHandler;
import io.harness.ng.core.outbox.ServiceAccountEventHandler;
import io.harness.ng.core.outbox.ServiceOutBoxEventHandler;
import io.harness.ng.core.outbox.TokenEventHandler;
import io.harness.ng.core.outbox.UserEventHandler;
import io.harness.ng.core.outbox.UserGroupEventHandler;
import io.harness.ng.core.schema.YamlBaseUrlService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.user.service.impl.NgUserServiceImpl;
import io.harness.ng.core.user.service.impl.UserEntityCrudStreamListener;
import io.harness.ng.eventsframework.EventsFrameworkModule;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ng.serviceaccounts.service.impl.ServiceAccountServiceImpl;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.entities.AwsCodeCommitSCM.AwsCodeCommitSCMMapper;
import io.harness.ng.userprofile.entities.AzureDevOpsSCM.AzureDevOpsSCMMapper;
import io.harness.ng.userprofile.entities.BitbucketSCM.BitbucketSCMMapper;
import io.harness.ng.userprofile.entities.GithubSCM.GithubSCMMapper;
import io.harness.ng.userprofile.entities.GitlabSCM.GitlabSCMMapper;
import io.harness.ng.userprofile.entities.SourceCodeManager.SourceCodeManagerMapper;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;
import io.harness.ng.userprofile.services.api.UserInfoService;
import io.harness.ng.userprofile.services.impl.SourceCodeManagerServiceImpl;
import io.harness.ng.userprofile.services.impl.UserInfoServiceImpl;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.ng.webhook.services.impl.WebhookEventProcessingServiceImpl;
import io.harness.ng.webhook.services.impl.WebhookServiceImpl;
import io.harness.notification.module.NotificationClientModule;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.PipelineRemoteClientModule;
import io.harness.pms.listener.NgOrchestrationNotifyEventListener;
import io.harness.redis.RedisConfig;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.secretmanagerclient.SecretManagementClientModule;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.NextGenRegistrars;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.signup.SignupModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.time.TimeModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.user.UserClientModule;
import io.harness.version.VersionModule;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import software.wings.security.ThreadLocalUserProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.mongodb.morphia.converters.TypeConverter;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NextGenModule extends AbstractModule {
  public static final String SECRET_MANAGER_CONNECTOR_SERVICE = "secretManagerConnectorService";
  public static final String CONNECTOR_DECORATOR_SERVICE = "connectorDecoratorService";
  private final NextGenConfiguration appConfig;
  public NextGenModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "ngManager_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "ngManager_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "ngManager_delegateTaskProgressResponses")
        .build();
  }

  @Provides
  @Singleton
  CiDefaultEntityConfiguration getCiDefaultConfiguration() {
    return appConfig.getCiDefaultEntityConfiguration();
  }

  @Provides
  @Singleton
  LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
    return appConfig.getLogStreamingServiceConfig();
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient, appConfig));
  }

  @Provides
  private FileServiceClientFactory fileServiceClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new FileServiceClientFactory(appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), new ServiceTokenGenerator(),
        kryoConverterFactory, NG_MANAGER.getServiceId());
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return appConfig.getDistributedLockImplementation() == null ? MONGO : appConfig.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisLockConfig() {
    return appConfig.getRedisLockConfig();
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, NextGenConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("ngManager")
                                  .setConnection(appConfig.getMongoConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    NextGenApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Reflections reflections = new Reflections(HarnessPackages.IO_HARNESS);

    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType = reflections.getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  public OutboxPollConfiguration getOutboxPollConfiguration() {
    return appConfig.getOutboxPollConfig();
  }

  @Provides
  @Singleton
  @Named("GitSyncGrpcClientConfigs")
  public Map<Microservice, GrpcClientConfig> grpcClientConfigs() {
    return appConfig.getGitGrpcClientConfigs();
  }

  @Provides
  @Singleton
  CEAwsSetupConfig ceAwsSetupConfig() {
    return this.appConfig.getCeAwsSetupConfig();
  }

  @Provides
  @Singleton
  CEAzureSetupConfig ceAzureSetupConfig() {
    return this.appConfig.getCeAzureSetupConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    install(PrimaryVersionManagerModule.getInstance());
    install(DelegateServiceDriverModule.getInstance(false));
    install(TimeModule.getInstance());
    bind(NextGenConfiguration.class).toInstance(appConfig);

    install(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return appConfig.getMongoConfig();
      }
    });

    bind(CDOverviewDashboardService.class).to(CDOverviewDashboardServiceImpl.class);

    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (appConfig.getEnableDashboardTimescale() != null && appConfig.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(appConfig.getTimeScaleDBConfig() != null ? appConfig.getTimeScaleDBConfig()
                                                               : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }
    /*
    [secondary-db]: To use another DB, uncomment this and add @Named("primaryMongoConfig") to the above one

    install(new ProviderModule() {
       @Provides
       @Singleton
       @Named("secondaryMongoConfig")
       MongoConfig mongoConfig() {
         return appConfig.getSecondaryMongoConfig();
       }
     });*/
    bind(FileServiceClient.class).toProvider(FileServiceClientFactory.class).in(Scopes.SINGLETON);
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(appConfig.getLogStreamingServiceConfig().getBaseUrl())
                        .build());
    bind(WebhookEventService.class).to(WebhookServiceImpl.class);

    install(new AuthenticationSettingsModule(
        this.appConfig.getManagerClientConfig(), this.appConfig.getNextGenConfig().getManagerServiceSecret()));
    install(new ValidationModule(getValidatorFactory()));
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new ThreadLocalUserProvider();
      }
    });
    install(new NextGenPersistenceModule());
    install(new CoreModule());
    install(AccessControlMigrationModule.getInstance());
    install(UserClientModule.getInstance(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new InviteModule(appConfig.getBaseUrls(), appConfig.isNGAuthUIEnabled()));
    install(new SignupModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId(),
        appConfig.getSignupNotificationConfiguration(), appConfig.getBaseUrls().getNextGenUiUrl(),
        appConfig.getBaseUrls().getNextGenAuthUiUrl()));
    install(ConnectorModule.getInstance());
    install(new GitSyncModule());
    install(new DefaultOrganizationModule());
    install(new NGAggregateModule());
    install(NGModule.getInstance());
    install(ExceptionModule.getInstance());
    install(new EventsFrameworkModule(this.appConfig.getEventsFrameworkConfiguration()));
    install(new SecretManagementModule());
    install(new AccountClientModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.toString()));
    install(new PipelineRemoteClientModule(
        ServiceHttpClientConfig.builder().baseUrl(appConfig.getPipelineServiceClientConfig().getBaseUrl()).build(),
        appConfig.getNextGenConfig().getPipelineServiceSecret(), NG_MANAGER.toString()));
    install(new SecretManagementClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new SecretNGManagerClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new DelegateServiceDriverGrpcClientModule(this.appConfig.getNextGenConfig().getManagerServiceSecret(),
        this.appConfig.getGrpcClientConfig().getTarget(), this.appConfig.getGrpcClientConfig().getAuthority(), true));
    install(new EntitySetupUsageClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new ModulesClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(YamlSdkModule.getInstance());
    install(new AuditClientModule(this.appConfig.getAuditClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(),
        this.appConfig.isEnableAudit()));
    install(new NotificationClientModule(appConfig.getNotificationClientConfiguration()));

    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().addAll(NextGenRegistrars.kryoRegistrars).build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NextGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ManagerRegistrars.springConverters)
            .build();
      }
      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClasses() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(NextGenRegistrars.yamlSchemaRegistrars).build();
      }

      @Provides
      @Singleton
      BaseUrls getBaseUrls() {
        return appConfig.getBaseUrls();
      }
    });
    install(OrchestrationModule.getInstance(getOrchestrationConfig()));
    install(OrchestrationStepsModule.getInstance(null));
    install(EntitySetupUsageModule.getInstance());
    install(PersistentLockModule.getInstance());
    install(new TransactionOutboxModule());
    install(new ResourceGroupClientModule(appConfig.getResourceGroupClientConfig().getServiceConfig(),
        appConfig.getResourceGroupClientConfig().getSecret(), NG_MANAGER.getServiceId()));
    install(new NGFileServiceModule(appConfig.getFileServiceConfiguration().getFileStorageMode(),
        appConfig.getFileServiceConfiguration().getClusterName()));
    if (TRUE.equals(appConfig.getAccessControlAdminClientConfiguration().getMockAccessControlService())) {
      AccessControlAdminClientConfiguration accessControlAdminClientConfiguration =
          AccessControlAdminClientConfiguration.builder()
              .accessControlServiceConfig(appConfig.getNgManagerClientConfig())
              .accessControlServiceSecret(appConfig.getNextGenConfig().getNgManagerServiceSecret())
              .build();
      install(new AccessControlAdminClientModule(accessControlAdminClientConfiguration, NG_MANAGER.getServiceId()));
    } else {
      install(new AccessControlAdminClientModule(
          appConfig.getAccessControlAdminClientConfiguration(), NG_MANAGER.getServiceId()));
    }
    install(new MockRoleAssignmentModule());
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return appConfig.getSegmentConfiguration();
      }
    });

    install(new AbstractAccountModule() {
      @Override
      public AccountConfig accountConfiguration() {
        return appConfig.getAccountConfig();
      }
    });
    install(LicenseModule.getInstance());
    bind(AggregateUserService.class).to(AggregateUserServiceImpl.class);
    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(NextGenOutboxEventHandler.class);
    bind(ProjectService.class).to(ProjectServiceImpl.class);
    bind(OrganizationService.class).to(OrganizationServiceImpl.class);
    bind(NGModulesService.class).to(NGModulesServiceImpl.class);
    bind(NGSecretServiceV2.class).to(NGSecretServiceV2Impl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
    bind(ConnectorService.class).annotatedWith(Names.named(CONNECTOR_DECORATOR_SERVICE)).to(ConnectorServiceImpl.class);
    bind(ConnectorService.class)
        .annotatedWith(Names.named(SECRET_MANAGER_CONNECTOR_SERVICE))
        .to(SecretManagerConnectorServiceImpl.class);
    bind(NgUserService.class).to(NgUserServiceImpl.class);
    bind(UserGroupService.class).to(UserGroupServiceImpl.class);
    bind(YamlBaseUrlService.class).to(YamlBaseUrlServiceImpl.class);
    bind(DelegateProfileManagerNgService.class).to(DelegateProfileManagerNgServiceImpl.class);
    bind(UserInfoService.class).to(UserInfoServiceImpl.class);
    bind(WebhookService.class).to(WebhookServiceImpl.class);
    bind(WebhookEventProcessingService.class).to(WebhookEventProcessingServiceImpl.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(USER_ENTITY + ENTITY_CRUD))
        .to(UserEntityCrudStreamListener.class);
    bind(MessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.SETUP_USAGE_ENTITY))
        .to(SetupUsageChangeEventMessageProcessor.class);
    install(AccessControlClientModule.getInstance(
        appConfig.getAccessControlClientConfiguration(), NG_MANAGER.getServiceId()));

    bind(SourceCodeManagerService.class).to(SourceCodeManagerServiceImpl.class);
    bind(ApiKeyService.class).to(ApiKeyServiceImpl.class);
    bind(TokenService.class).to(TokenServiceImpl.class);

    MapBinder<SCMType, SourceCodeManagerMapper> sourceCodeManagerMapBinder =
        MapBinder.newMapBinder(binder(), SCMType.class, SourceCodeManagerMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.BITBUCKET).to(BitbucketSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITHUB).to(GithubSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITLAB).to(GitlabSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.AWS_CODE_COMMIT).to(AwsCodeCommitSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.AZURE_DEV_OPS).to(AzureDevOpsSCMMapper.class);

    registerEventsFrameworkMessageListeners();
    registerEncryptors();
  }

  void registerEncryptors() {
    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.HASHICORP_VAULT_ENCRYPTOR.getName()))
        .to(NGManagerVaultEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AZURE_VAULT_ENCRYPTOR.getName()))
        .to(NGManagerVaultEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AWS_KMS_ENCRYPTOR.getName()))
        .to(NGManagerKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GCP_KMS_ENCRYPTOR.getName()))
        .to(NGManagerKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.LOCAL_ENCRYPTOR.getName()))
        .to(LocalEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_AWS_KMS_ENCRYPTOR.getName()))
        .to(AwsKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_GCP_KMS_ENCRYPTOR.getName()))
        .to(GcpKmsEncryptor.class);
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ORGANIZATION).to(OrganizationEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PROJECT).to(ProjectEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(USER_GROUP).to(UserGroupEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SECRET).to(SecretEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(USER).to(UserEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(DELEGATE_CONFIGURATION).to(DelegateProfileEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SERVICE_ACCOUNT).to(ServiceAccountEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CONNECTOR).to(ConnectorEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SERVICE).to(ServiceOutBoxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ENVIRONMENT).to(EnvironmentEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(API_KEY).to(ApiKeyEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(TOKEN).to(TokenEventHandler.class);
  }

  private void registerEventsFrameworkMessageListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(ORGANIZATION_ENTITY + ENTITY_CRUD))
        .to(OrganizationEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(PROJECT_ENTITY + ENTITY_CRUD))
        .to(ProjectEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CONNECTOR_ENTITY + ENTITY_CRUD))
        .to(ConnectorEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(SECRET_ENTITY + ENTITY_CRUD))
        .to(SecretEntityCRUDStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(ORGANIZATION_ENTITY + FEATURE_FLAG_STREAM))
        .to(OrganizationFeatureFlagStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CONNECTOR_ENTITY + FEATURE_FLAG_STREAM))
        .to(ConnectorFeatureFlagStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
        .to(UserMembershipStreamListener.class);

    bind(MessageListener.class).annotatedWith(Names.named(SETUP_USAGE)).to(SetupUsageChangeEventMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
        .to(EntityActivityCrudEventMessageListener.class);
    // todo(abhinav): Move to git sync msvc if it breaks out.
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.HARNESS_TO_GIT_PUSH))
        .to(HarnessToGitPushMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM))
        .to(GitPushEventStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM))
        .to(GitBranchHookEventStreamListener.class);
    bind(ServiceAccountService.class).to(ServiceAccountServiceImpl.class);
  }

  private OrchestrationModuleConfig getOrchestrationConfig() {
    return OrchestrationModuleConfig.builder()
        .serviceName("CD_NG")
        .withPMS(appConfig.getShouldConfigureWithPMS())
        .expressionEvaluatorProvider(new CDExpressionEvaluatorProvider())
        .publisherName(NgOrchestrationNotifyEventListener.NG_ORCHESTRATION)
        .build();
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }
}
