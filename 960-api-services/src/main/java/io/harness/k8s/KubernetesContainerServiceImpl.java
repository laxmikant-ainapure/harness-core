package io.harness.k8s;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ACCESS_DENIED;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.K8sConstants.CLIENT_ID_KEY;
import static io.harness.k8s.K8sConstants.CLIENT_SECRET_KEY;
import static io.harness.k8s.K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY;
import static io.harness.k8s.K8sConstants.ID_TOKEN_KEY;
import static io.harness.k8s.K8sConstants.ISSUER_URL_KEY;
import static io.harness.k8s.K8sConstants.KUBE_CONFIG_OIDC_TEMPLATE;
import static io.harness.k8s.K8sConstants.KUBE_CONFIG_TEMPLATE;
import static io.harness.k8s.K8sConstants.MASTER_URL;
import static io.harness.k8s.K8sConstants.NAME;
import static io.harness.k8s.K8sConstants.NAMESPACE;
import static io.harness.k8s.K8sConstants.NAMESPACE_KEY;
import static io.harness.k8s.K8sConstants.OIDC_AUTH_NAME;
import static io.harness.k8s.K8sConstants.OIDC_AUTH_NAME_VAL;
import static io.harness.k8s.K8sConstants.OIDC_CLIENT_ID;
import static io.harness.k8s.K8sConstants.OIDC_CLIENT_SECRET;
import static io.harness.k8s.K8sConstants.OIDC_ID_TOKEN;
import static io.harness.k8s.K8sConstants.OIDC_ISSUER_URL;
import static io.harness.k8s.K8sConstants.OIDC_RERESH_TOKEN;
import static io.harness.k8s.K8sConstants.REFRESH_TOKEN;
import static io.harness.k8s.KubernetesConvention.DASH;
import static io.harness.k8s.KubernetesConvention.ReleaseHistoryKeyName;
import static io.harness.k8s.KubernetesConvention.getPrefixFromControllerName;
import static io.harness.k8s.KubernetesConvention.getRevisionFromControllerName;
import static io.harness.k8s.KubernetesConvention.getServiceNameFromControllerName;
import static io.harness.k8s.model.ContainerApiVersions.KUBERNETES_V1;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

import io.harness.container.ContainerInfo;
import io.harness.container.ContainerInfo.ContainerInfoBuilder;
import io.harness.container.ContainerInfo.Status;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.k8s.oidc.OidcTokenRetriever;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.oidc.model.OidcTokenRequestData;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneableReplicationController;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetList;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.DoneableDaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DoneableDeployment;
import io.fabric8.kubernetes.api.model.extensions.DoneableReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.DoneableStatefulSet;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetList;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigList;
import io.fabric8.openshift.api.model.DoneableDeploymentConfig;
import io.fabric8.openshift.client.dsl.DeployableScalableResource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.VersionInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.IstioResource;
import me.snowdrop.istio.api.internal.IstioSpecRegistry;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationRule;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationRuleBuilder;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationWeight;
import me.snowdrop.istio.api.networking.v1alpha3.DoneableDestinationRule;
import me.snowdrop.istio.api.networking.v1alpha3.DoneableVirtualService;
import me.snowdrop.istio.api.networking.v1alpha3.VirtualService;
import me.snowdrop.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import me.snowdrop.istio.api.networking.v1alpha3.VirtualServiceSpec;
import me.snowdrop.istio.client.IstioClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Created by brett on 2/9/17
 */
@Singleton
@Slf4j
public class KubernetesContainerServiceImpl implements KubernetesContainerService {
  private static final String RUNNING = "Running";
  private static final String RESOURCE_NAME_FIELD = "metadata.name";
  private static final String K8S_SELECTOR_FORMAT = "%s=%s";
  private static final String K8S_SELECTOR_DELIMITER = ",";
  public static final String METRICS_SERVER_ABSENT = "CE.MetricsServerCheck: Please install metrics server.";
  public static final String RESOURCE_PERMISSION_REQUIRED =
      "CE: The provided serviceaccount is missing the following permissions: %n %s. Please grant these to the service account.";

