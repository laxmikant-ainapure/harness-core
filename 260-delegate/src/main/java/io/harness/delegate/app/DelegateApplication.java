package io.harness.delegate.app;

import static io.harness.annotations.dev.Module._420_DELEGATE_AGENT;
import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.delegate.message.MessageConstants.DELEGATE_DASH;
import static io.harness.delegate.message.MessageConstants.NEW_DELEGATE;
import static io.harness.delegate.message.MessageConstants.WATCHER_DATA;
import static io.harness.delegate.message.MessageConstants.WATCHER_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.WATCHER_PROCESS;
import static io.harness.delegate.message.MessengerType.DELEGATE;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.delegate.service.DelegateAgentServiceImpl.getDelegateId;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractAuthority;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractTarget;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.base.Charsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.message.MessageService;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.delegate.task.citasks.CITaskFactoryModule;
import io.harness.delegate.task.k8s.apiclient.KubernetesApiClientFactoryModule;
import io.harness.event.client.EventPublisher;
import io.harness.event.client.impl.EventPublisherConstants;
import io.harness.event.client.impl.appender.AppenderModule;
import io.harness.event.client.impl.appender.AppenderModule.Config;
import io.harness.govern.ProviderModule;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.grpc.delegateservice.DelegateServiceGrpcAgentClientModule;
import io.harness.grpc.pingpong.PingPongClient;
import io.harness.grpc.pingpong.PingPongModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.managerclient.ManagerClientModule;
import io.harness.perpetualtask.PerpetualTaskWorkerModule;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.YamlUtils;
import io.harness.serializer.kryo.CvNextGenCommonsBeansKryoRegistrar;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.utils.ProcessControl;

import software.wings.delegatetasks.k8s.client.KubernetesClientFactoryModule;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.ning.http.client.AsyncHttpClient;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

@Slf4j
@TargetModule(_420_DELEGATE_AGENT)
public class DelegateApplication {
  private static String processId = String.valueOf(ProcessControl.myProcessId());
  private static DelegateConfiguration configuration;

  public static String getProcessId() {
    return processId;
  }

  public static DelegateConfiguration getConfiguration() {
    return configuration;
  }

  public static void main(String... args) throws IOException {
    try {
      String proxyUser = System.getenv("PROXY_USER");
      if (isNotBlank(proxyUser)) {
        System.setProperty("http.proxyUser", proxyUser);
        System.setProperty("https.proxyUser", proxyUser);
      }
      String proxyPassword = System.getenv("PROXY_PASSWORD");
      if (isNotBlank(proxyPassword)) {
        System.setProperty("http.proxyPassword", proxyPassword);
        System.setProperty("https.proxyPassword", proxyPassword);
      }

      File configFile = new File(args[0]);
      configuration = new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), DelegateConfiguration.class);

      String watcherProcess = null;
      if (args.length > 1 && StringUtils.equals(args[1], "watched")) {
        watcherProcess = args[2];
      }

