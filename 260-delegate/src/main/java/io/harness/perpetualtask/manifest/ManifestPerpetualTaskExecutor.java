package io.harness.perpetualtask.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.network.SafeHttpCall.executeWithExceptions;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.ManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.artifact.ArtifactsPublishedCache;
import io.harness.serializer.KryoSerializer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.delegatetasks.manifest.ApplicationManifestLogContext;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.delegatetasks.manifest.ManifestCollectionResponse;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Slf4j
@OwnedBy(CDC)
public class ManifestPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final long INTERNAL_TIMEOUT_IN_MS = 120L * 1000;

  private final ManagerClient managerClient;
  private final KryoSerializer kryoSerializer;
  private final ManifestRepositoryService manifestRepositoryService;

  private final @Getter Cache<String, ArtifactsPublishedCache<HelmChart>> cache = Caffeine.newBuilder().build();

  @Inject
  public ManifestPerpetualTaskExecutor(
      ManifestRepositoryService manifestRepositoryService, ManagerClient managerClient, KryoSerializer kryoSerializer) {
    this.managerClient = managerClient;
    this.kryoSerializer = kryoSerializer;
    this.manifestRepositoryService = manifestRepositoryService;
  }

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    ManifestCollectionTaskParams manifestParams = getTaskParams(params);
    String appManifestId = manifestParams.getAppManifestId();
    logger.info("Started manifest collection for appManifestId:{}", appManifestId);
    ManifestCollectionParams manifestCollectionParams =
        (ManifestCollectionParams) kryoSerializer.asObject(manifestParams.getManifestCollectionParams().toByteArray());

    ArtifactsPublishedCache<HelmChart> appManifestCache = cache.get(appManifestId,
        id
        -> new ArtifactsPublishedCache<>(manifestCollectionParams.getPublishedVersions(), HelmChart::getVersion, true));

    String perpetualTaskId = taskId.getId();
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ApplicationManifestLogContext(
             appManifestId, manifestCollectionParams.getServiceId(), OVERRIDE_ERROR)) {
      Instant startTime = Instant.now();
      if (!appManifestCache.needsToPublish()) {
        collectManifests(appManifestCache, manifestCollectionParams, perpetualTaskId);
      }

      if (appManifestCache.needsToPublish()) {
        publishFromCache(
            appManifestCache, startTime.plusMillis(INTERNAL_TIMEOUT_IN_MS), manifestCollectionParams, perpetualTaskId);
        logger.info("Published manifest successfully");
      }
    } catch (Exception e) {
      logger.error("Manifest collection failed with the following error: ", e);
    }

    return PerpetualTaskResponse.builder().responseCode(200).responseMessage("success").build();
  }

  private void publishFromCache(ArtifactsPublishedCache<HelmChart> appManifestCache, Instant expiryTime,
      ManifestCollectionParams params, String taskId) {
    if (expiryTime.isBefore(Instant.now())) {
      logger.warn("Manifest Collection timed out after {} seconds",
          Instant.now().compareTo(expiryTime.minusMillis(INTERNAL_TIMEOUT_IN_MS)));
      return;
    }
    ImmutablePair<List<HelmChart>, Boolean> unpublishedDetails = appManifestCache.getLimitedUnpublishedBuildDetails();
    List<HelmChart> unpublishedVersions = unpublishedDetails.getLeft();
    Set<String> toBeDeletedVersions = appManifestCache.getToBeDeletedArtifactKeys();
    if (isEmpty(toBeDeletedVersions) && isEmpty(unpublishedVersions)) {
      logger.info("No new manifest versions added or deleted to publish");
      return;
    }
    ManifestCollectionExecutionResponse response =
        ManifestCollectionExecutionResponse.builder()
            .appManifestId(params.getAppManifestId())
            .appId(params.getAppId())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .manifestCollectionResponse(ManifestCollectionResponse.builder()
                                            .helmCharts(unpublishedVersions)
                                            .toBeDeletedKeys(toBeDeletedVersions)
                                            .stable(!unpublishedDetails.getRight())
                                            .build())
            .build();

    if (publishToManager(params.getAccountId(), taskId, response)) {
      appManifestCache.removeDeletedArtifactKeys(toBeDeletedVersions);
      appManifestCache.addPublishedBuildDetails(unpublishedVersions);
      publishFromCache(appManifestCache, expiryTime, params, taskId);
      logger.info("Published {} manifest versions to manager",
          unpublishedVersions.stream().map(HelmChart::getVersion).collect(Collectors.joining(",")));
    }
  }

  private boolean publishToManager(String accountId, String taskId, ManifestCollectionExecutionResponse response) {
    try {
      executeWithExceptions(managerClient.publishManifestCollectionResult(taskId, accountId, response));
      return true;
    } catch (Exception ex) {
      logger.error("Failed to publish build source execution response with status: {}",
          response.getCommandExecutionStatus().name(), ex);
      return false;
    }
  }

  private void collectManifests(ArtifactsPublishedCache<HelmChart> appManifestCache, ManifestCollectionParams params,
      String taskId) throws Exception {
    try {
      List<HelmChart> collectedManifests = manifestRepositoryService.collectManifests(params);
      if (isEmpty(collectedManifests)) {
        logger.info("No manifests present for the repository");
        return;
      }

      logger.info("Collected {} manifest versions from repository", collectedManifests.size());
      appManifestCache.addCollectionResult(collectedManifests);
    } catch (Exception e) {
      publishToManager(params.getAccountId(), taskId,
          ManifestCollectionExecutionResponse.builder()
              .appManifestId(params.getAppManifestId())
              .appId(params.getAppId())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    ManifestCollectionTaskParams manifestParams = getTaskParams(params);
    cache.invalidate(manifestParams.getAppManifestId());
    ManifestCollectionParams manifestCollectionParams =
        (ManifestCollectionParams) kryoSerializer.asObject(manifestParams.getManifestCollectionParams().toByteArray());
    try {
      manifestRepositoryService.cleanup(manifestCollectionParams);
      logger.info("Cleanup completed successfully for perpetual task: {}, app manifest: {}", taskId.getId(),
          manifestParams.getAppManifestId());
    } catch (Exception e) {
      logger.warn("Error in cleaning up after manifest collection", e);
    }
    return false;
  }

  private ManifestCollectionTaskParams getTaskParams(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), ManifestCollectionTaskParams.class);
  }
}
