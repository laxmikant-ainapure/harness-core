package io.harness.cvng.activity.services.impl;

import static io.harness.cvng.activity.CVActivityConstants.HEALTH_VERIFICATION_RETRIGGER_BUFFER_MINS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.activity.beans.ActivityDashboardDTO;
import io.harness.cvng.activity.beans.ActivityVerificationResultDTO;
import io.harness.cvng.activity.beans.ActivityVerificationResultDTO.CategoryRisk;
import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.beans.DeploymentActivityPopoverResultDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentResultSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary;
import io.harness.cvng.activity.beans.DeploymentActivityVerificationResultDTO;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.entities.CustomActivity;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.DeploymentActivity.DeploymentActivityKeys;
import io.harness.cvng.activity.entities.InfrastructureActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.source.services.api.CD10ActivitySourceService;
import io.harness.cvng.alert.services.api.AlertRuleService;
import io.harness.cvng.alert.util.VerificationStatus;
import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityStatusDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.activity.cd10.CD10RegisterActivityDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.cvng.dashboard.services.api.HealthVerificationHeatMapService;
import io.harness.cvng.verificationjob.CVVerificationJobConstants;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ActivityServiceImpl implements ActivityService {
  private static final int RECENT_DEPLOYMENT_ACTIVITIES_RESULT_SIZE = 5;
  @Inject private WebhookService webhookService;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationJobService verificationJobService;
  @Inject private NextGenService nextGenService;
  @Inject private HealthVerificationHeatMapService healthVerificationHeatMapService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationManagerService verificationManagerService;
  @Inject private CD10ActivitySourceService cd10ActivitySourceService;
  @Inject private AlertRuleService alertRuleService;

  @Override
  public Activity get(String activityId) {
    Preconditions.checkNotNull(activityId, "ActivityID should not be null");
    return hPersistence.get(Activity.class, activityId);
  }

  @Override
  public Activity getByVerificationJobInstanceId(String verificationJobInstanceId) {
    return hPersistence.createQuery(Activity.class, excludeAuthority)
        .field(ActivityKeys.verificationJobInstanceIds)
        .contains(verificationJobInstanceId)
        .get();
  }

  @Override
  public String register(String accountId, String webhookToken, ActivityDTO activityDTO) {
    webhookService.validateWebhookToken(
        webhookToken, activityDTO.getProjectIdentifier(), activityDTO.getOrgIdentifier());
    return register(accountId, activityDTO);
  }

  private String register(String accountId, ActivityDTO activityDTO) {
    Preconditions.checkNotNull(activityDTO);
    Activity activity = getActivityFromDTO(activityDTO);
    activity.validate();
    activity.setVerificationJobInstanceIds(createVerificationJobInstancesForActivity(activity));
    hPersistence.save(activity);
    log.info("Registered  an activity of type {} for account {}, project {}, org {}", activity.getType(), accountId,
        activity.getProjectIdentifier(), activity.getOrgIdentifier());
    return activity.getUuid();
  }
  @Override
  public CD10RegisterActivityDTO registerCD10Activity(String accountId, ActivityDTO activityDTO) {
    Preconditions.checkNotNull(activityDTO);
    Preconditions.checkState(
        activityDTO.getVerificationJobRuntimeDetails().size() == 1, "RuntimeDetails should be of size 1");
    if (activityDTO.getServiceIdentifier() == null || activityDTO.getEnvironmentIdentifier() == null) {
      Map<String, String> runtimeValues = activityDTO.getVerificationJobRuntimeDetails().get(0).getRuntimeValues();
      String harness10CdAppId = runtimeValues.get("harnessCdAppId");
      String harness10CdServiceId = runtimeValues.get("harnessCdServiceId");
      String harness10CdEnvId = runtimeValues.get("harnessCdEnvId");
      Preconditions.checkNotNull(harness10CdAppId, "harnessCdAppId is not present in runtimeValues");
      Preconditions.checkNotNull(harness10CdServiceId, "harnessCdServiceId is not present in runtimeValues");
      Preconditions.checkNotNull(harness10CdEnvId, "harnessCdEnvId is not present in runtimeValues");
      if (activityDTO.getEnvironmentIdentifier() == null) {
        activityDTO.setEnvironmentIdentifier(cd10ActivitySourceService.getNextGenEnvIdentifier(accountId,
            activityDTO.getOrgIdentifier(), activityDTO.getProjectIdentifier(), harness10CdAppId, harness10CdEnvId));
        runtimeValues.put(CVVerificationJobConstants.ENV_IDENTIFIER_KEY, activityDTO.getEnvironmentIdentifier());
      }
      if (activityDTO.getServiceIdentifier() == null) {
        activityDTO.setServiceIdentifier(
            cd10ActivitySourceService.getNextGenServiceIdentifier(accountId, activityDTO.getOrgIdentifier(),
                activityDTO.getProjectIdentifier(), harness10CdAppId, harness10CdServiceId));
        runtimeValues.put(CVVerificationJobConstants.SERVICE_IDENTIFIER_KEY, activityDTO.getServiceIdentifier());
      }
    }
    String activityId = register(accountId, activityDTO);
    return CD10RegisterActivityDTO.builder()
        .activityId(activityId)
        .serviceIdentifier(activityDTO.getServiceIdentifier())
        .envIdentifier(activityDTO.getEnvironmentIdentifier())
        .build();
  }

  @Override
  public void updateActivityStatus(Activity activity) {
    ActivityVerificationSummary summary = verificationJobInstanceService.getActivityVerificationSummary(
        verificationJobInstanceService.get(activity.getVerificationJobInstanceIds()));
    if (!summary.getAggregatedStatus().equals(ActivityVerificationStatus.IN_PROGRESS)
        && !summary.getAggregatedStatus().equals(ActivityVerificationStatus.NOT_STARTED)) {
      Query<Activity> activityQuery =
          hPersistence.createQuery(Activity.class).filter(ActivityKeys.uuid, activity.getUuid());
      UpdateOperations<Activity> activityUpdateOperations =
          hPersistence.createUpdateOperations(Activity.class)
              .set(ActivityKeys.analysisStatus, summary.getAggregatedStatus())
              .set(ActivityKeys.verificationSummary, summary);
      hPersistence.update(activityQuery, activityUpdateOperations);

      log.info("Updated the status of activity {} to {}", activity.getUuid(), summary.getAggregatedStatus());
    }

    if (ActivityVerificationStatus.getFinalStates().contains(summary.getAggregatedStatus())) {
      DeploymentActivity deploymentActivity = (DeploymentActivity) activity;
      alertRuleService.processDeploymentVerification(activity.getAccountId(), activity.getOrgIdentifier(),
          activity.getProjectIdentifier(), activity.getServiceIdentifier(), activity.getEnvironmentIdentifier(),
          activity.getType(), VerificationStatus.getVerificationStatus(summary.getAggregatedStatus()),
          summary.getStartTime(), summary.getDurationMs(), deploymentActivity.getDeploymentTag());
    }
  }

  public String createActivity(Activity activity) {
    return hPersistence.save(activity);
  }

  @Override
  public List<DeploymentActivityVerificationResultDTO> getRecentDeploymentActivityVerifications(
      String accountId, String orgIdentifier, String projectIdentifier) {
    List<DeploymentGroupByTag> recentDeploymentActivities =
        getRecentDeploymentActivities(accountId, orgIdentifier, projectIdentifier);
    List<DeploymentActivityVerificationResultDTO> results = new ArrayList<>();
    recentDeploymentActivities.forEach(deploymentGroupByTag -> {
      List<String> verificationJobInstanceIds =
          getVerificationJobInstanceIds(deploymentGroupByTag.getDeploymentActivities());
      DeploymentActivityVerificationResultDTO deploymentActivityVerificationResultDTO =
          verificationJobInstanceService.getAggregatedVerificationResult(verificationJobInstanceIds);
      deploymentActivityVerificationResultDTO.setTag(deploymentGroupByTag.getDeploymentTag());
      Activity firstActivity = deploymentGroupByTag.deploymentActivities.get(0);
      String serviceName = getServiceNameFromActivity(firstActivity);
      deploymentActivityVerificationResultDTO.setServiceName(serviceName);
      deploymentActivityVerificationResultDTO.setServiceIdentifier(firstActivity.getServiceIdentifier());
      results.add(deploymentActivityVerificationResultDTO);
    });

    return results;
  }

  @Override
  public DeploymentActivityResultDTO getDeploymentActivityVerificationsByTag(String accountId, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String deploymentTag) {
    List<DeploymentActivity> deploymentActivities =
        getDeploymentActivitiesByTag(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, deploymentTag);
    DeploymentResultSummary deploymentResultSummary =
        DeploymentResultSummary.builder()
            .preProductionDeploymentVerificationJobInstanceSummaries(new ArrayList<>())
            .productionDeploymentVerificationJobInstanceSummaries(new ArrayList<>())
            .postDeploymentVerificationJobInstanceSummaries(new ArrayList<>())
            .build();
    List<String> verificationJobInstanceIds = getVerificationJobInstanceIds(deploymentActivities);

    verificationJobInstanceService.addResultsToDeploymentResultSummary(
        accountId, verificationJobInstanceIds, deploymentResultSummary);

    String serviceName = getServiceNameFromActivity(deploymentActivities.get(0));

    updateDeploymentVerificationJobInstanceSummariesWithActivityId(
        deploymentResultSummary.getPreProductionDeploymentVerificationJobInstanceSummaries(), deploymentActivities);
    updateDeploymentVerificationJobInstanceSummariesWithActivityId(
        deploymentResultSummary.getProductionDeploymentVerificationJobInstanceSummaries(), deploymentActivities);
    updateDeploymentVerificationJobInstanceSummariesWithActivityId(
        deploymentResultSummary.getPostDeploymentVerificationJobInstanceSummaries(), deploymentActivities);

    DeploymentActivityResultDTO deploymentActivityResultDTO = DeploymentActivityResultDTO.builder()
                                                                  .deploymentTag(deploymentTag)
                                                                  .serviceName(serviceName)
                                                                  .deploymentResultSummary(deploymentResultSummary)
                                                                  .build();

    Set<String> environments = collectAllEnvironments(deploymentActivityResultDTO);
    deploymentActivityResultDTO.setEnvironments(environments);

    return deploymentActivityResultDTO;
  }

  private void updateDeploymentVerificationJobInstanceSummariesWithActivityId(
      List<DeploymentVerificationJobInstanceSummary> deploymentResultSummary, List<DeploymentActivity> activities) {
    Map<String, DeploymentActivity> verificationJobInstanceIdActivityMap = new HashMap<>();
    if (activities != null) {
      activities.forEach(deploymentActivity -> {
        deploymentActivity.getVerificationJobInstanceIds().forEach(
            jobInstanceId -> verificationJobInstanceIdActivityMap.put(jobInstanceId, deploymentActivity));
      });
    }

    if (deploymentResultSummary != null) {
      deploymentResultSummary.forEach(deploymentVerificationJobInstanceSummary -> {
        DeploymentActivity activity = verificationJobInstanceIdActivityMap.get(
            deploymentVerificationJobInstanceSummary.getVerificationJobInstanceId());
        deploymentVerificationJobInstanceSummary.setActivityId(activity.getUuid());
        deploymentVerificationJobInstanceSummary.setActivityStartTime(activity.getActivityStartTime().toEpochMilli());
      });
    }
  }

  @Override
  public DeploymentVerificationJobInstanceSummary getDeploymentSummary(String activityId) {
    Activity activity = get(activityId);
    List<String> verificationJobInstanceIds = activity.getVerificationJobInstanceIds();
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        verificationJobInstanceService.getDeploymentVerificationJobInstanceSummary(verificationJobInstanceIds);
    deploymentVerificationJobInstanceSummary.setActivityId(activity.getUuid());
    deploymentVerificationJobInstanceSummary.setActivityStartTime(activity.getActivityStartTime().toEpochMilli());
    return deploymentVerificationJobInstanceSummary;
  }

  @Override
  public ActivityStatusDTO getActivityStatus(String accountId, String activityId) {
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        getDeploymentSummary(activityId);
    ActivityStatusDTO activityStatusDTO =
        ActivityStatusDTO.builder()
            .durationMs(deploymentVerificationJobInstanceSummary.getDurationMs())
            .progressPercentage(deploymentVerificationJobInstanceSummary.getProgressPercentage())
            .activityId(activityId)
            .status(deploymentVerificationJobInstanceSummary.getStatus())
            .build();
    return activityStatusDTO;
  }

  @Override
  public DeploymentActivityPopoverResultDTO getDeploymentActivityVerificationsPopoverSummary(String accountId,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String deploymentTag) {
    List<DeploymentActivity> deploymentActivities =
        getDeploymentActivitiesByTag(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, deploymentTag);
    List<String> verificationJobInstanceIds = getVerificationJobInstanceIds(deploymentActivities);
    DeploymentActivityPopoverResultDTO deploymentActivityPopoverResultDTO =
        verificationJobInstanceService.getDeploymentVerificationPopoverResult(verificationJobInstanceIds);
    deploymentActivityPopoverResultDTO.setTag(deploymentTag);
    deploymentActivityPopoverResultDTO.setServiceName(deploymentTag);
    deploymentActivityPopoverResultDTO.setServiceName(getServiceNameFromActivity(deploymentActivities.get(0)));
    return deploymentActivityPopoverResultDTO;
  }

  private Set<String> collectAllEnvironments(DeploymentActivityResultDTO deploymentActivityResultDTO) {
    Set<String> environments = new HashSet<>();
    deploymentActivityResultDTO.getDeploymentResultSummary()
        .getPreProductionDeploymentVerificationJobInstanceSummaries()
        .forEach(deploymentVerificationJobInstanceSummary
            -> environments.add(deploymentVerificationJobInstanceSummary.getEnvironmentName()));

    deploymentActivityResultDTO.getDeploymentResultSummary()
        .getProductionDeploymentVerificationJobInstanceSummaries()
        .forEach(deploymentVerificationJobInstanceSummary
            -> environments.add(deploymentVerificationJobInstanceSummary.getEnvironmentName()));

    deploymentActivityResultDTO.getDeploymentResultSummary()
        .getPostDeploymentVerificationJobInstanceSummaries()
        .forEach(deploymentVerificationJobInstanceSummary
            -> environments.add(deploymentVerificationJobInstanceSummary.getEnvironmentName()));

    return environments;
  }

  private List<String> getVerificationJobInstanceIds(List<DeploymentActivity> deploymentActivities) {
    return deploymentActivities.stream()
        .map(deploymentActivity -> deploymentActivity.getVerificationJobInstanceIds())
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }
  private List<DeploymentActivity> getDeploymentActivitiesByTag(String accountId, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String deploymentTag) {
    List<DeploymentActivity> deploymentActivities =
        (List<DeploymentActivity>) (List<?>) hPersistence
            .createQuery(Activity.class, Collections.singleton(HQuery.QueryChecks.COUNT))
            .filter(ActivityKeys.accountId, accountId)
            .filter(ActivityKeys.orgIdentifier, orgIdentifier)
            .filter(ActivityKeys.projectIdentifier, projectIdentifier)
            .filter(ActivityKeys.serviceIdentifier, serviceIdentifier)
            .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
            .filter(DeploymentActivityKeys.deploymentTag, deploymentTag)
            .asList();
    Preconditions.checkState(
        isNotEmpty(deploymentActivities), "No Deployment Activities were found for deployment tag: %s", deploymentTag);
    return deploymentActivities;
  }

  private List<DeploymentGroupByTag> getRecentDeploymentActivities(
      String accountId, String orgIdentifier, String projectIdentifier) {
    List<DeploymentActivity> activities =
        (List<DeploymentActivity>) (List<?>) hPersistence.createQuery(Activity.class, excludeAuthority)
            .filter(ActivityKeys.accountId, accountId)
            .filter(ActivityKeys.orgIdentifier, orgIdentifier)
            .filter(ActivityKeys.projectIdentifier, projectIdentifier)
            .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
            .order(Sort.descending(ActivityKeys.createdAt))
            // assumption is that the latest 5 tags will be part of last 1000 deployments
            .asList(new FindOptions().limit(1000));

    Map<BuildTagServiceIdentifier, DeploymentGroupByTag> groupByTagMap = new HashMap<>();
    List<DeploymentGroupByTag> result = new ArrayList<>();
    for (DeploymentActivity activity : activities) {
      DeploymentGroupByTag deploymentGroupByTag;
      BuildTagServiceIdentifier buildTagServiceIdentifier = BuildTagServiceIdentifier.builder()
                                                                .deploymentTag(activity.getDeploymentTag())
                                                                .serviceIdentifier(activity.getServiceIdentifier())
                                                                .build();
      if (groupByTagMap.containsKey(buildTagServiceIdentifier)) {
        deploymentGroupByTag = groupByTagMap.get(buildTagServiceIdentifier);
      } else {
        if (groupByTagMap.size() < RECENT_DEPLOYMENT_ACTIVITIES_RESULT_SIZE) {
          deploymentGroupByTag = DeploymentGroupByTag.builder()
                                     .deploymentTag(activity.getDeploymentTag())
                                     .serviceIdentifier(activity.getServiceIdentifier())
                                     .build();
          groupByTagMap.put(buildTagServiceIdentifier, deploymentGroupByTag);
          result.add(deploymentGroupByTag);
        } else {
          // ignore the tag that is not in the latest 5 tags.
          continue;
        }
      }
      deploymentGroupByTag.addDeploymentActivity(activity);
    }
    return result;
  }

  @Override
  public String getDeploymentTagFromActivity(String accountId, String verificationJobInstanceId) {
    DeploymentActivity deploymentActivity =
        (DeploymentActivity) hPersistence.createQuery(Activity.class, excludeAuthority)
            .filter(ActivityKeys.accountId, accountId)
            .filter(ActivityKeys.type, ActivityType.DEPLOYMENT)
            .field(ActivityKeys.verificationJobInstanceIds)
            .hasThisOne(verificationJobInstanceId)
            .get();
    if (deploymentActivity != null) {
      return deploymentActivity.getDeploymentTag();
    } else {
      throw new IllegalStateException("Activity not found for verificationJobInstanceId: " + verificationJobInstanceId);
    }
  }

  @Override
  public List<ActivityDashboardDTO> listActivitiesInTimeRange(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentIdentifier, String serviceIdentifier, Instant startTime,
      Instant endTime) {
    Query<Activity> activityQuery = hPersistence.createQuery(Activity.class, excludeAuthority)
                                        .filter(ActivityKeys.accountId, accountId)
                                        .filter(ActivityKeys.orgIdentifier, orgIdentifier)
                                        .filter(ActivityKeys.projectIdentifier, projectIdentifier)
                                        .field(ActivityKeys.activityStartTime)
                                        .greaterThanOrEq(startTime)
                                        .field(ActivityKeys.activityStartTime)
                                        .lessThan(endTime);

    if (isNotEmpty(environmentIdentifier)) {
      activityQuery = activityQuery.filter(ActivityKeys.environmentIdentifier, environmentIdentifier);
    }
    if (isNotEmpty(serviceIdentifier)) {
      activityQuery = activityQuery.filter(ActivityKeys.serviceIdentifier, serviceIdentifier);
    }
    List<Activity> activities = activityQuery.asList();

    List<ActivityDashboardDTO> activityDashboardDTOList = new ArrayList<>();
    if (isNotEmpty(activities)) {
      activities.forEach(activity -> {
        ActivityVerificationSummary summary = activity.getVerificationSummary();
        if (summary == null) {
          summary = verificationJobInstanceService.getActivityVerificationSummary(
              verificationJobInstanceService.get(activity.getVerificationJobInstanceIds()));
        }

        activityDashboardDTOList.add(ActivityDashboardDTO.builder()
                                         .activityType(activity.getType())
                                         .activityId(activity.getUuid())
                                         .activityName(activity.getActivityName())
                                         .environmentIdentifier(activity.getEnvironmentIdentifier())
                                         .serviceIdentifier(activity.getServiceIdentifier())
                                         .activityStartTime(activity.getActivityStartTime().toEpochMilli())
                                         .activityVerificationSummary(summary)
                                         .verificationStatus(getVerificationStatus(activity, summary))
                                         .build());
      });
    }
    return activityDashboardDTOList;
  }

  private ActivityVerificationStatus getVerificationStatus(Activity activity, ActivityVerificationSummary summary) {
    if (isEmpty(activity.getVerificationJobInstanceIds())) {
      return ActivityVerificationStatus.IGNORED;
    }
    return summary == null ? ActivityVerificationStatus.NOT_STARTED : summary.getAggregatedStatus();
  }

  @Override
  public ActivityVerificationResultDTO getActivityVerificationResult(String accountId, String activityId) {
    Preconditions.checkNotNull(activityId, "ActivityId cannot be null while trying to fetch result");
    return getResultForAnActivity(get(activityId));
  }

  private ActivityVerificationResultDTO getResultForAnActivity(Activity activity) {
    List<VerificationJobInstance> verificationJobInstances =
        verificationJobInstanceService.getNonDeploymentInstances(activity.getVerificationJobInstanceIds());

    ActivityVerificationSummary summary =
        verificationJobInstanceService.getActivityVerificationSummary(verificationJobInstances);

    if (summary != null) {
      Set<CategoryRisk> preActivityRisks =
          healthVerificationHeatMapService.getAggregatedRisk(activity.getUuid(), HealthVerificationPeriod.PRE_ACTIVITY);
      Set<CategoryRisk> postActivityRisks = healthVerificationHeatMapService.getAggregatedRisk(
          activity.getUuid(), HealthVerificationPeriod.POST_ACTIVITY);
      List<Double> postActivityValidRisks = new ArrayList<>();
      postActivityRisks.stream()
          .filter(risk -> risk.getRisk() != -1.0)
          .forEach(categoryRisk -> postActivityValidRisks.add(categoryRisk.getRisk()));

      Double overallRisk = postActivityValidRisks.size() == 0 ? -1.0 : Collections.max(postActivityValidRisks);
      return ActivityVerificationResultDTO.builder()
          .preActivityRisks(preActivityRisks)
          .postActivityRisks(postActivityRisks)
          .activityName(activity.getActivityName())
          .activityId(activity.getUuid())
          .activityStartTime(activity.getActivityStartTime().toEpochMilli())
          .activityType(activity.getType())
          .environmentIdentifier(activity.getEnvironmentIdentifier())
          .serviceIdentifier(activity.getServiceIdentifier())
          .progressPercentage(summary.getProgressPercentage())
          .status(summary.getAggregatedStatus())
          .remainingTimeMs(summary.getRemainingTimeMs())
          .endTime(activity.getActivityStartTime().toEpochMilli() + summary.getDurationMs())
          .overallRisk(overallRisk.intValue())
          .build();
    }
    return null;
  }

  @Override
  public List<ActivityVerificationResultDTO> getRecentActivityVerificationResults(
      String accountId, String orgIdentifier, String projectIdentifier, int limitCounter) {
    if (limitCounter == 0) {
      limitCounter = RECENT_DEPLOYMENT_ACTIVITIES_RESULT_SIZE;
    }

    List<Activity> activities = hPersistence.createQuery(Activity.class, excludeAuthority)
                                    .filter(ActivityKeys.accountId, accountId)
                                    .filter(ActivityKeys.orgIdentifier, orgIdentifier)
                                    .filter(ActivityKeys.projectIdentifier, projectIdentifier)
                                    .field(ActivityKeys.type)
                                    .notIn(Arrays.asList(ActivityType.DEPLOYMENT))
                                    .order(Sort.descending(ActivityKeys.activityStartTime))
                                    .field(ActivityKeys.verificationJobInstanceIds)
                                    .exists()
                                    .asList(new FindOptions().limit(limitCounter));

    if (isEmpty(activities)) {
      log.info("No recent activities found for org {}, project {}", orgIdentifier, projectIdentifier);
      return null;
    }

    List<ActivityVerificationResultDTO> activityResultList = new ArrayList<>();
    activities.forEach(activity -> {
      ActivityVerificationResultDTO resultDTO = getResultForAnActivity(activity);
      if (resultDTO != null) {
        activityResultList.add(resultDTO);
      }
    });
    return activityResultList;
  }

  private String getServiceNameFromActivity(Activity activity) {
    return nextGenService
        .getService(activity.getAccountId(), activity.getOrgIdentifier(), activity.getProjectIdentifier(),
            activity.getServiceIdentifier())
        .getName();
  }

  @Data
  @Builder
  private static class DeploymentGroupByTag {
    String deploymentTag;
    String serviceIdentifier;
    List<DeploymentActivity> deploymentActivities;

    public void addDeploymentActivity(DeploymentActivity deploymentActivity) {
      if (deploymentActivities == null) {
        deploymentActivities = new ArrayList<>();
      }
      deploymentActivities.add(deploymentActivity);
    }
  }

  public List<String> createVerificationJobInstancesForActivity(Activity activity) {
    List<VerificationJobInstance> jobInstancesToCreate = new ArrayList<>();
    List<VerificationJob> verificationJobs = new ArrayList<>();
    Map<String, Map<String, String>> runtimeDetailsMap = new HashMap<>();
    if (isEmpty(activity.getVerificationJobRuntimeDetails())) {
      // check to see if any other jobs are currently running
      List<VerificationJobInstance> runningInstances = verificationJobInstanceService.getRunningOrQueuedJobInstances(
          activity.getAccountId(), activity.getOrgIdentifier(), activity.getProjectIdentifier(),
          activity.getEnvironmentIdentifier(), activity.getServiceIdentifier(), VerificationJobType.HEALTH,
          activity.getActivityStartTime().plus(Duration.ofMinutes(HEALTH_VERIFICATION_RETRIGGER_BUFFER_MINS)));
      if (isNotEmpty(runningInstances)) {
        log.info(
            "There are verification jobs that are already running for {}, {}, {}. So we will not trigger a new one",
            activity.getProjectIdentifier(), activity.getEnvironmentIdentifier(), activity.getServiceIdentifier());
        return null;
      }
      verificationJobs.addAll(
          verificationJobService.getHealthVerificationJobs(activity.getAccountId(), activity.getOrgIdentifier(),
              activity.getProjectIdentifier(), activity.getEnvironmentIdentifier(), activity.getServiceIdentifier()));
    } else {
      activity.getVerificationJobRuntimeDetails().forEach(jobDetail -> {
        String jobIdentifier = jobDetail.getVerificationJobIdentifier();
        Preconditions.checkNotNull(jobIdentifier, "Job Identifier must be present in the jobs to trigger");
        VerificationJob verificationJob = verificationJobService.getVerificationJob(
            activity.getAccountId(), activity.getOrgIdentifier(), activity.getProjectIdentifier(), jobIdentifier);
        Preconditions.checkNotNull(verificationJob, "No Job exists for verificationJobIdentifier: '%s'", jobIdentifier);
        verificationJobs.add(verificationJob);
        if (isNotEmpty(jobDetail.getRuntimeValues())) {
          runtimeDetailsMap.put(verificationJob.getIdentifier(), jobDetail.getRuntimeValues());
        }
      });
    }

    verificationJobs.forEach(verificationJob -> {
      if (runtimeDetailsMap.containsKey(verificationJob.getIdentifier())) {
        verificationJob.resolveVerificationJob(runtimeDetailsMap.get(verificationJob.getIdentifier()));
      }
      VerificationJobInstance verificationJobInstance = fillOutCommonJobInstanceProperties(
          activity, verificationJob.resolveAdditionsFields(verificationJobInstanceService));
      validateJob(verificationJob);
      activity.fillInVerificationJobInstanceDetails(verificationJobInstance);

      jobInstancesToCreate.add(verificationJobInstance);
    });
    Preconditions.checkState(!jobInstancesToCreate.isEmpty(), "Should have at least one VerificationJobInstance");
    if (activity.deduplicateEvents()) {
      return verificationJobInstanceService.dedupCreate(jobInstancesToCreate);
    } else {
      return verificationJobInstanceService.create(jobInstancesToCreate);
    }
  }

  private void validateJob(VerificationJob verificationJob) {
    List<CVConfig> cvConfigs = verificationJobInstanceService.getCVConfigsForVerificationJob(verificationJob);
    Preconditions.checkState(isNotEmpty(cvConfigs),
        "No monitoring sources with identifiers %s defined for environment %s and service %s",
        verificationJob.getMonitoringSources(), verificationJob.getEnvIdentifier(),
        verificationJob.getServiceIdentifier());
  }

  private VerificationJobInstance fillOutCommonJobInstanceProperties(
      Activity activity, VerificationJob verificationJob) {
    return VerificationJobInstance.builder()
        .verificationJobIdentifier(verificationJob.getIdentifier())
        .accountId(activity.getAccountId())
        .executionStatus(ExecutionStatus.QUEUED)
        .deploymentStartTime(activity.getActivityStartTime())
        .resolvedJob(verificationJob)
        .build();
  }

  public Activity getActivityFromDTO(ActivityDTO activityDTO) {
    Activity activity;
    switch (activityDTO.getType()) {
      case DEPLOYMENT:
        activity = DeploymentActivity.builder().build();
        break;
      case INFRASTRUCTURE:
        activity = InfrastructureActivity.builder().build();
        break;
      case CUSTOM:
        activity = CustomActivity.builder().build();
        break;
      case KUBERNETES:
        throw new IllegalStateException("KUBERNETES events are handled by its own service");
      default:
        throw new IllegalStateException("Invalid type " + activityDTO.getType());
    }

    activity.fromDTO(activityDTO);
    return activity;
  }

  @Value
  @Builder
  private static class BuildTagServiceIdentifier {
    String deploymentTag;
    String serviceIdentifier;
  }
}