      // Optionally remove existing handlers attached to j.u.l root logger
      SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)

      // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
      // the initialization phase of your application
      SLF4JBridgeHandler.install();

      // Set logging level
      java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

      initializeLogging();
      log.info("Starting Delegate");
      log.info("Process: {}", ManagementFactory.getRuntimeMXBean().getName());
      DelegateApplication delegateApplication = new DelegateApplication();
      delegateApplication.run(configuration, watcherProcess);
    } catch (RuntimeException | IOException exception) {
      log.error("Delegate process initialization failed", exception);
      throw exception;
    }
  }

  private void run(DelegateConfiguration configuration, String watcherProcess) {
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("sync-task-%d").setPriority(Thread.NORM_PRIORITY).build()));

    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar> > registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar> >builder()
            .addAll(ManagerRegistrars.kryoRegistrars)
            .add(CvNextGenCommonsBeansKryoRegistrar.class)
            .build();
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(DelegateConfiguration.class).toInstance(configuration);
      }
    });
    modules.add(new ManagerClientModule(configuration.getManagerUrl(), configuration.getVerificationServiceUrl(),
        configuration.getCvNextGenUrl(), configuration.getAccountId(), configuration.getAccountSecret()));
    modules.add(new LogStreamingModule(configuration.getLogStreamingServiceBaseUrl()));
    String managerHostAndPort = System.getenv("MANAGER_HOST_AND_PORT");
    modules.add(new ManagerGrpcClientModule(
        ManagerGrpcClientModule.Config.builder()
            .target(Optional.ofNullable(configuration.getManagerTarget())
                        .orElseGet(() -> extractTarget(managerHostAndPort)))
            .authority(Optional.ofNullable(configuration.getManagerAuthority())
                           .orElseGet(() -> extractAuthority(managerHostAndPort, "manager")))
            .accountId(configuration.getAccountId())
            .accountSecret(configuration.getAccountSecret())
            .build()));
    if (!isOnPrem(System.getenv().get(DEPLOY_MODE))) {
      modules.add(new PingPongModule());
      modules.add(new PerpetualTaskWorkerModule());
      modules.add(DelegateServiceGrpcAgentClientModule.getInstance());
    }
    modules.add(KubernetesClientFactoryModule.getInstance());
    modules.add(KubernetesApiClientFactoryModule.getInstance());
    modules.add(new CITaskFactoryModule());
    modules.add(new AppenderModule(Config.builder()
                                       .queueFilePath(Optional.ofNullable(configuration.getQueueFilePath())
                                                          .orElse(EventPublisherConstants.DEFAULT_QUEUE_FILE_PATH))
                                       .build(),
        () -> getDelegateId().orElse("UNREGISTERED")));
    modules.add(DelegateModule.getInstance());

    if (configuration.isGrpcServiceEnabled()) {
      modules.add(new DelegateGrpcServiceModule(
          configuration.getGrpcServiceConnectorPort(), configuration.getManagerServiceSecret()));
    }

    Injector injector = Guice.createInjector(modules);
    MessageService messageService = injector.getInstance(MessageService.class);

    // Add JVM shutdown hook so as to have a clean shutdown
    addShutdownHook(injector, messageService);

    boolean watched = watcherProcess != null;
    if (watched) {
      log.info("Sending watcher {} new delegate process ID: {}", watcherProcess, processId);
      messageService.writeMessageToChannel(WATCHER, watcherProcess, NEW_DELEGATE, processId);
      Map<String, Object> watcherData = new HashMap<>();
      watcherData.put(WATCHER_HEARTBEAT, System.currentTimeMillis());
      watcherData.put(WATCHER_PROCESS, watcherProcess);
      messageService.putAllData(WATCHER_DATA, watcherData);
    }
    if (!isOnPrem(System.getenv().get(DEPLOY_MODE))) {
      injector.getInstance(PingPongClient.class).startAsync();
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> injector.getInstance(PingPongClient.class).stopAsync()));
    DelegateAgentService delegateService = injector.getInstance(DelegateAgentService.class);
    delegateService.run(watched);

    System.exit(0);
  }

  private void addShutdownHook(Injector injector, MessageService messageService) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      messageService.closeChannel(DELEGATE, processId);
      messageService.closeData(DELEGATE_DASH + processId);
      log.info("Message service has been closed.");

      injector.getInstance(ExecutorService.class).shutdown();
      injector.getInstance(EventPublisher.class).shutdown();
      log.info("Executor services have been shut down.");

      injector.getInstance(AsyncHttpClient.class).close();
      log.info("Async HTTP client has been closed.");

      ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
      if (loggerFactory instanceof LoggerContext) {
        LoggerContext context = (LoggerContext) loggerFactory;
        context.stop();
      }
      log.info("Log manager has been shutdown and logs have been flushed.");
    }));
  }
}
