package io.harness.delegate.task.k8s;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.getFilesUnderPath;
import static io.harness.helm.HelmConstants.HELM_RELEASE_LABEL;
import static io.harness.k8s.K8sConstants.KUBERNETES_CHANGE_CAUSE_ANNOTATION;
import static io.harness.k8s.K8sConstants.SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT;
import static io.harness.k8s.KubernetesConvention.ReleaseHistoryKeyName;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.k8s.kubectl.Utils.parseLatestRevisionNumberFromRolloutHistory;
import static io.harness.k8s.manifest.ManifestHelper.getFirstLoadBalancerService;
import static io.harness.k8s.manifest.ManifestHelper.validateValuesFileContents;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.k8s.manifest.ManifestHelper.yaml_file_extension;
import static io.harness.k8s.manifest.ManifestHelper.yml_file_extension;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.k8s.model.Release.Status.Failed;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.FileData;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGLogCallback;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.expression.DelegateExpressionEvaluator;
import io.harness.delegate.git.NGGitService;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GitOperationException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesValuesException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.kubectl.AbstractExecutable;
import io.harness.k8s.kubectl.ApplyCommand;
import io.harness.k8s.kubectl.DeleteCommand;
import io.harness.k8s.kubectl.DescribeCommand;
import io.harness.k8s.kubectl.GetCommand;
import io.harness.k8s.kubectl.GetJobCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutHistoryCommand;
import io.harness.k8s.kubectl.RolloutStatusCommand;
import io.harness.k8s.kubectl.ScaleCommand;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceComparer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import me.snowdrop.istio.api.networking.v1alpha3.Destination;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationRule;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationRuleBuilder;
import me.snowdrop.istio.api.networking.v1alpha3.DestinationWeight;
import me.snowdrop.istio.api.networking.v1alpha3.DoneableDestinationRule;
import me.snowdrop.istio.api.networking.v1alpha3.DoneableVirtualService;
import me.snowdrop.istio.api.networking.v1alpha3.HTTPRoute;
import me.snowdrop.istio.api.networking.v1alpha3.PortSelector;
import me.snowdrop.istio.api.networking.v1alpha3.Subset;
import me.snowdrop.istio.api.networking.v1alpha3.TCPRoute;
import me.snowdrop.istio.api.networking.v1alpha3.TLSRoute;
import me.snowdrop.istio.api.networking.v1alpha3.VirtualService;
import me.snowdrop.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
public class K8sTaskHelperBase {
  public static final Set<String> openshiftResources = ImmutableSet.of("Route");
  @Inject private TimeLimiter timeLimiter;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private KubernetesHelperService kubernetesHelperService;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Inject private NGErrorHelper ngErrorHelper;

  private DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator();

  public static final String ISTIO_DESTINATION_TEMPLATE = "host: $ISTIO_DESTINATION_HOST_NAME\n"
      + "subset: $ISTIO_DESTINATION_SUBSET_NAME";