  @Inject private KubernetesHelperService kubernetesHelperService = new KubernetesHelperService();
  @Inject private TimeLimiter timeLimiter;
  @Inject private Clock clock;
  @Inject private K8sResourceValidatorImpl k8sResourceValidator;
  @Inject private OidcTokenRetriever oidcTokenRetriever;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  @Override
  public HasMetadata createOrReplaceController(KubernetesConfig kubernetesConfig, HasMetadata definition) {
    String name = definition.getMetadata().getName();
    log.info("Creating {} {}", definition.getKind(), name);

    // TODO - Use definition.getKind()
    HasMetadata controller = null;
    if (definition instanceof ReplicationController) {
      controller = rcOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                       .createOrReplace((ReplicationController) definition);
    } else if (definition instanceof Deployment) {
      controller = deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                       .createOrReplace((Deployment) definition);
    } else if (definition instanceof ReplicaSet) {
      controller =
          replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).createOrReplace((ReplicaSet) definition);
    } else if (definition instanceof StatefulSet) {
      HasMetadata existing = getController(kubernetesConfig, name);
      if (existing != null && existing.getKind().equals("StatefulSet")) {
        controller = statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                         .withName(name)
                         .patch((StatefulSet) definition);
      } else {
        controller =
            statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).create((StatefulSet) definition);
      }
    } else if (definition instanceof DaemonSet) {
      controller =
          daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).createOrReplace((DaemonSet) definition);
    }
    return controller;
  }

  @Override
  public HasMetadata getController(KubernetesConfig kubernetesConfig, String name) {
    return getController(kubernetesConfig, name, kubernetesConfig.getNamespace());
  }

  @Override
  public HasMetadata getController(KubernetesConfig kubernetesConfig, String name, String namespace) {
    try {
      Callable<HasMetadata> controller = getControllerInternal(kubernetesConfig, name, namespace);
      return timeLimiter.callWithTimeout(controller, 2L, TimeUnit.MINUTES, true);
    } catch (WingsException e) {
      throw e;
    } catch (UncheckedTimeoutException e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Timed out while getting controller");
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Error while getting controller");
    }
  }

  @SuppressWarnings("squid:S3776")
  private Callable<HasMetadata> getControllerInternal(
      KubernetesConfig kubernetesConfig, String name, String namespace) {
    return () -> {
      HasMetadata controller = null;
      log.info("Trying to get controller for name {}", name);
      if (isNotBlank(name)) {
        boolean success = false;
        boolean allFailed = true;
        while (!success) {
          try {
            try {
              controller = rcOperations(kubernetesConfig, namespace).withName(name).get();
              allFailed = false;
            } catch (Exception e) {
              // Ignore
            }
            if (controller == null) {
              try {
                controller = deploymentOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = replicaOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = statefulOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = daemonOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = deploymentConfigOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (allFailed) {
              controller = deploymentOperations(kubernetesConfig, namespace).withName(name).get();
            } else {
              success = true;
            }
          } catch (Exception e) {
            log.warn("Exception while getting controller {}: {}:{}", name, e.getClass().getSimpleName(),
                ExceptionUtils.getMessage(e));
            if (e.getCause() != null) {
              log.warn("Caused by: {}:{}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }

            // Special handling of k8s client 401/403 error. No need to retry...
            if (e instanceof KubernetesClientException) {
              KubernetesClientException clientException = (KubernetesClientException) e;
              int code = clientException.getCode();
              // error code 0 means connectivity issue. It will retry.
              switch (code) {
                case SC_UNAUTHORIZED:
                  throw new InvalidRequestException("Invalid credentials", e, INVALID_CREDENTIAL, USER);
                case SC_FORBIDDEN:
                  throw new InvalidRequestException("Access Denied", e, ACCESS_DENIED, USER);
                default:
                  log.warn("Got KubernetesClientException with error code {}", code);
                  break;
              }
            }

            sleep(ofSeconds(1));
            log.info("Retrying getController {} ...", name);
          }
        }
      }
      log.info("Got controller for name {}", name);
      return controller;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<? extends HasMetadata> getControllers(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    List<? extends HasMetadata> controllers = new ArrayList<>();
    boolean allFailed = true;
    try {
      controllers.addAll(
          (List) rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withLabels(labels).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) deploymentConfigOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    if (allFailed) {
      controllers.addAll((List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
    }
    return controllers;
  }

  @Override
  public void validate(KubernetesConfig kubernetesConfig) {
    tryListControllersKubectl(kubernetesConfig);
  }

  @Override
  public void tryListControllersKubectl(final KubernetesConfig kubernetesConfig) {
    ProcessResult result = null;
    final File kubeConfigDir = Files.createTempDir();
    try (ByteArrayOutputStream errStream = new ByteArrayOutputStream()) {
      final String kubeconfigFileContent = getConfigFileContent(kubernetesConfig);
      final String kubeconfigPath = Paths.get(kubeConfigDir.getPath(), K8sConstants.KUBECONFIG_FILENAME).toString();
      FileIo.writeUtf8StringToFile(kubeconfigPath, kubeconfigFileContent);
      final Kubectl client = getKubectlClient();
      for (final String workloadType : Arrays.asList(
               Kind.ReplicaSet.name(), Kind.StatefulSet.name(), Kind.DaemonSet.name(), Kind.Deployment.name())) {
        errStream.reset();
        result = client.get()
                     .resources(workloadType)
                     .namespace(kubernetesConfig.getNamespace())
                     .execute(kubeConfigDir.getPath(), null, errStream, false);
        if (0 == result.getExitValue()) {
          return;
        }
      }
      throw new InvalidRequestException(errStream.toString("UTF-8"), USER);
    } catch (IOException ex) {
      throw new GeneralException("Could not list deployments because kubeconfig could not be created", ex);
    } catch (WingsException ex) {
      throw ex;
    } catch (KubernetesClientException ex) {
      throw new InvalidRequestException(ex.getMessage(), ex, USER);
    } catch (Exception ex) {
      log.error("Failed to list Deployments", ex);
      throw new InvalidRequestException("Failed to List Deployments", USER);
    } finally {
      cleanupDir(kubeConfigDir);
    }
  }

  @VisibleForTesting
  Kubectl getKubectlClient() {
    return Kubectl.client(k8sGlobalConfigService.getKubectlPath(), K8sConstants.KUBECONFIG_FILENAME);
  }

  private void cleanupDir(File kubeConfigDir) {
    try {
      FileIo.deleteDirectoryAndItsContentIfExists(kubeConfigDir.getPath());
    } catch (IOException e) {
      log.warn(format("Failed to cleanup directory %s", kubeConfigDir.getPath()), e);
    }
  }

  public void validateCEPermissions(KubernetesConfig kubernetesConfig) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    validateCEMetricsServer(apiClient);
    validateCEResourcePermission(apiClient);
  }

  public void validateCEMetricsServer(ApiClient apiClient) {
    try {
      if (!k8sResourceValidator.validateMetricsServer(apiClient)) {
        throw new InvalidRequestException(METRICS_SERVER_ABSENT);
      }
    } catch (ApiException e) {
      log.error("Error validating Metrics Server", e);
      throw new InvalidRequestException(
          format("CE.MetricsServerCheck: code=%s message=%s. Try again, if it persists contact Harness Support.",
              e.getCode(), e.getResponseBody()));
    }
  }

  public void validateCEResourcePermission(ApiClient apiClient) {
    String result = k8sResourceValidator.validateCEPermissions(apiClient);
    if (!result.isEmpty()) {
      throw new InvalidRequestException(format(RESOURCE_PERMISSION_REQUIRED, result));
    }
  }

  @Override
  public CEK8sDelegatePrerequisite.MetricsServerCheck validateMetricsServer(KubernetesConfig kubernetesConfig) {
    try {
      boolean isInstalled =
          k8sResourceValidator.validateMetricsServer(kubernetesHelperService.getApiClient(kubernetesConfig));
      return CEK8sDelegatePrerequisite.MetricsServerCheck.builder().isInstalled(isInstalled).build();
    } catch (ApiException ex) {
      log.error("validateMetricsServer:ApiException ", ex);
      return CEK8sDelegatePrerequisite.MetricsServerCheck.builder()
          .isInstalled(false)
          .message(ex.getCode() + ":" + ex.getMessage())
          .build();
    }
  }

  @Override
  public List<CEK8sDelegatePrerequisite.Rule> validateCEResourcePermissions(KubernetesConfig kubernetesConfig) {
    return k8sResourceValidator.validateCEPermissions2(kubernetesHelperService.getApiClient(kubernetesConfig));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<? extends HasMetadata> listControllers(KubernetesConfig kubernetesConfig) {
    List<? extends HasMetadata> controllers = new ArrayList<>();
    boolean allFailed = true;
    try {
      controllers.addAll((List) rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll(
          (List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll(
          (List) statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    if (allFailed) {
      controllers.addAll(
          (List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
    }
    return controllers;
  }

  @Override
  public void deleteController(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting controller {}", name);
    if (isNotBlank(name)) {
      HasMetadata controller = getController(kubernetesConfig, name);
      if (controller instanceof ReplicationController) {
        rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof Deployment) {
        deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof ReplicaSet) {
        replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof StatefulSet) {
        statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof DaemonSet) {
        daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      }
    }
  }

  @Override
  public HorizontalPodAutoscaler createOrReplaceAutoscaler(KubernetesConfig kubernetesConfig, String autoscalerYaml) {
    if (isNotBlank(autoscalerYaml)) {
      HorizontalPodAutoscaler hpa;
      try {
        hpa = KubernetesHelper.loadYaml(autoscalerYaml);
        hpa.getMetadata().setResourceVersion(null);
      } catch (Exception e) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
            .addParam("args", "Couldn't parse horizontal pod autoscaler YAML: " + autoscalerYaml);
      }
      String api = kubernetesHelperService.trimVersion(hpa.getApiVersion());

      if (KUBERNETES_V1.getVersionName().equals(api)) {
        return kubernetesHelperService.hpaOperations(kubernetesConfig).createOrReplace(hpa);
      } else {
        return kubernetesHelperService.hpaOperationsForCustomMetricHPA(kubernetesConfig, api).createOrReplace(hpa);
      }
    }
    return null;
  }

  @Override
  public HorizontalPodAutoscaler getAutoscaler(KubernetesConfig kubernetesConfig, String name, String apiVersion) {
    if (KUBERNETES_V1.getVersionName().equals(apiVersion) || isEmpty(apiVersion)) {
      return kubernetesHelperService.hpaOperations(kubernetesConfig).withName(name).get();
    } else {
      return kubernetesHelperService.hpaOperationsForCustomMetricHPA(kubernetesConfig, apiVersion).withName(name).get();
    }
  }

  @Override
  public void deleteAutoscaler(KubernetesConfig kubernetesConfig, String name) {
    kubernetesHelperService.hpaOperations(kubernetesConfig).withName(name).delete();
  }

  @Override
  public List<ContainerInfo> setControllerPodCount(KubernetesConfig kubernetesConfig, String clusterName,
      String controllerName, int previousCount, int desiredCount, int serviceSteadyStateTimeout,
      LogCallback logCallback) {
    boolean sizeChanged = previousCount != desiredCount;
    long startTime = clock.millis();
    List<Pod> originalPods = getRunningPods(kubernetesConfig, controllerName);
    if (sizeChanged) {
      logCallback.saveExecutionLog(format("Resizing controller [%s] in cluster [%s] from %s to %s instances",
          controllerName, clusterName, previousCount, desiredCount));
      HasMetadata controller = getController(kubernetesConfig, controllerName);

      if (controller == null) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT)
            .addParam("args", "Could not find a controller named " + controllerName);
      }
      if (controller instanceof ReplicationController) {
        rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(controllerName).scale(desiredCount);
      } else if (controller instanceof Deployment) {
        deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof ReplicaSet) {
        replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof StatefulSet) {
        statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof DaemonSet) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT)
            .addParam("args", "DaemonSet runs one instance per cluster node and cannot be scaled.");
      }

      log.info("Scaled controller {} in cluster {} from {} to {} instances", controllerName, clusterName, previousCount,
          desiredCount);
    } else {
      logCallback.saveExecutionLog(
          format("Controller [%s] in cluster [%s] stays at %s instances", controllerName, clusterName, previousCount));
    }
    return getContainerInfosWhenReady(kubernetesConfig, controllerName, previousCount, desiredCount,
        serviceSteadyStateTimeout, originalPods, false, logCallback, sizeChanged, startTime,
        kubernetesConfig.getNamespace());
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public List<ContainerInfo> getContainerInfosWhenReady(KubernetesConfig kubernetesConfig, String controllerName,
      int previousCount, int desiredCount, int serviceSteadyStateTimeout, List<Pod> originalPods,
      boolean isNotVersioned, LogCallback logCallback, boolean wait, long startTime, String namespace) {
    List<Pod> pods = wait ? waitForPodsToBeRunning(kubernetesConfig, controllerName, previousCount, desiredCount,
                         serviceSteadyStateTimeout, originalPods, isNotVersioned, startTime, namespace, logCallback)
                          : originalPods;

    HasMetadata controllerInfo = getController(kubernetesConfig, controllerName, namespace);
    if (controllerInfo == null) {
      throw new InvalidRequestException(format("Could not find a controller named %s", controllerName));
    }
    int controllerDesiredCount = getControllerPodCount(controllerInfo);

    if (desiredCount == -1) {
      // This indicates wait for all pods to be in steady state. In case of HPA you won't know absolute numbers
      desiredCount = controllerDesiredCount;
    }

    Set<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toSet());
    List<ContainerInfo> containerInfos = new ArrayList<>();
    boolean hasErrors = false;
    if (wait && (pods.size() != desiredCount || controllerDesiredCount != desiredCount)) {
      hasErrors = true;
      String msg = "";
      if (controllerDesiredCount != desiredCount) {
        msg = format("Controller replica count is set to %d instead of %d. ", controllerDesiredCount, desiredCount);
      }
      if (pods.size() != desiredCount) {
        msg += format("Pod count did not reach desired count (%d/%d)", pods.size(), desiredCount);
      }
      log.error(msg);
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
    }
    for (Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      String containerId = !pod.getStatus().getContainerStatuses().isEmpty()
          ? StringUtils.substring(pod.getStatus().getContainerStatuses().get(0).getContainerID(), 9, 21)
          : "";
      ContainerInfoBuilder containerInfoBuilder = ContainerInfo.builder()
                                                      .hostName(podName)
                                                      .ip(pod.getStatus().getPodIP())
                                                      .containerId(containerId)
                                                      .workloadName(controllerName)
                                                      .podName(podName)
                                                      .newContainer(!originalPodNames.contains(podName));

      HasMetadata controller = getController(kubernetesConfig, controllerName, namespace);
      PodTemplateSpec podTemplateSpec = null;
      if (null != controller) {
        podTemplateSpec = getPodTemplateSpec(controller);
      } else {
        log.warn("podTemplateSpec is null.");
      }
      Set<String> images = emptySet();
      if (null != podTemplateSpec) {
        images = getControllerImages(podTemplateSpec);
      } else {
        log.warn("Images is null.");
      }

      if (desiredCount > 0 && !podHasImages(pod, images)) {
        hasErrors = true;
        String msg = format("Pod %s does not have image %s", podName, images);
        log.error(msg);
        logCallback.saveExecutionLog(msg, LogLevel.ERROR);
      }

      if (isNotVersioned || desiredCount > previousCount) {
        if (!isRunning(pod)) {
          hasErrors = true;
          String msg = format("Pod %s failed to start", podName);
          log.error(msg);
          logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        }

        if (!inSteadyState(pod)) {
          hasErrors = true;
          String msg = format("Pod %s failed to reach steady state", podName);
          log.error(msg);
          logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        }
      }

      if (!hasErrors) {
        containerInfoBuilder.status(Status.SUCCESS);
        log.info("Pod {} started successfully", podName);
        logCallback.saveExecutionLog(format("Pod [%s] is running. Host IP: %s. Pod IP: %s", podName,
            pod.getStatus().getHostIP(), pod.getStatus().getPodIP()));
      } else {
        containerInfoBuilder.status(Status.FAILURE);
        String containerMessage = Joiner.on("], [").join(
            pod.getStatus().getContainerStatuses().stream().map(this::getContainerStatusMessage).collect(toList()));
        String conditionMessage = Joiner.on("], [").join(
            pod.getStatus().getConditions().stream().map(this::getPodConditionMessage).collect(toList()));
        String reason = Joiner.on("], [").join(pod.getStatus()
                                                   .getContainerStatuses()
                                                   .stream()
                                                   .map(containerStatus
                                                       -> containerStatus.getState().getTerminated() != null
                                                           ? containerStatus.getState().getTerminated().getReason()
                                                           : containerStatus.getState().getWaiting() != null
                                                           ? containerStatus.getState().getWaiting().getReason()
                                                           : RUNNING)
                                                   .collect(toList()));
        String msg =
            format("Pod [%s] has state [%s]. Current status: phase - %s. Container status: [%s]. Condition: [%s].",
                podName, reason, pod.getStatus().getPhase(), containerMessage, conditionMessage);
        log.error(msg);
        logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        logCallback.saveExecutionLog("\nCheck Kubernetes console for more information");
      }
      containerInfos.add(containerInfoBuilder.build());
    }
    return containerInfos;
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCounts(
      KubernetesConfig kubernetesConfig, String containerServiceName) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    String controllerNamePrefix = getPrefixFromControllerName(containerServiceName);
    listControllers(kubernetesConfig)
        .stream()
        .filter(ctrl -> controllerNamePrefix.equals(getPrefixFromControllerName(ctrl.getMetadata().getName())))
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .sorted(comparingInt(ctrl -> getRevisionFromControllerName(ctrl.getMetadata().getName()).orElse(-1)))
        .forEach(ctrl -> result.put(ctrl.getMetadata().getName(), getControllerPodCount(ctrl)));
    return result;
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCountsWithLabels(
      KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    getControllers(kubernetesConfig, labels)
        .stream()
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .sorted(comparingInt(
            ctrl -> Integer.parseInt(ctrl.getMetadata().getLabels().get(HARNESS_KUBERNETES_REVISION_LABEL_KEY))))
        .forEach(ctrl -> result.put(ctrl.getMetadata().getName(), getControllerPodCount(ctrl)));
    return result;
  }

  @Override
  public Map<String, String> getActiveServiceImages(
      KubernetesConfig kubernetesConfig, String containerServiceName, String imagePrefix) {
    Map<String, String> result = new HashMap<>();
    String controllerNamePrefix = getPrefixFromControllerName(containerServiceName);
    listControllers(kubernetesConfig)
        .stream()
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> ctrl.getMetadata().getName().startsWith(controllerNamePrefix))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .filter(ctrl -> getRevisionFromControllerName(ctrl.getMetadata().getName()).isPresent())
        .forEach(ctrl
            -> result.put(ctrl.getMetadata().getName(),
                requireNonNull(getPodTemplateSpec(ctrl))
                    .getSpec()
                    .getContainers()
                    .stream()
                    .map(Container::getImage)
                    .filter(image -> image.startsWith(imagePrefix + ":"))
                    .findFirst()
                    .orElse("none")));
    return result;
  }

  private boolean inSteadyState(Pod pod) {
    List<PodCondition> conditions = pod.getStatus().getConditions();
    return isNotEmpty(conditions)
        && conditions.stream().allMatch(podCondition -> "True".equals(podCondition.getStatus()));
  }

  private boolean isRunning(Pod pod) {
    return pod.getStatus().getPhase().equals(RUNNING);
  }

  private boolean podHasImages(Pod pod, Set<String> images) {
    return pod.getSpec().getContainers().stream().map(Container::getImage).collect(toList()).containsAll(images);
  }

  private String getContainerStatusMessage(ContainerStatus status) {
    ContainerStateWaiting waiting = status.getState().getWaiting();
    ContainerStateTerminated terminated = status.getState().getTerminated();
    ContainerStateRunning running = status.getState().getRunning();
    String msg = status.getName();
    if (running != null) {
      msg += ": Started at " + running.getStartedAt();
    } else if (terminated != null) {
      msg += ": " + terminated.getReason() + " - " + terminated.getMessage();
    } else if (waiting != null) {
      msg += ": " + waiting.getReason() + " - " + waiting.getMessage();
    }
    return msg;
  }

  private String getPodConditionMessage(PodCondition cond) {
    String msg = cond.getType() + ": " + cond.getStatus();
    if (cond.getReason() != null) {
      msg += " - " + cond.getReason();
    }
    if (cond.getMessage() != null) {
      msg += " - " + cond.getMessage();
    }
    return msg;
  }

  @Override
  public Optional<Integer> getControllerPodCount(KubernetesConfig kubernetesConfig, String name) {
    HasMetadata controller = getController(kubernetesConfig, name);
    if (controller != null) {
      Integer count = getControllerPodCount(controller);
      return count == null ? Optional.empty() : Optional.of(count);
    }
    return Optional.empty();
  }

  @Override
  public Integer getControllerPodCount(HasMetadata controller) {
    if (controller instanceof ReplicationController) {
      return ((ReplicationController) controller).getSpec().getReplicas();
    } else if (controller instanceof Deployment) {
      return ((Deployment) controller).getSpec().getReplicas();
    } else if (controller instanceof ReplicaSet) {
      return ((ReplicaSet) controller).getSpec().getReplicas();
    } else if (controller instanceof StatefulSet) {
      return ((StatefulSet) controller).getSpec().getReplicas();
    } else if (controller instanceof DaemonSet) {
      return ((DaemonSet) controller).getStatus().getDesiredNumberScheduled();
    } else if (controller instanceof DeploymentConfig) {
      return ((DeploymentConfig) controller).getSpec().getReplicas();
    } else {
      throw new InvalidRequestException(
          format("Unhandled kubernetes resource type [%s] for getting the pod count", controller.getKind()));
    }
  }

  @Override
  public PodTemplateSpec getPodTemplateSpec(HasMetadata controller) {
    PodTemplateSpec podTemplateSpec = null;
    if (controller instanceof ReplicationController) {
      podTemplateSpec = ((ReplicationController) controller).getSpec().getTemplate();
    } else if (controller instanceof Deployment) {
      podTemplateSpec = ((Deployment) controller).getSpec().getTemplate();
    } else if (controller instanceof DaemonSet) {
      podTemplateSpec = ((DaemonSet) controller).getSpec().getTemplate();
    } else if (controller instanceof ReplicaSet) {
      podTemplateSpec = ((ReplicaSet) controller).getSpec().getTemplate();
    } else if (controller instanceof StatefulSet) {
      podTemplateSpec = ((StatefulSet) controller).getSpec().getTemplate();
    } else if (controller instanceof DeploymentConfig) {
      podTemplateSpec = ((DeploymentConfig) controller).getSpec().getTemplate();
    }
    return podTemplateSpec;
  }

  private NonNamespaceOperation<ReplicationController, ReplicationControllerList, DoneableReplicationController,
      RollableScalableResource<ReplicationController, DoneableReplicationController>>
  rcOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .replicationControllers()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<Deployment, DeploymentList, DoneableDeployment,
      ScalableResource<Deployment, DoneableDeployment>>
  deploymentOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .deployments()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<ReplicaSet, ReplicaSetList, DoneableReplicaSet,
      RollableScalableResource<ReplicaSet, DoneableReplicaSet>>
  replicaOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .replicaSets()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<DaemonSet, DaemonSetList, DoneableDaemonSet, Resource<DaemonSet, DoneableDaemonSet>>
  daemonOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .daemonSets()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<StatefulSet, StatefulSetList, DoneableStatefulSet,
      RollableScalableResource<StatefulSet, DoneableStatefulSet>>
  statefulOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).apps().statefulSets().inNamespace(namespace);
  }

  private NonNamespaceOperation<DeploymentConfig, DeploymentConfigList, DoneableDeploymentConfig,
      DeployableScalableResource<DeploymentConfig, DoneableDeploymentConfig>>
  deploymentConfigOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getOpenShiftClient(kubernetesConfig).deploymentConfigs().inNamespace(namespace);
  }

  @Override
  public Service createOrReplaceService(KubernetesConfig kubernetesConfig, Service definition) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @Override
  public Service getServiceFabric8(KubernetesConfig kubernetesConfig, String name) {
    return isNotBlank(name) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                  .services()
                                  .inNamespace(kubernetesConfig.getNamespace())
                                  .withName(name)
                                  .get()
                            : null;
  }

  @Override
  public V1Service getService(KubernetesConfig kubernetesConfig, String name, String namespace) {
    try {
      if (kubernetesConfig == null || isBlank(name)) {
        return null;
      }

      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      return new CoreV1Api(apiClient).readNamespacedService(name, namespace, null, null, null);
    } catch (ApiException exception) {
      if (isResourceNotFoundException(exception.getCode())) {
        return null;
      }
      String message =
          format("Unable to get service. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  private boolean isResourceNotFoundException(int code) {
    return code == 404;
  }

  @Override
  public V1Service getService(KubernetesConfig kubernetesConfig, String name) {
    return kubernetesConfig == null ? null : getService(kubernetesConfig, name, kubernetesConfig.getNamespace());
  }

  @Override
  public List<Service> getServices(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  @Override
  public void deleteService(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting service {}", name);
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public Ingress createOrReplaceIngress(KubernetesConfig kubernetesConfig, Ingress definition) {
    String name = definition.getMetadata().getName();
    Ingress ingress = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                          .extensions()
                          .ingresses()
                          .inNamespace(kubernetesConfig.getNamespace())
                          .withName(name)
                          .get();
    log.info("{} ingress [{}]", ingress == null ? "Creating" : "Replacing", name);
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .ingresses()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @Override
  public Ingress getIngress(KubernetesConfig kubernetesConfig, String name) {
    return isNotBlank(name) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                  .extensions()
                                  .ingresses()
                                  .inNamespace(kubernetesConfig.getNamespace())
                                  .withName(name)
                                  .get()
                            : null;
  }

  @Override
  public void deleteIngress(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting service {}", name);
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .ingresses()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public ConfigMap createOrReplaceConfigMapFabric8(KubernetesConfig kubernetesConfig, ConfigMap definition) {
    String name = definition.getMetadata().getName();
    ConfigMap configMap = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                              .configMaps()
                              .inNamespace(kubernetesConfig.getNamespace())
                              .withName(name)
                              .get();
    log.info("{} config map [{}]", configMap == null ? "Creating" : "Replacing", name);
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .configMaps()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @Override
  public V1ConfigMap createOrReplaceConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, name);
    return configMap == null ? createConfigMap(kubernetesConfig, definition)
                             : replaceConfigMap(kubernetesConfig, definition);
  }

  private V1ConfigMap replaceConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    log.info("Replacing config map [{}]", name);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).replaceNamespacedConfigMap(
          name, kubernetesConfig.getNamespace(), definition, null, null, null);
    } catch (ApiException exception) {
      String message = format(
          "Failed to replace ConfigMap. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  private V1ConfigMap createConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    log.info("Creating config map [{}]", name);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).createNamespacedConfigMap(
          kubernetesConfig.getNamespace(), definition, null, null, null);
    } catch (ApiException exception) {
      String message =
          format("Failed to create ConfigMap. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public ConfigMap getConfigMapFabric8(KubernetesConfig kubernetesConfig, String name) {
    try {
      return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
          .configMaps()
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public V1ConfigMap getConfigMap(KubernetesConfig kubernetesConfig, String name) {
    try {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      return new CoreV1Api(apiClient).readNamespacedConfigMap(name, kubernetesConfig.getNamespace(), null, null, null);
    } catch (ApiException exception) {
      if (isResourceNotFoundException(exception.getCode())) {
        return null;
      }
      String message =
          format("Failed to get ConfigMap. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public void deleteConfigMapFabric8(KubernetesConfig kubernetesConfig, String name) {
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .configMaps()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public void deleteConfigMap(KubernetesConfig kubernetesConfig, String name) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      new CoreV1Api(apiClient).deleteNamespacedConfigMap(
          name, kubernetesConfig.getNamespace(), null, null, null, null, null, null);
    } catch (ApiException exception) {
      String message =
          format("Failed to delete ConfigMap. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public IstioResource createOrReplaceIstioResource(KubernetesConfig kubernetesConfig, IstioResource definition) {
    String name = definition.getMetadata().getName();
    String kind = definition.getKind();
    log.info("Registering {} [{}]", kind, name);
    IstioClient istioClient = kubernetesHelperService.getIstioClient(kubernetesConfig);
    return istioClient.registerOrUpdateCustomResource(definition);
  }

  @Override
  public VirtualService getIstioVirtualService(KubernetesConfig kubernetesConfig, String name) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    try {
      VirtualService virtualService = new VirtualServiceBuilder().build();

      return kubernetesClient
          .customResources(getCustomResourceDefinition(kubernetesClient, virtualService), VirtualService.class,
              KubernetesResourceList.class, DoneableVirtualService.class)
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public DestinationRule getIstioDestinationRule(KubernetesConfig kubernetesConfig, String name) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    try {
      DestinationRule destinationRule = new DestinationRuleBuilder().build();

      return kubernetesClient
          .customResources(getCustomResourceDefinition(kubernetesClient, destinationRule), DestinationRule.class,
              KubernetesResourceList.class, DoneableDestinationRule.class)
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public CustomResourceDefinition getCustomResourceDefinition(KubernetesClient client, IstioResource resource) {
    final Optional<String> crdName = IstioSpecRegistry.getCRDNameFor(resource.getKind().toLowerCase());
    final CustomResourceDefinition customResourceDefinition =
        client.customResourceDefinitions().withName(crdName.get()).get();
    if (customResourceDefinition == null) {
      throw new IllegalArgumentException(
          format("Custom Resource Definition %s is not found in cluster %s", crdName, client.getMasterUrl()));
    }
    return customResourceDefinition;
  }

  @Override
  public void deleteIstioDestinationRule(KubernetesConfig kubernetesConfig, String name) {
    IstioClient istioClient = kubernetesHelperService.getIstioClient(kubernetesConfig);
    try {
      istioClient.unregisterCustomResource(new DestinationRuleBuilder()
                                               .withNewMetadata()
                                               .withName(name)
                                               .withNamespace(kubernetesConfig.getNamespace())
                                               .endMetadata()
                                               .build());
    } catch (Exception e) {
      log.info(e.getMessage());
    }
  }

  @Override
  public void deleteIstioVirtualService(KubernetesConfig kubernetesConfig, String name) {
    IstioClient istioClient = kubernetesHelperService.getIstioClient(kubernetesConfig);
    try {
      istioClient.unregisterCustomResource(new VirtualServiceBuilder()
                                               .withNewMetadata()
                                               .withName(name)
                                               .withNamespace(kubernetesConfig.getNamespace())
                                               .endMetadata()
                                               .build());
    } catch (Exception e) {
      log.info(e.getMessage());
    }
  }

  @Override
  public int getTrafficPercent(KubernetesConfig kubernetesConfig, String controllerName) {
    String serviceName = getServiceNameFromControllerName(controllerName);
    IstioResource virtualService = getIstioVirtualService(kubernetesConfig, serviceName);
    Optional<Integer> revision = getRevisionFromControllerName(controllerName);
    if (virtualService == null || !revision.isPresent()) {
      return 0;
    }
    VirtualServiceSpec virtualServiceSpec = ((VirtualService) virtualService).getSpec();
    if (isEmpty(virtualServiceSpec.getHttp()) || isEmpty(virtualServiceSpec.getHttp().get(0).getRoute())) {
      return 0;
    }

    return virtualServiceSpec.getHttp()
        .get(0)
        .getRoute()
        .stream()
        .filter(dw -> Integer.toString(revision.get()).equals(dw.getDestination().getSubset()))
        .map(DestinationWeight::getWeight)
        .findFirst()
        .orElse(0);
  }

  @Override
  public Map<String, Integer> getTrafficWeights(KubernetesConfig kubernetesConfig, String controllerName) {
    String serviceName = getServiceNameFromControllerName(controllerName);
    String controllerNamePrefix = getPrefixFromControllerName(controllerName);
    IstioResource virtualService = getIstioVirtualService(kubernetesConfig, serviceName);
    if (virtualService == null) {
      return new HashMap<>();
    }

    VirtualServiceSpec virtualServiceSpec = ((VirtualService) virtualService).getSpec();
    if (isEmpty(virtualServiceSpec.getHttp()) || isEmpty(virtualServiceSpec.getHttp().get(0).getRoute())) {
      return new HashMap<>();
    }
    List<DestinationWeight> destinationWeights = virtualServiceSpec.getHttp().get(0).getRoute();
    return destinationWeights.stream().collect(
        toMap(dw -> controllerNamePrefix + DASH + dw.getDestination().getSubset(), DestinationWeight::getWeight));
  }

  @Override
  public void createNamespaceIfNotExist(KubernetesConfig kubernetesConfig) {
    try {
      Namespace namespace = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                .namespaces()
                                .withName(kubernetesConfig.getNamespace())
                                .get();
      if (namespace == null) {
        log.info("Creating namespace [{}]", kubernetesConfig.getNamespace());
        kubernetesHelperService.getKubernetesClient(kubernetesConfig)
            .namespaces()
            .create(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(kubernetesConfig.getNamespace())
                        .endMetadata()
                        .build());
      }
    } catch (Exception e) {
      log.error("Couldn't get or create namespace {}", kubernetesConfig.getNamespace(), e);
    }
  }

  @Override
  public Secret getSecretFabric8(KubernetesConfig kubernetesConfig, String secretName) {
    return isNotBlank(secretName) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                        .secrets()
                                        .inNamespace(kubernetesConfig.getNamespace())
                                        .withName(secretName)
                                        .get()
                                  : null;
  }

  @Override
  public V1Secret getSecret(KubernetesConfig kubernetesConfig, String secretName) {
    if (isBlank(secretName)) {
      return null;
    }

    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).readNamespacedSecret(
          secretName, kubernetesConfig.getNamespace(), null, null, null);
    } catch (ApiException exception) {
      if (isResourceNotFoundException(exception.getCode())) {
        return null;
      }
      String message =
          format("Failed to get Secret. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public void deleteSecretFabric8(KubernetesConfig kubernetesConfig, String secretName) {
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .secrets()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(secretName)
        .delete();
  }

  @Override
  public void deleteSecret(KubernetesConfig kubernetesConfig, String secretName) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      new CoreV1Api(apiClient).deleteNamespacedSecret(
          secretName, kubernetesConfig.getNamespace(), null, null, null, null, null, null);
    } catch (ApiException exception) {
      String message =
          format("Failed to delete Secret. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public Secret createOrReplaceSecretFabric8(KubernetesConfig kubernetesConfig, Secret secret) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .secrets()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(secret);
  }

  @Override
  public V1Secret createOrReplaceSecret(KubernetesConfig kubernetesConfig, V1Secret definition) {
    String name = definition.getMetadata().getName();
    V1Secret secret = getSecret(kubernetesConfig, name);
    return secret == null ? createSecret(kubernetesConfig, definition) : replaceSecret(kubernetesConfig, definition);
  }

  @VisibleForTesting
  V1Secret createSecret(KubernetesConfig kubernetesConfig, V1Secret secret) {
    log.info("Creating secret [{}]", secret.getMetadata().getName());
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).createNamespacedSecret(kubernetesConfig.getNamespace(), secret, null, null, null);
    } catch (ApiException exception) {
      String message =
          format("Failed to create Secret. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @VisibleForTesting
  V1Secret replaceSecret(KubernetesConfig kubernetesConfig, V1Secret secret) {
    String name = secret.getMetadata().getName();
    log.info("Replacing secret [{}]", name);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).replaceNamespacedSecret(
          name, kubernetesConfig.getNamespace(), secret, null, null, null);
    } catch (ApiException exception) {
      String message =
          format("Failed to replace Secret. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public List<Pod> getPods(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .pods()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  private List<Pod> prunePodsInFinalState(List<Pod> pods) {
    return pods.stream()
        .filter(pod
            -> !StringUtils.equals(pod.getStatus().getPhase(), "Failed")
                && !StringUtils.equals(pod.getStatus().getPhase(), "Succeeded"))
        .collect(toList());
  }

  @Override
  public void waitForPodsToStop(KubernetesConfig kubernetesConfig, Map<String, String> labels,
      int serviceSteadyStateTimeout, List<Pod> originalPods, long startTime, LogCallback logCallback) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    List<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList());
    String namespace = kubernetesConfig.getNamespace();
    String waitingMsg = "Waiting for pods to stop...";
    log.info(waitingMsg);
    try {
      Callable<Boolean> callbable = () -> {
        Set<String> seenEvents = new HashSet<>();

        while (true) {
          logCallback.saveExecutionLog(waitingMsg);
          List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();

          showPodEvents(kubernetesClient, namespace, pods, originalPodNames, seenEvents, startTime, logCallback);

          pods = prunePodsInFinalState(pods);
          if (pods.size() <= 0) {
            return TRUE;
          }
          sleep(ofSeconds(5));
        }
      };
      timeLimiter.callWithTimeout(callbable, serviceSteadyStateTimeout, TimeUnit.MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out waiting for pods to stop";
      log.error(msg, e);
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Error while waiting for pods to stop");
    }
  }

  @SuppressWarnings({"squid:S00107", "squid:S3776"})
  private List<Pod> waitForPodsToBeRunning(KubernetesConfig kubernetesConfig, String controllerName, int previousCount,
      int desiredCount, int serviceSteadyStateTimeout, List<Pod> originalPods, boolean isNotVersioned, long startTime,
      String namespace, LogCallback executionLogCallback) {
    HasMetadata controller = getController(kubernetesConfig, controllerName, namespace);
    if (controller == null) {
      throw new InvalidArgumentsException(Pair.of(controllerName, "is null"));
    }
    PodTemplateSpec podTemplateSpec = getPodTemplateSpec(controller);
    if (podTemplateSpec == null) {
      throw new InvalidArgumentsException(Pair.of(controllerName + " pod spec", "is null"));
    }
    Set<String> images = getControllerImages(podTemplateSpec);
    Map<String, String> labels = podTemplateSpec.getMetadata().getLabels();
    List<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList());
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    log.info("Waiting for pods to be ready...");
    AtomicBoolean countReached = new AtomicBoolean(false);
    AtomicBoolean haveImagesCountReached = new AtomicBoolean(false);
    AtomicBoolean runningCountReached = new AtomicBoolean(false);
    AtomicBoolean steadyStateCountReached = new AtomicBoolean(false);

    try {
      int waitMinutes = serviceSteadyStateTimeout > 0 ? serviceSteadyStateTimeout : DEFAULT_STEADY_STATE_TIMEOUT;
      Callable<List<Pod>> callable = () -> {
        Set<String> seenEvents = new HashSet<>();

        while (true) {
          try {
            int absoluteDesiredCount = desiredCount;
            HasMetadata currentController = getController(kubernetesConfig, controllerName, namespace);
            if (currentController != null) {
              int controllerDesiredCount = getControllerPodCount(currentController);
              absoluteDesiredCount = (desiredCount == -1) ? controllerDesiredCount : desiredCount;
              if (controllerDesiredCount != absoluteDesiredCount) {
                String msg = format("Replica count is set to %d instead of %d. [Could be due to HPA.]",
                    controllerDesiredCount, absoluteDesiredCount);
                log.warn(msg);
                executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
              }
            } else {
              String msg = "Couldn't find controller " + controllerName;
              log.error(msg);
              executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
            }

            showControllerEvents(
                kubernetesClient, namespace, controllerName, seenEvents, startTime, executionLogCallback);

            List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();

            // Show pod events
            showPodEvents(
                kubernetesClient, namespace, pods, originalPodNames, seenEvents, startTime, executionLogCallback);

            pods = prunePodsInFinalState(pods);

            // Check current state
            if (pods.size() != absoluteDesiredCount) {
              executionLogCallback.saveExecutionLog(
                  format("Waiting for desired number of pods [%d/%d]", pods.size(), absoluteDesiredCount));
              sleep(ofSeconds(5));
              continue;
            }
            if (!countReached.getAndSet(true)) {
              executionLogCallback.saveExecutionLog(
                  format("Desired number of pods reached [%d/%d]", pods.size(), absoluteDesiredCount));
            }

            if (absoluteDesiredCount > 0) {
              int haveImages = (int) pods.stream().filter(pod -> podHasImages(pod, images)).count();
              if (haveImages != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(format("Waiting for pods to be updated with image %s [%d/%d]",
                                                          images, haveImages, absoluteDesiredCount),
                    LogLevel.INFO);
                sleep(ofSeconds(5));
                continue;
              }
              if (!haveImagesCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods are updated with image %s [%d/%d]", images, haveImages, absoluteDesiredCount));
              }
            }

            if (isNotVersioned || absoluteDesiredCount > previousCount) {
              int running = (int) pods.stream().filter(this::isRunning).count();
              if (running != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(
                    format("Waiting for pods to be running [%d/%d]", running, absoluteDesiredCount));
                sleep(ofSeconds(10));
                continue;
              }
              if (!runningCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods are running [%d/%d]", running, absoluteDesiredCount));
              }

              int steadyState = (int) pods.stream().filter(this::inSteadyState).count();
              if (steadyState != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(
                    format("Waiting for pods to reach steady state [%d/%d]", steadyState, absoluteDesiredCount));
                sleep(ofSeconds(15));
                continue;
              }
              if (!steadyStateCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods have reached steady state [%d/%d]", steadyState, absoluteDesiredCount));
              }
            }
            return pods;
          } catch (Exception e) {
            log.error("Exception in pod state wait loop.", e);
            executionLogCallback.saveExecutionLog("Error while waiting for pods to be ready", LogLevel.ERROR);
            Misc.logAllMessages(e, executionLogCallback);
            executionLogCallback.saveExecutionLog("Continuing to wait...", LogLevel.ERROR);
            sleep(ofSeconds(15));
          }
        }
      };
      return timeLimiter.callWithTimeout(callable, waitMinutes, TimeUnit.MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out waiting for pods to be ready";
      log.error(msg, e);
      executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e)
          .addParam("message", "Error while waiting for pods to be ready");
    }

    return kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();
  }

  private void showPodEvents(KubernetesClient kubernetesClient, String namespace, List<Pod> currentPods,
      List<String> originalPodNames, Set<String> seenEvents, long startTime, LogCallback executionLogCallback) {
    try {
      Set<String> podNames = new LinkedHashSet<>(originalPodNames);
      podNames.addAll(currentPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList()));

      List<Event> newEvents = kubernetesClient.events()
                                  .inNamespace(namespace)
                                  .list()
                                  .getItems()
                                  .stream()
                                  .filter(evt -> !seenEvents.contains(evt.getMetadata().getName()))
                                  .filter(evt -> podNames.contains(evt.getInvolvedObject().getName()))
                                  .filter(evt -> DateTime.parse(evt.getLastTimestamp()).getMillis() > startTime)
                                  .collect(toList());

      if (isNotEmpty(newEvents)) {
        executionLogCallback.saveExecutionLog("\n****  Kubernetes Pod Events  ****");
        podNames.forEach(podName -> {
          List<Event> podEvents =
              newEvents.stream().filter(evt -> evt.getInvolvedObject().getName().equals(podName)).collect(toList());
          if (isNotEmpty(podEvents)) {
            executionLogCallback.saveExecutionLog("  Pod: " + podName);
            podEvents.forEach(evt -> executionLogCallback.saveExecutionLog("   - " + evt.getMessage()));
          }
        });
        executionLogCallback.saveExecutionLog("");
        seenEvents.addAll(newEvents.stream().map(evt -> evt.getMetadata().getName()).collect(toList()));
      }
    } catch (Exception e) {
      Misc.logAllMessages(e, executionLogCallback);
      log.error("Failed to process kubernetes pod events", e);
    }
  }

  private void showControllerEvents(KubernetesClient kubernetesClient, String namespace, String controllerName,
      Set<String> seenEvents, long startTime, LogCallback executionLogCallback) {
    try {
      List<Event> newEvents = kubernetesClient.events()
                                  .inNamespace(namespace)
                                  .list()
                                  .getItems()
                                  .stream()
                                  .filter(evt -> !seenEvents.contains(evt.getMetadata().getName()))
                                  .filter(evt -> controllerName.equals(evt.getInvolvedObject().getName()))
                                  .filter(evt -> DateTime.parse(evt.getLastTimestamp()).getMillis() > startTime)
                                  .collect(toList());

      if (isNotEmpty(newEvents)) {
        executionLogCallback.saveExecutionLog("\n****  Kubernetes Controller Events  ****");
        executionLogCallback.saveExecutionLog("  Controller: " + controllerName);
        newEvents.forEach(evt -> executionLogCallback.saveExecutionLog("   - " + evt.getMessage()));
        executionLogCallback.saveExecutionLog("");
        seenEvents.addAll(newEvents.stream().map(evt -> evt.getMetadata().getName()).collect(toList()));
      }
    } catch (Exception e) {
      Misc.logAllMessages(e, executionLogCallback);
      log.error("Failed to process kubernetes controller events", e);
    }
  }

  @Override
  public List<Pod> getRunningPods(KubernetesConfig kubernetesConfig, String controllerName) {
    HasMetadata controller = getController(kubernetesConfig, controllerName);
    PodTemplateSpec podTemplateSpec = getPodTemplateSpec(controller);
    if (podTemplateSpec == null) {
      return emptyList();
    }
    Map<String, String> labels = podTemplateSpec.getMetadata().getLabels();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .pods()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  private Set<String> getControllerImages(PodTemplateSpec template) {
    return template.getSpec().getContainers().stream().map(Container::getImage).collect(toSet());
  }

  public void checkStatus(KubernetesConfig kubernetesConfig, String rcName, String serviceName) {
    KubernetesClient client = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    String masterUrl = client.getMasterUrl().toString();
    ReplicationController rc =
        client.replicationControllers().inNamespace(kubernetesConfig.getNamespace()).withName(rcName).get();
    if (rc != null) {
      String rcLink = masterUrl + rc.getMetadata().getSelfLink().substring(1);
      log.info("Controller {}: {}", rcName, rcLink);
    } else {
      log.info("Controller {} does not exist", rcName);
    }
    Service service = client.services().inNamespace(kubernetesConfig.getNamespace()).withName(serviceName).get();
    if (service != null) {
      String serviceLink = masterUrl + service.getMetadata().getSelfLink().substring(1);
      log.info("Service: {}, link: {}", serviceName, serviceLink);
    } else {
      log.info("Service {} does not exist", serviceName);
    }
  }

  @Override
  public String fetchReleaseHistoryFromConfigMap(KubernetesConfig kubernetesConfig, String releaseName) {
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, releaseName);
    if (configMap != null && configMap.getData() != null && configMap.getData().containsKey(ReleaseHistoryKeyName)) {
      return configMap.getData().get(ReleaseHistoryKeyName);
    }

    return EMPTY;
  }

  @Override
  public String fetchReleaseHistoryFromSecrets(KubernetesConfig kubernetesConfig, String releaseName) {
    V1Secret secret = getSecret(kubernetesConfig, releaseName);
    if (secret != null && secret.getData() != null && secret.getData().containsKey(ReleaseHistoryKeyName)) {
      return new String(secret.getData().get(ReleaseHistoryKeyName), Charsets.UTF_8);
    }

    return EMPTY;
  }

  @Override
  public V1ConfigMap saveReleaseHistoryInConfigMap(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory) {
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, releaseName);
    if (configMap == null) {
      configMap = new V1ConfigMapBuilder()
                      .withMetadata(new V1ObjectMetaBuilder()
                                        .withName(releaseName)
                                        .withNamespace(kubernetesConfig.getNamespace())
                                        .build())
                      .withData(ImmutableMap.of(ReleaseHistoryKeyName, releaseHistory))
                      .build();
    } else {
      Map data = configMap.getData();
      data.put(ReleaseHistoryKeyName, releaseHistory);
      configMap.setData(data);
    }

    return createOrReplaceConfigMap(kubernetesConfig, configMap);
  }

  @Override
  public void saveReleaseHistory(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory, boolean storeInSecrets) {
    if (storeInSecrets) {
      saveReleaseHistoryInSecrets(kubernetesConfig, releaseName, releaseHistory);
    } else {
      saveReleaseHistoryInConfigMap(kubernetesConfig, releaseName, releaseHistory);
    }
  }

  @Override
  public V1Secret saveReleaseHistoryInSecrets(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory) {
    V1Secret secret = getSecret(kubernetesConfig, releaseName);
    if (secret == null) {
      secret = new V1SecretBuilder()
                   .withMetadata(new V1ObjectMetaBuilder()
                                     .withNamespace(kubernetesConfig.getNamespace())
                                     .withName(releaseName)
                                     .build())
                   .withData(ImmutableMap.of(ReleaseHistoryKeyName, releaseHistory.getBytes(Charsets.UTF_8)))
                   .build();
    } else {
      Map data = secret.getData();
      data.put(ReleaseHistoryKeyName, releaseHistory.getBytes(Charsets.UTF_8));
      secret.setData(data);
    }

    return createOrReplaceSecret(kubernetesConfig, secret);
  }

  @Override
  public List<Pod> getRunningPodsWithLabelsFabric8(
      KubernetesConfig kubernetesConfig, String namespace, Map<String, String> labels) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .pods()
        .inNamespace(namespace)
        .withLabels(labels)
        .list()
        .getItems()
        .stream()
        .filter(pod
            -> StringUtils.isBlank(pod.getMetadata().getDeletionTimestamp())
                && StringUtils.equals(pod.getStatus().getPhase(), RUNNING))
        .collect(Collectors.toList());
  }

  @Override
  public List<V1Pod> getRunningPodsWithLabels(
      KubernetesConfig kubernetesConfig, String namespace, Map<String, String> labels) {
    try {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      String labelSelector = labels.entrySet()
                                 .stream()
                                 .map(entry -> format(K8S_SELECTOR_FORMAT, entry.getKey(), entry.getValue()))
                                 .collect(Collectors.joining(K8S_SELECTOR_DELIMITER));
      V1PodList podList = new CoreV1Api(apiClient).listNamespacedPod(
          namespace, null, null, null, null, labelSelector, null, null, null, null);
      return podList.getItems()
          .stream()
          .filter(pod
              -> pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() == null
                  && pod.getStatus() != null && StringUtils.equals(pod.getStatus().getPhase(), RUNNING))
          .collect(Collectors.toList());
    } catch (ApiException exception) {
      String message =
          format("Unable to get running pods. Code: %s, message: %s", exception.getCode(), exception.getResponseBody());
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public VersionInfo getVersion(KubernetesConfig kubernetesConfig) {
    try {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      return new VersionApi(apiClient).getCode();
    } catch (ApiException exception) {
      String message =
          format("Unable to retrieve k8s version. Code: %s, message: %s", exception.getCode(), exception.getMessage());
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public String getVersionAsString(KubernetesConfig kubernetesConfig) {
    VersionInfo versionInfo = getVersion(kubernetesConfig);
    return format("%s.%s", versionInfo.getMajor(), versionInfo.getMinor());
  }

  @Override
  public String getConfigFileContent(KubernetesConfig config) {
    encodeCharsIfNeeded(config);

    if (isBlank(config.getMasterUrl())) {
      return "";
    }

    if (KubernetesClusterAuthType.OIDC == config.getAuthType()) {
      OidcTokenRequestData oidcTokenRequestData = oidcTokenRetriever.createOidcTokenRequestData(config);
      return generateKubeConfigStringForOpenID(config, oidcTokenRequestData);
    }

    String insecureSkipTlsVerify = isEmpty(config.getCaCert()) ? "insecure-skip-tls-verify: true" : "";
    String certificateAuthorityData =
        isNotEmpty(config.getCaCert()) ? "certificate-authority-data: " + new String(config.getCaCert()) : "";
    String clientCertData =
        isNotEmpty(config.getClientCert()) ? "client-certificate-data: " + new String(config.getClientCert()) : "";
    String clientKeyData =
        isNotEmpty(config.getClientKey()) ? "client-key-data: " + new String(config.getClientKey()) : "";
    String password = isNotEmpty(config.getPassword()) ? "password: " + new String(config.getPassword()) : "";
    String username = isNotEmpty(config.getUsername()) ? "username: " + new String(config.getUsername()) : "";
    String namespace = isNotEmpty(config.getNamespace()) ? "namespace: " + config.getNamespace() : "";
    String serviceAccountTokenData =
        isNotEmpty(config.getServiceAccountToken()) ? "token: " + new String(config.getServiceAccountToken()) : "";

    return KUBE_CONFIG_TEMPLATE.replace("${MASTER_URL}", config.getMasterUrl())
        .replace("${INSECURE_SKIP_TLS_VERIFY}", insecureSkipTlsVerify)
        .replace("${CERTIFICATE_AUTHORITY_DATA}", certificateAuthorityData)
        .replace("${NAMESPACE}", namespace)
        .replace("${USER_NAME}", username)
        .replace("${CLIENT_CERT_DATA}", clientCertData)
        .replace("${CLIENT_KEY_DATA}", clientKeyData)
        .replace("${PASSWORD}", password)
        .replace("${SERVICE_ACCOUNT_TOKEN_DATA}", serviceAccountTokenData);
  }

  private void encodeCharsIfNeeded(KubernetesConfig config) {
    config.setCaCert(getEncodedChars(config.getCaCert()));
    config.setClientCert(getEncodedChars(config.getClientCert()));
    config.setClientKey(getEncodedChars(config.getClientKey()));
  }

  private char[] getEncodedChars(char[] chars) {
    if (isEmpty(chars) || !(new String(chars).startsWith("-----BEGIN "))) {
      return chars;
    }
    return encodeBase64(chars).toCharArray();
  }

  @VisibleForTesting
  String generateKubeConfigStringForOpenID(KubernetesConfig config, OidcTokenRequestData oidcTokenRequestData) {
    OpenIdOAuth2AccessToken openIdOAuth2AccessToken =
        oidcTokenRetriever.retrieveOpenIdAccessToken(oidcTokenRequestData);

    String clientIdData =
        isNotEmpty(oidcTokenRequestData.getClientId()) ? CLIENT_ID_KEY + oidcTokenRequestData.getClientId() : EMPTY;
    String clientSecretData = isNotEmpty(oidcTokenRequestData.getClientSecret())
        ? CLIENT_SECRET_KEY + oidcTokenRequestData.getClientSecret()
        : EMPTY;
    String idToken = isNotEmpty(openIdOAuth2AccessToken.getOpenIdToken())
        ? ID_TOKEN_KEY + openIdOAuth2AccessToken.getOpenIdToken()
        : EMPTY;
    String providerUrl = isNotEmpty(oidcTokenRequestData.getProviderUrl())
        ? ISSUER_URL_KEY + oidcTokenRequestData.getProviderUrl()
        : EMPTY;
    String refreshToken = isNotEmpty(openIdOAuth2AccessToken.getRefreshToken())
        ? REFRESH_TOKEN + openIdOAuth2AccessToken.getRefreshToken()
        : EMPTY;
    String authConfigName = NAME + OIDC_AUTH_NAME_VAL;
    String namespace = isNotEmpty(config.getNamespace()) ? NAMESPACE_KEY + config.getNamespace() : EMPTY;

    return KUBE_CONFIG_OIDC_TEMPLATE.replace(MASTER_URL, config.getMasterUrl())
        .replace(NAMESPACE, namespace)
        .replace(OIDC_CLIENT_ID, clientIdData)
        .replace(OIDC_CLIENT_SECRET, clientSecretData)
        .replace(OIDC_ID_TOKEN, idToken)
        .replace(OIDC_ISSUER_URL, providerUrl)
        .replace(OIDC_RERESH_TOKEN, refreshToken)
        .replace(OIDC_AUTH_NAME, authConfigName);
  }
}