  public static LogOutputStream getExecutionLogOutputStream(LogCallback executionLogCallback, LogLevel logLevel) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(line, logLevel);
      }
    };
  }

  public static String getResourcesInStringFormat(List<KubernetesResourceId> resourceIds) {
    StringBuilder sb = new StringBuilder(1024);
    resourceIds.forEach(resourceId -> sb.append("\n- ").append(resourceId.namespaceKindNameRef()));
    return sb.toString();
  }

  public static long getTimeoutMillisFromMinutes(Integer timeoutMinutes) {
    if (timeoutMinutes == null || timeoutMinutes <= 0) {
      timeoutMinutes = DEFAULT_STEADY_STATE_TIMEOUT;
    }

    return ofMinutes(timeoutMinutes).toMillis();
  }

  public static LogOutputStream getEmptyLogOutputStream() {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {}
    };
  }

  public static ProcessResult executeCommandSilent(AbstractExecutable command, String workingDirectory)
      throws Exception {
    try (LogOutputStream emptyLogOutputStream = getEmptyLogOutputStream()) {
      return command.execute(workingDirectory, emptyLogOutputStream, emptyLogOutputStream, false);
    }
  }

  public static ProcessResult executeCommand(
      AbstractExecutable command, String workingDirectory, LogCallback executionLogCallback) throws Exception {
    try (LogOutputStream logOutputStream = getExecutionLogOutputStream(executionLogCallback, INFO);
         LogOutputStream logErrorStream = getExecutionLogOutputStream(executionLogCallback, ERROR)) {
      return command.execute(workingDirectory, logOutputStream, logErrorStream, true);
    }
  }

  public static String getOcCommandPrefix(String ocPath, String kubeConfigPath) {
    StringBuilder command = new StringBuilder(128);

    if (StringUtils.isNotBlank(ocPath)) {
      command.append(encloseWithQuotesIfNeeded(ocPath));
    } else {
      command.append("oc");
    }

    if (StringUtils.isNotBlank(kubeConfigPath)) {
      command.append(" --kubeconfig=").append(encloseWithQuotesIfNeeded(kubeConfigPath));
    }

    return command.toString();
  }

  public static String getOcCommandPrefix(K8sDelegateTaskParams k8sDelegateTaskParams) {
    return getOcCommandPrefix(k8sDelegateTaskParams.getOcPath(), k8sDelegateTaskParams.getKubeconfigPath());
  }

  @VisibleForTesting
  public static String getRelativePath(String filePath, String prefixPath) {
    Path fileAbsolutePath = Paths.get(filePath).toAbsolutePath();
    Path prefixAbsolutePath = Paths.get(prefixPath).toAbsolutePath();
    return prefixAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  public static boolean isValidManifestFile(String filename) {
    return (StringUtils.endsWith(filename, yaml_file_extension) || StringUtils.endsWith(filename, yml_file_extension))
        && !StringUtils.equals(filename, values_filename);
  }

  public List<K8sPod> getPodDetailsWithLabels(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      Map<String, String> labels, long timeoutinMillis) throws Exception {
    return timeLimiter.callWithTimeout(
        ()
            -> kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, namespace, labels)
                   .stream()
                   .filter(pod
                       -> pod.getMetadata() != null && pod.getStatus() != null
                           && pod.getStatus().getContainerStatuses() != null)
                   .map(pod -> {
                     V1ObjectMeta metadata = pod.getMetadata();
                     return K8sPod.builder()
                         .uid(metadata.getUid())
                         .name(metadata.getName())
                         .podIP(pod.getStatus().getPodIP())
                         .namespace(metadata.getNamespace())
                         .releaseName(releaseName)
                         .containerList(pod.getStatus()
                                            .getContainerStatuses()
                                            .stream()
                                            .map(container
                                                -> K8sContainer.builder()
                                                       .containerId(container.getContainerID())
                                                       .name(container.getName())
                                                       .image(container.getImage())
                                                       .build())
                                            .collect(toList()))
                         // Need to ensure that we're storing labels as registered by kryo map implementation
                         .labels(metadata.getLabels() != null ? new HashMap<>(metadata.getLabels()) : null)
                         .build();
                   })
                   .collect(toList()),
        timeoutinMillis, TimeUnit.MILLISECONDS, true);
  }

  public List<K8sPod> getPodDetailsWithTrack(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      String track, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.track, track);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  public List<K8sPod> getPodDetailsWithColor(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      String color, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.color, color);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  private V1Service waitForLoadBalancerService(
      KubernetesConfig kubernetesConfig, String serviceName, String namespace, int timeoutInSeconds) {
    return waitForLoadBalancerService(serviceName, () -> {
      V1Service service = kubernetesContainerService.getService(kubernetesConfig, serviceName, namespace);
      if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null) {
        V1LoadBalancerStatus loadBalancerStatus = service.getStatus().getLoadBalancer();
        if (isNotEmpty(loadBalancerStatus.getIngress())) {
          return service;
        }
      }

      return null;
    }, timeoutInSeconds);
  }

  private <T> T waitForLoadBalancerService(String name, Callable<T> getLoadBalancerService, int timeoutInSeconds) {
    try {
      return timeLimiter.callWithTimeout(() -> {
        while (true) {
          T result = getLoadBalancerService.call();
          if (result != null) {
            return result;
          }

          int sleepTimeInSeconds = 5;
          log.info("waitForLoadBalancerService: LoadBalancer Service {} not ready. Sleeping for {} seconds", name,
              sleepTimeInSeconds);
          sleep(ofSeconds(sleepTimeInSeconds));
        }
      }, timeoutInSeconds, TimeUnit.SECONDS, true);
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out waiting for LoadBalancer service. Moving on.", e);
    } catch (Exception e) {
      log.error("Exception while trying to get LoadBalancer service", e);
    }

    return null;
  }

  private String getLoadBalancerEndpoint(String loadBalancerHost, Iterator<Integer> ports) {
    boolean port80Found = false;
    boolean port443Found = false;
    Integer firstPort = null;

    while (ports.hasNext()) {
      firstPort = ports.next();

      if (firstPort == 80) {
        port80Found = true;
      }
      if (firstPort == 443) {
        port443Found = true;
      }
    }

    if (port443Found) {
      return "https://" + loadBalancerHost + "/";
    } else if (port80Found) {
      return "http://" + loadBalancerHost + "/";
    } else if (firstPort != null) {
      return loadBalancerHost + ":" + firstPort;
    } else {
      return loadBalancerHost;
    }
  }

  public String getLoadBalancerEndpoint(KubernetesConfig kubernetesConfig, List<KubernetesResource> resources) {
    KubernetesResource loadBalancerResource = getFirstLoadBalancerService(resources);
    if (loadBalancerResource == null) {
      return null;
    }

    // NOTE(hindwani): We are not using timeOutInMillis for waiting because of the bug: CDP-13872
    V1Service service = waitForLoadBalancerService(kubernetesConfig, loadBalancerResource.getResourceId().getName(),
        loadBalancerResource.getResourceId().getNamespace(), 60);

    if (service == null) {
      log.warn("Could not get the Service Status {} from cluster.", loadBalancerResource.getResourceId().getName());
      return null;
    }

    if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null
        || service.getStatus().getLoadBalancer().getIngress() == null) {
      return null;
    }

    V1LoadBalancerIngress loadBalancerIngress = service.getStatus().getLoadBalancer().getIngress().get(0);
    String loadBalancerHost =
        isNotBlank(loadBalancerIngress.getHostname()) ? loadBalancerIngress.getHostname() : loadBalancerIngress.getIp();
    if (service.getSpec() == null || service.getSpec().getPorts() == null) {
      return loadBalancerHost;
    }

    return getLoadBalancerEndpoint(
        loadBalancerHost, service.getSpec().getPorts().stream().map(V1ServicePort::getPort).iterator());
  }

  public void setNamespaceToKubernetesResourcesIfRequired(
      List<KubernetesResource> kubernetesResources, String namespace) {
    if (isEmpty(kubernetesResources)) {
      return;
    }

    for (KubernetesResource kubernetesResource : kubernetesResources) {
      if (isBlank(kubernetesResource.getResourceId().getNamespace())) {
        kubernetesResource.getResourceId().setNamespace(namespace);
      }
    }
  }

  public List<K8sPod> getPodDetails(
      KubernetesConfig kubernetesConfig, String namespace, String releaseName, long timeoutInMillis) throws Exception {
    if (isEmpty(releaseName)) {
      return Collections.emptyList();
    }
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  /**
   * This method arranges resources to be deleted in the reverse order of their creation.
   * To see order of create, please refer to KubernetesResourceComparer.kindOrder
   * @param resourceIdsToDelete
   */
  public List<KubernetesResourceId> arrangeResourceIdsInDeletionOrder(List<KubernetesResourceId> resourceIdsToDelete) {
    List<KubernetesResource> kubernetesResources =
        resourceIdsToDelete.stream()
            .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
            .collect(Collectors.toList());
    kubernetesResources =
        kubernetesResources.stream().sorted(new KubernetesResourceComparer().reversed()).collect(Collectors.toList());
    return kubernetesResources.stream()
        .map(kubernetesResource -> kubernetesResource.getResourceId())
        .collect(Collectors.toList());
  }

  public Integer getTargetInstancesForCanary(
      Integer percentInstancesInDelegateRequest, Integer maxInstances, LogCallback logCallback) {
    Integer targetInstances = (int) Math.round(percentInstancesInDelegateRequest * maxInstances / 100.0);
    if (targetInstances < 1) {
      logCallback.saveExecutionLog("\nTarget instances computed to be less than 1. Bumped up to 1");
      targetInstances = 1;
    }
    return targetInstances;
  }

  public List<Subset> generateSubsetsForDestinationRule(List<String> subsetNames) {
    List<Subset> subsets = new ArrayList<>();

    for (String subsetName : subsetNames) {
      Subset subset = new Subset();
      subset.setName(subsetName);

      if (subsetName.equals(HarnessLabelValues.trackCanary)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackCanary);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.trackStable)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackStable);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorBlue)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorBlue);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorGreen)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorGreen);
        subset.setLabels(labels);
      }

      subsets.add(subset);
    }

    return subsets;
  }

  private String generateDestination(String host, String subset) {
    return ISTIO_DESTINATION_TEMPLATE.replace("$ISTIO_DESTINATION_HOST_NAME", host)
        .replace("$ISTIO_DESTINATION_SUBSET_NAME", subset);
  }

  private String getDestinationYaml(String destination, String host) {
    if (canaryDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackCanary);
    } else if (stableDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackStable);
    } else {
      return destination;
    }
  }

  private List<DestinationWeight> generateDestinationWeights(
      List<IstioDestinationWeight> istioDestinationWeights, String host, PortSelector portSelector) throws IOException {
    List<DestinationWeight> destinationWeights = new ArrayList<>();

    for (IstioDestinationWeight istioDestinationWeight : istioDestinationWeights) {
      String destinationYaml = getDestinationYaml(istioDestinationWeight.getDestination(), host);
      Destination destination = new YamlUtils().read(destinationYaml, Destination.class);
      destination.setPort(portSelector);

      DestinationWeight destinationWeight = new DestinationWeight();
      destinationWeight.setWeight(Integer.parseInt(istioDestinationWeight.getWeight()));
      destinationWeight.setDestination(destination);

      destinationWeights.add(destinationWeight);
    }

    return destinationWeights;
  }

  private String getHostFromRoute(List<DestinationWeight> routes) {
    if (isEmpty(routes)) {
      throw new InvalidRequestException("No routes exist in VirtualService", USER);
    }

    if (null == routes.get(0).getDestination()) {
      throw new InvalidRequestException("No destination exist in VirtualService", USER);
    }

    if (isBlank(routes.get(0).getDestination().getHost())) {
      throw new InvalidRequestException("No host exist in VirtualService", USER);
    }

    return routes.get(0).getDestination().getHost();
  }

  private PortSelector getPortSelectorFromRoute(List<DestinationWeight> routes) {
    return routes.get(0).getDestination().getPort();
  }

  private void validateRoutesInVirtualService(VirtualService virtualService) {
    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    List<TCPRoute> tcp = virtualService.getSpec().getTcp();
    List<TLSRoute> tls = virtualService.getSpec().getTls();

    if (isEmpty(http)) {
      throw new InvalidRequestException(
          "Http route is not present in VirtualService. Only Http routes are allowed", USER);
    }

    if (isNotEmpty(tcp) || isNotEmpty(tls)) {
      throw new InvalidRequestException("Only Http routes are allowed in VirtualService for Traffic split", USER);
    }

    if (http.size() > 1) {
      throw new InvalidRequestException("Only one route is allowed in VirtualService", USER);
    }
  }

  public void updateVirtualServiceWithDestinationWeights(List<IstioDestinationWeight> istioDestinationWeights,
      VirtualService virtualService, LogCallback executionLogCallback) throws IOException {
    validateRoutesInVirtualService(virtualService);

    executionLogCallback.saveExecutionLog("\nUpdating VirtualService with destination weights");

    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    if (isNotEmpty(http)) {
      String host = getHostFromRoute(http.get(0).getRoute());
      PortSelector portSelector = getPortSelectorFromRoute(http.get(0).getRoute());
      http.get(0).setRoute(generateDestinationWeights(istioDestinationWeights, host, portSelector));
    }
  }

  private VirtualService updateVirtualServiceManifestFilesWithRoutes(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, List<IstioDestinationWeight> istioDestinationWeights,
      LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> virtualServiceResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.VirtualService.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(virtualServiceResources)) {
      return null;
    }

    if (virtualServiceResources.size() > 1) {
      String msg = "\nMore than one VirtualService found. Only one VirtualService can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    kubernetesClient.customResources(
        kubernetesContainerService.getCustomResourceDefinition(kubernetesClient, new VirtualServiceBuilder().build()),
        VirtualService.class, KubernetesResourceList.class, DoneableVirtualService.class);

    KubernetesResource kubernetesResource = virtualServiceResources.get(0);
    InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
    VirtualService virtualService = (VirtualService) kubernetesClient.load(inputStream).get().get(0);
    updateVirtualServiceWithDestinationWeights(istioDestinationWeights, virtualService, executionLogCallback);

    kubernetesResource.setSpec(KubernetesHelper.toYaml(virtualService));

    return virtualService;
  }

  public VirtualService updateVirtualServiceManifestFilesWithRoutesForCanary(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<IstioDestinationWeight> istioDestinationWeights = new ArrayList<>();
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("100").build());
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("0").build());

    return updateVirtualServiceManifestFilesWithRoutes(
        resources, kubernetesConfig, istioDestinationWeights, executionLogCallback);
  }

  public DestinationRule updateDestinationRuleManifestFilesWithSubsets(List<KubernetesResource> resources,
      List<String> subsets, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> destinationRuleResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.DestinationRule.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(destinationRuleResources)) {
      return null;
    }

    if (destinationRuleResources.size() > 1) {
      String msg = "More than one DestinationRule found. Only one DestinationRule can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    kubernetesClient.customResources(
        kubernetesContainerService.getCustomResourceDefinition(kubernetesClient, new DestinationRuleBuilder().build()),
        DestinationRule.class, KubernetesResourceList.class, DoneableDestinationRule.class);

    KubernetesResource kubernetesResource = destinationRuleResources.get(0);
    InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
    DestinationRule destinationRule = (DestinationRule) kubernetesClient.load(inputStream).get().get(0);
    destinationRule.getSpec().setSubsets(generateSubsetsForDestinationRule(subsets));

    kubernetesResource.setSpec(KubernetesHelper.toYaml(destinationRule));

    return destinationRule;
  }

  private String getPodContainerId(K8sPod pod) {
    return isEmpty(pod.getContainerList()) ? EMPTY : pod.getContainerList().get(0).getContainerId();
  }

  private List<K8sPod> getHelmPodDetails(
      KubernetesConfig kubernetesConfig, String namespace, String releaseName, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HELM_RELEASE_LABEL, releaseName);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  public List<ContainerInfo> getContainerInfos(
      KubernetesConfig kubernetesConfig, String releaseName, String namespace, long timeoutInMillis) throws Exception {
    List<K8sPod> helmPods = getHelmPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis);

    return helmPods.stream()
        .map(pod
            -> ContainerInfo.builder()
                   .hostName(pod.getName())
                   .ip(pod.getPodIP())
                   .containerId(getPodContainerId(pod))
                   .podName(pod.getName())
                   .newContainer(true)
                   .status(ContainerInfo.Status.SUCCESS)
                   .releaseName(releaseName)
                   .build())
        .collect(Collectors.toList());
  }

  public Kubectl getOverriddenClient(
      Kubectl client, List<KubernetesResource> resources, K8sDelegateTaskParams k8sDelegateTaskParams) {
    List<KubernetesResource> openshiftResourcesList =
        resources.stream()
            .filter(kubernetesResource -> openshiftResources.contains(kubernetesResource.getResourceId().getKind()))
            .collect(Collectors.toList());
    if (isEmpty(openshiftResourcesList)) {
      return client;
    }

    return Kubectl.client(k8sDelegateTaskParams.getOcPath(), k8sDelegateTaskParams.getKubeconfigPath());
  }

  @VisibleForTesting
  public ProcessResult runK8sExecutable(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      AbstractExecutable executable) throws Exception {
    return executeCommand(executable, k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
  }

  public boolean applyManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess)
      throws Exception {
    FileIo.writeUtf8StringToFile(
        k8sDelegateTaskParams.getWorkingDirectory() + "/manifests.yaml", ManifestHelper.toYaml(resources));

    Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

    // We want to set `kubernetes.io/change-cause` annotation only if no any custom value already defined
    boolean recordCommand =
        resources.stream()
            .map(resource -> resource.getMetadataAnnotationValue(KUBERNETES_CHANGE_CAUSE_ANNOTATION))
            .noneMatch(Objects::nonNull);

    final ApplyCommand applyCommand = overriddenClient.apply().filename("manifests.yaml").record(recordCommand);
    ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, applyCommand);
    if (result.getExitValue() != 0) {
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    if (denoteOverallSuccess) {
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    }

    return true;
  }

  public boolean deleteManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    FileIo.writeUtf8StringToFile(
        k8sDelegateTaskParams.getWorkingDirectory() + "/manifests.yaml", ManifestHelper.toYaml(resources));

    Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

    final DeleteCommand deleteCommand = overriddenClient.delete().filename("manifests.yaml");
    ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand);
    if (result.getExitValue() != 0) {
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  @VisibleForTesting
  public StartedProcess getEventWatchProcess(String workingDirectory, GetCommand getEventsCommand,
      LogOutputStream watchInfoStream, LogOutputStream watchErrorStream) throws Exception {
    return getEventsCommand.executeInBackground(workingDirectory, watchInfoStream, watchErrorStream);
  }

  @VisibleForTesting
  public ProcessResult executeCommandUsingUtils(String workingDirectory, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, String command) throws Exception {
    return Utils.executeScript(workingDirectory, command, statusInfoStream, statusErrorStream);
  }

  public boolean scale(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId resourceId,
      int targetReplicaCount, LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("\nScaling " + resourceId.kindNameRef());

    final ScaleCommand scaleCommand = client.scale()
                                          .resource(resourceId.kindNameRef())
                                          .replicas(targetReplicaCount)
                                          .namespace(resourceId.getNamespace());
    ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, scaleCommand);
    if (result.getExitValue() == 0) {
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    } else {
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      log.warn("Failed to scale workload. Error {}", result.getOutput());
      return false;
    }
  }

  public void cleanup(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, ReleaseHistory releaseHistory,
      LogCallback executionLogCallback) throws Exception {
    final int lastSuccessfulReleaseNumber =
        (releaseHistory.getLastSuccessfulRelease() != null) ? releaseHistory.getLastSuccessfulRelease().getNumber() : 0;

    if (lastSuccessfulReleaseNumber == 0) {
      executionLogCallback.saveExecutionLog("\nNo previous successful release found.");
    } else {
      executionLogCallback.saveExecutionLog("\nPrevious Successful Release is " + lastSuccessfulReleaseNumber);
    }

    executionLogCallback.saveExecutionLog("\nCleaning up older and failed releases");

    for (int releaseIndex = releaseHistory.getReleases().size() - 1; releaseIndex >= 0; releaseIndex--) {
      Release release = releaseHistory.getReleases().get(releaseIndex);
      if (release.getNumber() < lastSuccessfulReleaseNumber || release.getStatus() == Failed) {
        for (int resourceIndex = release.getResources().size() - 1; resourceIndex >= 0; resourceIndex--) {
          KubernetesResourceId resourceId = release.getResources().get(resourceIndex);
          if (resourceId.isVersioned()) {
            DeleteCommand deleteCommand =
                client.delete().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
            ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand);
            if (result.getExitValue() != 0) {
              log.warn("Failed to delete resource {}. Error {}", resourceId.kindNameRef(), result.getOutput());
            }
          }
        }
      }
    }
    releaseHistory.getReleases().removeIf(
        release -> release.getNumber() < lastSuccessfulReleaseNumber || release.getStatus() == Failed);
  }

  public void delete(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams,
      List<KubernetesResourceId> kubernetesResourceIds, LogCallback executionLogCallback, boolean denoteOverallSuccess)
      throws Exception {
    for (KubernetesResourceId resourceId : kubernetesResourceIds) {
      DeleteCommand deleteCommand =
          client.delete().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
      ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand);
      if (result.getExitValue() != 0) {
        log.warn("Failed to delete resource {}. Error {}", resourceId.kindNameRef(), result.getOutput());
      }
    }

    if (denoteOverallSuccess) {
      executionLogCallback.saveExecutionLog("Done", INFO, CommandExecutionStatus.SUCCESS);
    }
  }

  public void describe(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback)
      throws Exception {
    final DescribeCommand describeCommand = client.describe().filename("manifests.yaml");
    runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, describeCommand);
  }

  public String getRolloutHistoryCommandForDeploymentConfig(
      K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId resourceId) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    return K8sConstants.ocRolloutHistoryCommand
        .replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(k8sDelegateTaskParams))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace)
        .trim();
  }

  @VisibleForTesting
  public ProcessResult executeCommandUsingUtils(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogOutputStream statusInfoStream, LogOutputStream statusErrorStream, String command) throws Exception {
    return executeCommandUsingUtils(
        k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, command);
  }

  public String getRolloutStatusCommandForDeploymentConfig(
      String ocPath, String kubeConfigPath, KubernetesResourceId resourceId) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    return K8sConstants.ocRolloutStatusCommand
        .replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(ocPath, kubeConfigPath))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace);
  }

  @VisibleForTesting
  public ProcessResult runK8sExecutableSilent(
      K8sDelegateTaskParams k8sDelegateTaskParams, AbstractExecutable executable) throws Exception {
    return executeCommandSilent(executable, k8sDelegateTaskParams.getWorkingDirectory());
  }

  public String getLatestRevision(
      Kubectl client, KubernetesResourceId resourceId, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
      String rolloutHistoryCommand = getRolloutHistoryCommandForDeploymentConfig(k8sDelegateTaskParams, resourceId);

      try (LogOutputStream emptyLogOutputStream = getEmptyLogOutputStream()) {
        ProcessResult result = executeCommandUsingUtils(
            k8sDelegateTaskParams, emptyLogOutputStream, emptyLogOutputStream, rolloutHistoryCommand);

        if (result.getExitValue() == 0) {
          String[] lines = result.outputUTF8().split("\\r?\\n");
          return lines[lines.length - 1].split("\t")[0];
        }
      }

    } else {
      RolloutHistoryCommand rolloutHistoryCommand =
          client.rollout().history().resource(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
      ProcessResult result = runK8sExecutableSilent(k8sDelegateTaskParams, rolloutHistoryCommand);
      if (result.getExitValue() == 0) {
        return parseLatestRevisionNumberFromRolloutHistory(result.outputUTF8());
      }
    }

    return "";
  }

  public Integer getCurrentReplicas(
      Kubectl client, KubernetesResourceId resourceId, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    GetCommand getCommand = client.get()
                                .resources(resourceId.kindNameRef())
                                .namespace(resourceId.getNamespace())
                                .output("jsonpath={$.spec.replicas}");
    ProcessResult result = runK8sExecutableSilent(k8sDelegateTaskParams, getCommand);
    if (result.getExitValue() == 0) {
      return Integer.valueOf(result.outputUTF8());
    } else {
      return null;
    }
  }

  @VisibleForTesting
  public ProcessResult executeShellCommand(String commandDirectory, String command, LogOutputStream logErrorStream,
      long timeoutInMillis) throws IOException, InterruptedException, TimeoutException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .directory(new File(commandDirectory))
                                          .commandSplit(command)
                                          .readOutput(true)
                                          .redirectError(logErrorStream);

    return processExecutor.execute();
  }

  public boolean dryRunManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) {
    try {
      executionLogCallback.saveExecutionLog(color("\nValidating manifests with Dry Run", White, Bold), INFO);

      FileIo.writeUtf8StringToFile(
          k8sDelegateTaskParams.getWorkingDirectory() + "/manifests-dry-run.yaml", ManifestHelper.toYaml(resources));

      Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

      final ApplyCommand dryrun = overriddenClient.apply().filename("manifests-dry-run.yaml").dryrun(true);
      ProcessResult result = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, dryrun);
      if (result.getExitValue() != 0) {
        executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
        return false;
      }
    } catch (Exception e) {
      log.error("Exception in running dry-run", e);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  public boolean doStatusCheck(Kubectl client, KubernetesResourceId resourceId, String workingDirectory, String ocPath,
      String kubeconfigPath, LogCallback executionLogCallback) throws Exception {
    final String eventFormat = "%-7s: %s";
    final String statusFormat = "%n%-7s: %s";

    GetCommand getEventsCommand = client.get()
                                      .resources("events")
                                      .namespace(resourceId.getNamespace())
                                      .output(K8sConstants.eventOutputFormat)
                                      .watchOnly(true);

    executionLogCallback.saveExecutionLog(GetCommand.getPrintableCommand(getEventsCommand.command()) + "\n");

    boolean success = false;

    StartedProcess eventWatchProcess = null;
    try (LogOutputStream watchInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 if (line.contains(resourceId.getName())) {
                   executionLogCallback.saveExecutionLog(format(eventFormat, "Event", line), INFO);
                 }
               }
             };
         LogOutputStream watchErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(eventFormat, "Event", line), ERROR);
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(statusFormat, "Status", line), INFO);
               }
             };
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(statusFormat, "Status", line), ERROR);
               }
             }) {
      eventWatchProcess = getEventWatchProcess(workingDirectory, getEventsCommand, watchInfoStream, watchErrorStream);

      ProcessResult result;
      if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
        String rolloutStatusCommand = getRolloutStatusCommandForDeploymentConfig(ocPath, kubeconfigPath, resourceId);

        executionLogCallback.saveExecutionLog(
            rolloutStatusCommand.substring(rolloutStatusCommand.indexOf("oc --kubeconfig")) + "\n");

        result = executeCommandUsingUtils(workingDirectory, statusInfoStream, statusErrorStream, rolloutStatusCommand);
      } else {
        RolloutStatusCommand rolloutStatusCommand = client.rollout()
                                                        .status()
                                                        .resource(resourceId.kindNameRef())
                                                        .namespace(resourceId.getNamespace())
                                                        .watch(true);

        executionLogCallback.saveExecutionLog(
            RolloutStatusCommand.getPrintableCommand(rolloutStatusCommand.command()) + "\n");

        result = rolloutStatusCommand.execute(workingDirectory, statusInfoStream, statusErrorStream, false);
      }

      success = result.getExitValue() == 0;

      if (!success) {
        log.warn(result.outputUTF8());
      }
      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    } finally {
      if (eventWatchProcess != null) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

      } else {
        executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      }
    }
  }

  public boolean doStatusCheck(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    return doStatusCheck(client, resourceId, k8sDelegateTaskParams.getWorkingDirectory(),
        k8sDelegateTaskParams.getOcPath(), k8sDelegateTaskParams.getKubeconfigPath(), executionLogCallback);
  }

  public boolean getJobStatus(K8sDelegateTaskParams k8sDelegateTaskParams, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, GetJobCommand jobCompleteCommand, GetJobCommand jobFailedCommand,
      GetJobCommand jobStatusCommand, GetJobCommand jobCompletionTimeCommand) throws Exception {
    while (true) {
      jobStatusCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false);

      ProcessResult result = jobCompleteCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        return false;
      }

      // cli command outputs with single quotes
      String jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        result = jobCompletionTimeCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);
        success = 0 == result.getExitValue();
        if (!success) {
          log.warn(result.outputUTF8());
          return false;
        }

        String completionTime = result.outputUTF8().replace("'", "");
        if (isNotBlank(completionTime)) {
          return true;
        }
      }

      result = jobFailedCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);

      success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        return false;
      }

      jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        return false;
      }

      sleep(ofSeconds(5));
    }
  }

  public boolean doStatusCheckForJob(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, String statusFormat, LogCallback executionLogCallback)
      throws Exception {
    try (LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             };
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
               }
             }) {
      GetJobCommand jobCompleteCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                             .output("jsonpath='{.status.conditions[?(@.type==\"Complete\")].status}'");
      GetJobCommand jobFailedCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                           .output("jsonpath='{.status.conditions[?(@.type==\"Failed\")].status}'");
      GetJobCommand jobStatusCommand =
          client.getJobCommand(resourceId.getName(), resourceId.getNamespace()).output("jsonpath='{.status}'");
      GetJobCommand jobCompletionTimeCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                                   .output("jsonpath='{.status.completionTime}'");

      executionLogCallback.saveExecutionLog(getPrintableCommand(jobStatusCommand.command()) + "\n");

      return getJobStatus(k8sDelegateTaskParams, statusInfoStream, statusErrorStream, jobCompleteCommand,
          jobFailedCommand, jobStatusCommand, jobCompletionTimeCommand);
    }
  }

  public boolean doStatusCheckForWorkloads(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, String statusFormat, LogCallback executionLogCallback)
      throws Exception {
    try (LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             }) {
      ProcessResult result;

      if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
        String rolloutStatusCommand = getRolloutStatusCommandForDeploymentConfig(
            k8sDelegateTaskParams.getOcPath(), k8sDelegateTaskParams.getKubeconfigPath(), resourceId);

        executionLogCallback.saveExecutionLog(
            rolloutStatusCommand.substring(rolloutStatusCommand.indexOf("oc --kubeconfig")) + "\n");

        result =
            executeCommandUsingUtils(k8sDelegateTaskParams, statusInfoStream, statusErrorStream, rolloutStatusCommand);
      } else {
        RolloutStatusCommand rolloutStatusCommand = client.rollout()
                                                        .status()
                                                        .resource(resourceId.kindNameRef())
                                                        .namespace(resourceId.getNamespace())
                                                        .watch(true);

        executionLogCallback.saveExecutionLog(getPrintableCommand(rolloutStatusCommand.command()) + "\n");

        result = rolloutStatusCommand.execute(
            k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false);
      }

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
      }

      return success;
    }
  }

  public boolean doStatusCheckForAllResources(Kubectl client, List<KubernetesResourceId> resourceIds,
      K8sDelegateTaskParams k8sDelegateTaskParams, String namespace, LogCallback executionLogCallback,
      boolean denoteOverallSuccess) throws Exception {
    if (isEmpty(resourceIds)) {
      return true;
    }

    int maxResourceNameLength = 0;
    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      maxResourceNameLength = Math.max(maxResourceNameLength, kubernetesResourceId.getName().length());
    }

    final String eventErrorFormat = "%-7s: %s";
    final String eventInfoFormat = "%-7s: %-" + maxResourceNameLength + "s   %s";
    final String statusFormat = "%n%-7s: %-" + maxResourceNameLength + "s   %s";

    Set<String> namespaces = resourceIds.stream().map(KubernetesResourceId::getNamespace).collect(toSet());
    namespaces.add(namespace);
    List<GetCommand> getEventCommands = namespaces.stream()
                                            .map(ns
                                                -> client.get()
                                                       .resources("events")
                                                       .namespace(ns)
                                                       .output(K8sConstants.eventWithNamespaceOutputFormat)
                                                       .watchOnly(true))
                                            .collect(toList());

    for (GetCommand cmd : getEventCommands) {
      executionLogCallback.saveExecutionLog(getPrintableCommand(cmd.command()) + "\n");
    }

    boolean success = false;

    List<StartedProcess> eventWatchProcesses = new ArrayList<>();
    try (LogOutputStream watchInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 Optional<KubernetesResourceId> filteredResourceId =
                     resourceIds.parallelStream()
                         .filter(kubernetesResourceId
                             -> line.contains(isNotBlank(kubernetesResourceId.getNamespace())
                                        ? kubernetesResourceId.getNamespace()
                                        : namespace)
                                 && line.contains(kubernetesResourceId.getName()))
                         .findFirst();

                 filteredResourceId.ifPresent(kubernetesResourceId
                     -> executionLogCallback.saveExecutionLog(
                         format(eventInfoFormat, "Event", kubernetesResourceId.getName(), line), INFO));
               }
             };
         LogOutputStream watchErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(eventErrorFormat, "Event", line), ERROR);
               }
             }) {
      for (GetCommand getEventsCommand : getEventCommands) {
        eventWatchProcesses.add(getEventWatchProcess(
            k8sDelegateTaskParams.getWorkingDirectory(), getEventsCommand, watchInfoStream, watchErrorStream));
      }

      for (KubernetesResourceId kubernetesResourceId : resourceIds) {
        if (Kind.Job.name().equals(kubernetesResourceId.getKind())) {
          success = doStatusCheckForJob(
              client, kubernetesResourceId, k8sDelegateTaskParams, statusFormat, executionLogCallback);
        } else {
          success = doStatusCheckForWorkloads(
              client, kubernetesResourceId, k8sDelegateTaskParams, statusFormat, executionLogCallback);
        }

        if (!success) {
          break;
        }
      }

      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    } finally {
      for (StartedProcess eventWatchProcess : eventWatchProcesses) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        if (denoteOverallSuccess) {
          executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("%nStatus check for resources in namespace [%s] failed.", namespace), INFO, FAILURE);
      }
    }
  }

  public String getResourcesInTableFormat(List<KubernetesResource> resources) {
    int maxKindLength = 16;
    int maxNameLength = 36;
    for (KubernetesResource resource : resources) {
      KubernetesResourceId id = resource.getResourceId();
      if (id.getKind().length() > maxKindLength) {
        maxKindLength = id.getKind().length();
      }

      if (id.getName().length() > maxNameLength) {
        maxNameLength = id.getName().length();
      }
    }

    maxKindLength += 4;
    maxNameLength += 4;

    StringBuilder sb = new StringBuilder(1024);
    String tableFormat = "%-" + maxKindLength + "s%-" + maxNameLength + "s%-10s";
    sb.append(System.lineSeparator())
        .append(color(format(tableFormat, "Kind", "Name", "Versioned"), White, Bold))
        .append(System.lineSeparator());

    for (KubernetesResource resource : resources) {
      KubernetesResourceId id = resource.getResourceId();
      sb.append(color(format(tableFormat, id.getKind(), id.getName(), id.isVersioned()), Gray))
          .append(System.lineSeparator());
    }

    return sb.toString();
  }

  @VisibleForTesting
  public String generateTruncatedFileListForLogging(Path basePath, Stream<Path> paths) {
    StringBuilder sb = new StringBuilder(1024);
    AtomicInteger filesTraversed = new AtomicInteger(0);
    paths.filter(Files::isRegularFile).forEach(each -> {
      if (filesTraversed.getAndIncrement() <= K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
        sb.append(color(format("- %s", getRelativePath(each.toString(), basePath.toString())), Gray))
            .append(System.lineSeparator());
      }
    });
    if (filesTraversed.get() > K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
      sb.append(color(format("- ..%d more", filesTraversed.get() - K8sConstants.FETCH_FILES_DISPLAY_LIMIT), Gray))
          .append(System.lineSeparator());
    }

    return sb.toString();
  }

  @VisibleForTesting
  public String getManifestFileNamesInLogFormat(String manifestFilesDirectory) throws IOException {
    Path basePath = Paths.get(manifestFilesDirectory);
    try (Stream<Path> paths = Files.walk(basePath)) {
      return generateTruncatedFileListForLogging(basePath, paths);
    }
  }

  public void deleteSkippedManifestFiles(String manifestFilesDirectory, LogCallback executionLogCallback)
      throws Exception {
    List<FileData> files;
    Path directory = Paths.get(manifestFilesDirectory);

    try {
      files = getFilesUnderPath(directory.toString());
    } catch (Exception ex) {
      log.info(ExceptionUtils.getMessage(ex));
      throw new WingsException("Failed to get files. Error: " + ExceptionUtils.getMessage(ex));
    }

    List<String> skippedFilesList = new ArrayList<>();

    for (FileData fileData : files) {
      try {
        String fileContent = new String(fileData.getFileBytes(), UTF_8);

        if (isNotBlank(fileContent)
            && fileContent.split("\\r?\\n")[0].contains(SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT)) {
          skippedFilesList.add(fileData.getFilePath());
        }
      } catch (Exception ex) {
        log.info("Could not convert to string for file" + fileData.getFilePath(), ex);
      }
    }

    if (isNotEmpty(skippedFilesList)) {
      executionLogCallback.saveExecutionLog("Following manifest files are skipped for applying");
      for (String file : skippedFilesList) {
        executionLogCallback.saveExecutionLog(color(file, Yellow, Bold));

        String filePath = Paths.get(manifestFilesDirectory, file).toString();
        FileIo.deleteFileIfExists(filePath);
      }

      executionLogCallback.saveExecutionLog("\n");
    }
  }

  public List<KubernetesResource> readManifests(List<FileData> manifestFiles, LogCallback executionLogCallback) {
    List<KubernetesResource> result = new ArrayList<>();

    for (FileData manifestFile : manifestFiles) {
      if (isValidManifestFile(manifestFile.getFileName())) {
        try {
          result.addAll(ManifestHelper.processYaml(manifestFile.getFileContent()));
        } catch (Exception e) {
          executionLogCallback.saveExecutionLog("Exception while processing " + manifestFile.getFileName(), ERROR);
          throw e;
        }
      }
    }

    return result.stream().sorted(new KubernetesResourceComparer()).collect(toList());
  }

  public List<FileData> readManifestFilesFromDirectory(String manifestFilesDirectory) {
    List<FileData> fileDataList;
    Path directory = Paths.get(manifestFilesDirectory);

    try {
      fileDataList = getFilesUnderPath(directory.toString());
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex));
      throw new WingsException("Failed to get files. Error: " + ExceptionUtils.getMessage(ex));
    }

    List<FileData> manifestFiles = new ArrayList<>();
    for (FileData fileData : fileDataList) {
      if (isValidManifestFile(fileData.getFilePath())) {
        manifestFiles.add(FileData.builder()
                              .fileName(fileData.getFilePath())
                              .fileContent(new String(fileData.getFileBytes(), UTF_8))
                              .build());
      } else {
        log.info("Found file [{}] with unsupported extension", fileData.getFilePath());
      }
    }

    return manifestFiles;
  }

  public List<FileData> replaceManifestPlaceholdersWithLocalDelegateSecrets(List<FileData> manifestFiles) {
    List<FileData> updatedManifestFiles = new ArrayList<>();
    for (FileData manifestFile : manifestFiles) {
      updatedManifestFiles.add(
          FileData.builder()
              .fileName(manifestFile.getFileName())
              .fileContent(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(manifestFile.getFileContent()))
              .build());
    }

    return updatedManifestFiles;
  }

  public List<KubernetesResource> readManifestAndOverrideLocalSecrets(
      List<FileData> manifestFiles, LogCallback executionLogCallback, boolean overrideLocalSecrets) {
    if (overrideLocalSecrets) {
      manifestFiles = replaceManifestPlaceholdersWithLocalDelegateSecrets(manifestFiles);
    }
    return readManifests(manifestFiles, executionLogCallback);
  }

  public String writeValuesToFile(String directoryPath, List<String> valuesFiles) throws Exception {
    StringBuilder valuesFilesOptionsBuilder = new StringBuilder(128);

    for (int i = 0; i < valuesFiles.size(); i++) {
      validateValuesFileContents(valuesFiles.get(i));
      String valuesFileName = format("values-%d.yaml", i);
      FileIo.writeUtf8StringToFile(directoryPath + '/' + valuesFileName, valuesFiles.get(i));
      valuesFilesOptionsBuilder.append(" -f ").append(valuesFileName);
    }

    return valuesFilesOptionsBuilder.toString();
  }

  public List<FileData> renderManifestFilesForGoTemplate(K8sDelegateTaskParams k8sDelegateTaskParams,
      List<FileData> manifestFiles, List<String> valuesFiles, LogCallback executionLogCallback, long timeoutInMillis)
      throws Exception {
    if (isEmpty(valuesFiles)) {
      executionLogCallback.saveExecutionLog("No values.yaml file found. Skipping template rendering.");
      return manifestFiles;
    }

    String valuesFileOptions = null;
    try {
      valuesFileOptions = writeValuesToFile(k8sDelegateTaskParams.getWorkingDirectory(), valuesFiles);
    } catch (KubernetesValuesException kvexception) {
      String message = kvexception.getParams().get("reason").toString();
      executionLogCallback.saveExecutionLog(message, ERROR);
      throw new KubernetesValuesException(message, kvexception.getCause());
    }

    log.info("Values file options: " + valuesFileOptions);

    List<FileData> result = new ArrayList<>();

    executionLogCallback.saveExecutionLog(color("\nRendering manifest files using go template", White, Bold));
    executionLogCallback.saveExecutionLog(
        color("Only manifest files with [.yaml] or [.yml] extension will be processed", White, Bold));

    for (FileData manifestFile : manifestFiles) {
      if (StringUtils.equals(values_filename, manifestFile.getFileName())) {
        continue;
      }

      FileIo.writeUtf8StringToFile(
          k8sDelegateTaskParams.getWorkingDirectory() + "/template.yaml", manifestFile.getFileContent());

      try (LogOutputStream logErrorStream = getExecutionLogOutputStream(executionLogCallback, ERROR)) {
        String goTemplateCommand = encloseWithQuotesIfNeeded(k8sDelegateTaskParams.getGoTemplateClientPath())
            + " -t template.yaml " + valuesFileOptions;
        ProcessResult processResult = executeShellCommand(
            k8sDelegateTaskParams.getWorkingDirectory(), goTemplateCommand, logErrorStream, timeoutInMillis);

        if (processResult.getExitValue() != 0) {
          throw new InvalidRequestException(format("Failed to render template for %s. Error %s",
                                                manifestFile.getFileName(), processResult.getOutput().getUTF8()),
              USER);
        }

        result.add(
            FileData.builder().fileName(manifestFile.getFileName()).fileContent(processResult.outputUTF8()).build());
      }
    }

    return result;
  }

  public String generateResourceIdentifier(KubernetesResourceId resourceId) {
    return new StringBuilder(128)
        .append(resourceId.getNamespace())
        .append('/')
        .append(resourceId.getKind())
        .append('/')
        .append(resourceId.getName())
        .toString();
  }

  public List<KubernetesResourceId> fetchAllResourcesForRelease(
      String releaseName, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    executionLogCallback.saveExecutionLog("Fetching all resources created for release: " + releaseName);

    final V1ConfigMap releaseConfigMap = kubernetesContainerService.getConfigMap(kubernetesConfig, releaseName);
    final V1Secret releaseSecret = kubernetesContainerService.getSecret(kubernetesConfig, releaseName);

    if (!(releaseHistoryPresent(releaseConfigMap) || releaseHistoryPresent(releaseSecret))) {
      executionLogCallback.saveExecutionLog("No resource history was available");
      return emptyList();
    }

    String releaseHistoryDataString = releaseHistoryPresent(releaseSecret)
        ? new String(releaseSecret.getData().get(ReleaseHistoryKeyName), UTF_8)
        : releaseConfigMap.getData().get(ReleaseHistoryKeyName);
    ReleaseHistory releaseHistory = ReleaseHistory.createFromData(releaseHistoryDataString);

    if (isEmpty(releaseHistory.getReleases())) {
      return emptyList();
    }

    Map<String, KubernetesResourceId> kubernetesResourceIdMap = new HashMap<>();
    for (Release release : releaseHistory.getReleases()) {
      if (isNotEmpty(release.getResources())) {
        release.getResources().forEach(
            resource -> kubernetesResourceIdMap.put(generateResourceIdentifier(resource), resource));
      }
    }

    if (releaseConfigMap != null) {
      KubernetesResourceId harnessGeneratedCMResource = KubernetesResourceId.builder()
                                                            .kind(releaseConfigMap.getKind())
                                                            .name(releaseName)
                                                            .namespace(kubernetesConfig.getNamespace())
                                                            .build();
      kubernetesResourceIdMap.put(generateResourceIdentifier(harnessGeneratedCMResource), harnessGeneratedCMResource);
    }
    if (releaseSecret != null) {
      KubernetesResourceId harnessGeneratedSecretResource = KubernetesResourceId.builder()
                                                                .kind(releaseSecret.getKind())
                                                                .name(releaseName)
                                                                .namespace(kubernetesConfig.getNamespace())
                                                                .build();
      kubernetesResourceIdMap.put(
          generateResourceIdentifier(harnessGeneratedSecretResource), harnessGeneratedSecretResource);
    }
    return new ArrayList<>(kubernetesResourceIdMap.values());
  }

  private boolean releaseHistoryPresent(V1ConfigMap configMap) {
    return configMap != null && isNotEmpty(configMap.getData())
        && isNotBlank(configMap.getData().get(ReleaseHistoryKeyName));
  }

  private boolean releaseHistoryPresent(V1Secret secret) {
    return secret != null && isNotEmpty(secret.getData())
        && ArrayUtils.isNotEmpty(secret.getData().get(ReleaseHistoryKeyName));
  }

  public List<FileData> readFilesFromDirectory(
      String directory, List<String> filePaths, LogCallback executionLogCallback) {
    List<FileData> manifestFiles = new ArrayList<>();

    for (String filepath : filePaths) {
      if (isValidManifestFile(filepath)) {
        Path path = Paths.get(directory, filepath);
        byte[] fileBytes;

        try {
          fileBytes = Files.readAllBytes(path);
        } catch (Exception ex) {
          log.info(ExceptionUtils.getMessage(ex));
          throw new InvalidRequestException(
              format("Failed to read file at path [%s].%nError: %s", filepath, ExceptionUtils.getMessage(ex)));
        }

        manifestFiles.add(FileData.builder().fileName(filepath).fileContent(new String(fileBytes, UTF_8)).build());
      } else {
        executionLogCallback.saveExecutionLog(
            color(format("Ignoring file [%s] with unsupported extension", filepath), Yellow, Bold));
      }
    }

    return manifestFiles;
  }

  public boolean doStatusCheckForAllCustomResources(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess,
      long timeoutInMillis) throws Exception {
    List<KubernetesResourceId> resourceIds =
        resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());
    if (isEmpty(resourceIds)) {
      return true;
    }

    executionLogCallback.saveExecutionLog("Performing steady check for managed workloads \n");
    int maxResourceNameLength = 0;
    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      maxResourceNameLength = Math.max(maxResourceNameLength, kubernetesResourceId.getName().length());
    }

    final String eventInfoFormat = "%-7s: %-" + maxResourceNameLength + "s   %s";

    Set<String> namespaces = resourceIds.stream().map(KubernetesResourceId::getNamespace).collect(toSet());
    List<GetCommand> getEventCommands = namespaces.stream()
                                            .map(ns
                                                -> client.get()
                                                       .resources("events")
                                                       .namespace(ns)
                                                       .output(K8sConstants.eventWithNamespaceOutputFormat)
                                                       .watchOnly(true))
                                            .collect(toList());

    for (GetCommand cmd : getEventCommands) {
      executionLogCallback.saveExecutionLog(GetCommand.getPrintableCommand(cmd.command()) + "\n");
    }

    boolean success = false;

    List<StartedProcess> eventWatchProcesses = new ArrayList<>();
    String currentSteadyCondition = null;
    try (LogOutputStream watchInfoStream =
             createFilteredInfoLogOutputStream(resourceIds, executionLogCallback, eventInfoFormat);
         LogOutputStream watchErrorStream = createErrorLogOutputStream(executionLogCallback)) {
      for (GetCommand getEventsCommand : getEventCommands) {
        eventWatchProcesses.add(getEventWatchProcess(
            k8sDelegateTaskParams.getWorkingDirectory(), getEventsCommand, watchInfoStream, watchErrorStream));
      }

      for (KubernetesResource kubernetesResource : resources) {
        String steadyCondition = kubernetesResource.getMetadataAnnotationValue(HarnessAnnotations.steadyStateCondition);
        currentSteadyCondition = steadyCondition;
        success = timeLimiter.callWithTimeout(
            ()
                -> doStatusCheckForCustomResources(client, kubernetesResource.getResourceId(), steadyCondition,
                    k8sDelegateTaskParams, executionLogCallback),
            timeoutInMillis, TimeUnit.MILLISECONDS, true);

        if (!success) {
          break;
        }
      }

      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      executionLogCallback.saveExecutionLog("\nFailed to execute the status check of the custom resources.", INFO);
      executionLogCallback.saveExecutionLog(color(
          format(
              "%nPossible reasons: %n\t 1. The steady check condition [%s] is wrong. %n\t 2. The custom controller is not running.",
              currentSteadyCondition),
          Yellow, Bold));

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      return false;
    } finally {
      for (StartedProcess eventWatchProcess : eventWatchProcesses) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        if (denoteOverallSuccess) {
          executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("%nStatus check for resources in namespace [%s] failed.", namespaces), INFO,
            CommandExecutionStatus.FAILURE);
      }
    }
  }

  public void checkSteadyStateCondition(List<KubernetesResource> customWorkloads) {
    for (KubernetesResource customWorkload : customWorkloads) {
      String steadyCondition = customWorkload.getMetadataAnnotationValue(HarnessAnnotations.steadyStateCondition);
      if (isEmpty(steadyCondition)) {
        throw new InvalidArgumentsException(
            Pair.of(HarnessAnnotations.steadyStateCondition, "Metadata annotation not provided."));
      }
    }
  }

  @VisibleForTesting
  LogOutputStream createFilteredInfoLogOutputStream(
      List<KubernetesResourceId> resourceIds, LogCallback executionLogCallback, String eventInfoFormat) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        Optional<KubernetesResourceId> filteredResourceId =
            resourceIds.parallelStream()
                .filter(kubernetesResourceId
                    -> line.contains(kubernetesResourceId.getNamespace())
                        && line.contains(kubernetesResourceId.getName()))
                .findFirst();

        filteredResourceId.ifPresent(kubernetesResourceId
            -> executionLogCallback.saveExecutionLog(
                format(eventInfoFormat, "Event", kubernetesResourceId.getName(), line), INFO));
      }
    };
  }

  @VisibleForTesting
  LogOutputStream createErrorLogOutputStream(LogCallback executionLogCallback) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format("%-7s: %s", "Event", line), ERROR);
      }
    };
  }

  boolean doStatusCheckForCustomResources(Kubectl client, KubernetesResourceId resourceId, String steadyCondition,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    GetCommand crdStatusCommand =
        client.get().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace()).output("json");

    executionLogCallback.saveExecutionLog(getPrintableCommand(crdStatusCommand.command()) + "\n");
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    while (true) {
      ProcessResult result = crdStatusCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        return false;
      }

      evaluatorResponseContext.put("response", result.outputUTF8());
      String steadyResult = delegateExpressionEvaluator.substitute(steadyCondition, evaluatorResponseContext);
      if (isNotEmpty(steadyResult)) {
        boolean steady = Boolean.parseBoolean(steadyResult);
        if (steady) {
          return true;
        }
      }
    }
  }

  @VisibleForTesting
  LogOutputStream createStatusInfoLogOutputStream(LogCallback executionLogCallback, String message, String format) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format(format, "Status", message, line), INFO);
      }
    };
  }

  @VisibleForTesting
  LogOutputStream createStatusErrorLogOutputStream(LogCallback executionLogCallback, String message, String format) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format(format, "Status", message, line), ERROR);
      }
    };
  }

  public String getReleaseHistoryData(KubernetesConfig kubernetesConfig, String releaseName) {
    return getReleaseHistoryDataK8sClient(kubernetesConfig, releaseName);
  }

  private String getReleaseHistoryDataK8sClient(KubernetesConfig kubernetesConfig, String releaseName) {
    String releaseHistoryData =
        kubernetesContainerService.fetchReleaseHistoryFromSecrets(kubernetesConfig, releaseName);

    if (isEmpty(releaseHistoryData)) {
      releaseHistoryData = kubernetesContainerService.fetchReleaseHistoryFromConfigMap(kubernetesConfig, releaseName);
    }

    return releaseHistoryData;
  }

  public String getReleaseHistoryDataFromConfigMap(KubernetesConfig kubernetesConfig, String releaseName) {
    return kubernetesContainerService.fetchReleaseHistoryFromConfigMap(kubernetesConfig, releaseName);
  }

  public void saveReleaseHistoryInConfigMap(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistoryAsYaml) {
    kubernetesContainerService.saveReleaseHistoryInConfigMap(kubernetesConfig, releaseName, releaseHistoryAsYaml);
  }

  public void saveReleaseHistory(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory, boolean storeInSecrets) {
    kubernetesContainerService.saveReleaseHistory(kubernetesConfig, releaseName, releaseHistory, storeInSecrets);
  }

  public String getReleaseHistoryFromSecret(KubernetesConfig kubernetesConfig, String releaseName) {
    return kubernetesContainerService.fetchReleaseHistoryFromSecrets(kubernetesConfig, releaseName);
  }

  public LogCallback getLogCallback(
      ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream);
  }

  public List<FileData> renderTemplate(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, List<String> valuesFiles,
      String releaseName, String namespace, LogCallback executionLogCallback, Integer timeoutInMin) throws Exception {
    ManifestType manifestType = manifestDelegateConfig.getManifestType();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(timeoutInMin);

    switch (manifestType) {
      case K8S_MANIFEST:
        List<FileData> manifestFiles = readManifestFilesFromDirectory(manifestFilesDirectory);
        return renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, valuesFiles, executionLogCallback, timeoutInMillis);

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s]", manifestType.name()));
    }
  }

  public List<FileData> renderTemplateForGivenFiles(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, @NotEmpty List<String> filesList,
      List<String> valuesFiles, String releaseName, String namespace, LogCallback executionLogCallback,
      Integer timeoutInMin) throws Exception {
    ManifestType manifestType = manifestDelegateConfig.getManifestType();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(timeoutInMin);

    switch (manifestType) {
      case K8S_MANIFEST:
        List<FileData> manifestFiles = readFilesFromDirectory(manifestFilesDirectory, filesList, executionLogCallback);
        return renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, valuesFiles, executionLogCallback, timeoutInMillis);

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s]", manifestType.name()));
    }
  }

  public List<KubernetesResource> getResourcesFromManifests(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, @NotEmpty List<String> filesList,
      List<String> valuesFiles, String releaseName, String namespace, LogCallback logCallback, Integer timeoutInMin)
      throws Exception {
    List<FileData> manifestFiles = renderTemplateForGivenFiles(k8sDelegateTaskParams, manifestDelegateConfig,
        manifestFilesDirectory, filesList, valuesFiles, releaseName, namespace, logCallback, timeoutInMin);
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }

    List<KubernetesResource> resources = readManifests(manifestFiles, logCallback);
    setNamespaceToKubernetesResourcesIfRequired(resources, namespace);

    return resources;
  }

  public boolean fetchManifestFilesAndWriteToDirectory(ManifestDelegateConfig manifestDelegateConfig,
      String manifestFilesDirectory, LogCallback executionLogCallback, long timeoutInMillis, String accountId) {
    ManifestType manifestType = manifestDelegateConfig.getManifestType();
    switch (manifestType) {
      case K8S_MANIFEST:
        return downloadManifestFilesFromGit(
            manifestDelegateConfig, manifestFilesDirectory, executionLogCallback, accountId);

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s]", manifestType.name()));
    }
  }

  private boolean downloadManifestFilesFromGit(ManifestDelegateConfig manifestDelegateConfig,
      String manifestFilesDirectory, LogCallback executionLogCallback, String accountId) {
    if (!(manifestDelegateConfig instanceof K8sManifestDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("manifestDelegateConfig", "Must be instance of K8sManifestDelegateConfig"));
    }

    GitStoreDelegateConfig gitStoreDelegateConfig =
        (GitStoreDelegateConfig) (((K8sManifestDelegateConfig) manifestDelegateConfig).getStoreDelegateConfig());

    // ToDo What to set here now as we have a list now?
    //    if (isBlank(gitStoreDelegateConfig.getPaths().getFilePath())) {
    //      delegateManifestConfig.getGitFileConfig().setFilePath(StringUtils.EMPTY);
    //    }

    try {
      printGitConfigInExecutionLogs(gitStoreDelegateConfig, executionLogCallback);
      ngGitService.downloadFiles(gitStoreDelegateConfig, manifestFilesDirectory, accountId, null);

      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(getManifestFileNamesInLogFormat(manifestFilesDirectory));
      executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return true;
    } catch (Exception e) {
      String errorMsg = "Failed to download manifest files from git. ";
      executionLogCallback.saveExecutionLog(
          errorMsg + ExceptionUtils.getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      throw new GitOperationException(errorMsg, e);
    }
  }

  private void printGitConfigInExecutionLogs(
      GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
    executionLogCallback.saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfigDTO.getUrl());

    if (FetchType.BRANCH == gitStoreDelegateConfig.getFetchType()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitStoreDelegateConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitStoreDelegateConfig.getCommitId());
    }

    gitStoreDelegateConfig.getPaths().stream().collect(
        Collectors.joining(System.lineSeparator(), "\nFetching manifest files at path: ", System.lineSeparator()));
  }

  public ConnectorValidationResult validate(
      ConnectorConfigDTO connector, String accountIdentifier, List<EncryptedDataDetail> encryptionDetailList) {
    ConnectivityStatus connectivityStatus = ConnectivityStatus.FAILURE;
    KubernetesConfig kubernetesConfig = getKubernetesConfig(connector, encryptionDetailList);
    try {
      kubernetesContainerService.validate(kubernetesConfig);
      connectivityStatus = ConnectivityStatus.SUCCESS;
    } catch (Exception ex) {
      log.info("Exception while validating kubernetes credentials", ex);
      return createConnectivityFailureValidationResult(ex);
    }
    return ConnectorValidationResult.builder().status(connectivityStatus).build();
  }

  private KubernetesConfig getKubernetesConfig(
      ConnectorConfigDTO connector, List<EncryptedDataDetail> encryptionDetailList) {
    KubernetesClusterConfigDTO kubernetesClusterConfig = (KubernetesClusterConfigDTO) connector;
    if (kubernetesClusterConfig.getCredential().getKubernetesCredentialType()
        == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesAuthCredentialDTO kubernetesCredentialAuth = getKubernetesCredentialsAuth(
          (KubernetesClusterDetailsDTO) kubernetesClusterConfig.getCredential().getConfig());
      secretDecryptionService.decrypt(kubernetesCredentialAuth, encryptionDetailList);
    }
    return k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(kubernetesClusterConfig);
  }

  public ConnectorValidationResult validateCEKubernetesCluster(
      ConnectorConfigDTO connector, String accountIdentifier, List<EncryptedDataDetail> encryptionDetailList) {
    ConnectivityStatus connectivityStatus = ConnectivityStatus.SUCCESS;
    KubernetesConfig kubernetesConfig = getKubernetesConfig(connector, encryptionDetailList);
    List<ErrorDetail> errorDetails = new ArrayList<>();
    String errorSummary = "";
    try {
      CEK8sDelegatePrerequisite.MetricsServerCheck metricsServerCheck =
          kubernetesContainerService.validateMetricsServer(kubernetesConfig);
      List<CEK8sDelegatePrerequisite.Rule> ruleList =
          kubernetesContainerService.validateCEResourcePermissions(kubernetesConfig);

      if (!metricsServerCheck.getIsInstalled()) {
        errorDetails.add(ErrorDetail.builder()
                             .message("Please install metrics server on your cluster")
                             .reason("couldn't access metrics server")
                             .build());
        errorSummary += metricsServerCheck.getMessage();
      }
      if (!ruleList.isEmpty()) {
        errorDetails.addAll(ruleList.stream()
                                .map(e
                                    -> ErrorDetail.builder()
                                           .reason(String.format("'%s' not granted on '%s' in apiGroup:'%s'",
                                               e.getVerbs(), e.getResources(), e.getApiGroups()))
                                           .message(e.getMessage())
                                           .code(0)
                                           .build())
                                .collect(toList()));
        errorSummary += "; few permissions are missing.";
      }

      if (!errorDetails.isEmpty()) {
        return ConnectorValidationResult.builder()
            .errorSummary(errorSummary)
            .errors(errorDetails)
            .status(ConnectivityStatus.FAILURE)
            .build();
      }
    } catch (Exception ex) {
      log.info("Exception while validating kubernetes credentials", ex);
      return createConnectivityFailureValidationResult(ex);
    }
    return ConnectorValidationResult.builder().status(connectivityStatus).build();
  }

  private ConnectorValidationResult createConnectivityFailureValidationResult(Exception ex) {
    String errorMessage = ex.getMessage();
    ErrorDetail errorDetail = ngErrorHelper.createErrorDetail(errorMessage);
    String errorSummary = ngErrorHelper.getErrorSummary(errorMessage);
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.FAILURE)
        .errors(Collections.singletonList(errorDetail))
        .errorSummary(errorSummary)
        .build();
  }

  private KubernetesAuthCredentialDTO getKubernetesCredentialsAuth(
      KubernetesClusterDetailsDTO kubernetesClusterConfigDTO) {
    return kubernetesClusterConfigDTO.getAuth().getCredentials();
  }

  @VisibleForTesting
  public List<K8sPod> tagNewPods(List<K8sPod> newPods, List<K8sPod> existingPods) {
    Set<String> existingPodNames = existingPods.stream().map(K8sPod::getName).collect(Collectors.toSet());
    List<K8sPod> allPods = new ArrayList<>(newPods);
    allPods.forEach(pod -> {
      if (!existingPodNames.contains(pod.getName())) {
        pod.setNewPod(true);
      }
    });
    return allPods;
  }
}
