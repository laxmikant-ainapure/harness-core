package io.harness.cvng.dashboard.services.impl;

import static io.harness.cvng.core.utils.DateTimeUtils.roundDownTo5MinBoundary;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.getHeatMapResolution;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.alert.services.api.AlertRuleService;
import io.harness.cvng.analysis.services.api.AnalysisService;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.dashboard.beans.CategoryRisksDTO;
import io.harness.cvng.dashboard.beans.CategoryRisksDTO.CategoryRisk;
import io.harness.cvng.dashboard.beans.EnvServiceRiskDTO;
import io.harness.cvng.dashboard.beans.EnvServiceRiskDTO.ServiceRisk;
import io.harness.cvng.dashboard.beans.EnvToServicesDTO;
import io.harness.cvng.dashboard.beans.HeatMapDTO;
import io.harness.cvng.dashboard.beans.RiskSummaryPopoverDTO;
import io.harness.cvng.dashboard.beans.RiskSummaryPopoverDTO.RiskSummaryPopoverDTOBuilder;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapKeys;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapRisk.HeatMapRiskKeys;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.utils.CVNGParallelExecutor;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.DBCollectionUpdateOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.mongodb.morphia.UpdateOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

public class HeatMapServiceImpl implements HeatMapService {
  private static final int RISK_TIME_BUFFER_MINS = 15;
  public static final int RISK_THRESHOLD = 50;
  public static final int POPOVER_NUMBER_OF_MAX_RISKS = 3;
  @Inject private HPersistence hPersistence;
  @Inject private CVConfigService cvConfigService;
  @Inject private Clock clock;
  @Inject private AnalysisService analysisService;
  @Inject private AlertRuleService alertRuleService;
  @Inject private ExecutorService defaultExecutorService;
  @Inject private CVNGParallelExecutor cvngParallelExecutor;
  @Inject private NextGenService nextGenService;

  @Override
  public void updateRiskScore(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, String envIdentifier, CVConfig cvConfig, CVMonitoringCategory category,
      Instant timeStamp, double riskScore) {
    List<Callable<Void>> callables = new ArrayList<>();
    // update for service/env
    callables.add(() -> {
      updateRiskScore(category, accountId, orgIdentifier, projectIdentifier, serviceIdentifier, envIdentifier,
          timeStamp, riskScore);
      return null;
    });

    if (cvConfigService.isProductionConfig(cvConfig)) {
      // update for env
      callables.add(() -> {
        updateRiskScore(
            category, accountId, orgIdentifier, projectIdentifier, null, envIdentifier, timeStamp, riskScore);
        return null;
      });

      // update for project
      callables.add(() -> {
        updateRiskScore(category, accountId, orgIdentifier, projectIdentifier, null, null, timeStamp, riskScore);
        return null;
      });
    }

    cvngParallelExecutor.executeParallel(callables);
    defaultExecutorService.execute(()
                                       -> alertRuleService.processRiskScore(accountId, orgIdentifier, projectIdentifier,
                                           serviceIdentifier, envIdentifier, category, timeStamp, riskScore));
  }

  private void updateRiskScore(CVMonitoringCategory category, String accountId, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String envIdentifier, Instant timeStamp, double riskScore) {
    UpdateOptions options = new UpdateOptions();
    options.upsert(true);
    for (HeatMapResolution heatMapResolution : HeatMapResolution.values()) {
      Instant bucketStartTime = getBoundaryOfResolution(timeStamp, heatMapResolution.getBucketSize());
      Instant bucketEndTime = bucketStartTime.plusMillis(heatMapResolution.getBucketSize().toMillis());
      Instant heatMapStartTime = getBoundaryOfResolution(timeStamp, heatMapResolution.getResolution());
      Instant heatMapEndTime = heatMapStartTime.plusMillis(heatMapResolution.getResolution().toMillis());

      Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class)
                                        .filter(HeatMapKeys.accountId, accountId)
                                        .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                        .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                        .filter(HeatMapKeys.serviceIdentifier, serviceIdentifier)
                                        .filter(HeatMapKeys.envIdentifier, envIdentifier)
                                        .filter(HeatMapKeys.category, category)
                                        .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                        .filter(HeatMapKeys.heatMapBucketStartTime, bucketStartTime)
                                        .filter(HeatMapKeys.heatMapBucketEndTime, bucketEndTime);

      // first create the heatmap record if it doesn't exists
      hPersistence.getDatastore(HeatMap.class)
          .update(heatMapQuery,
              hPersistence.createUpdateOperations(HeatMap.class)
                  .setOnInsert(HeatMapKeys.accountId, accountId)
                  .setOnInsert(HeatMapKeys.validUntil, HeatMap.builder().build().getValidUntil())
                  .addToSet(HeatMapKeys.heatMapRisks,
                      HeatMapRisk.builder()
                          .riskScore(riskScore)
                          .startTime(heatMapStartTime)
                          .endTime(heatMapEndTime)
                          .build()),
              options);

      DBCollectionUpdateOptions arrayFilterOptions = new DBCollectionUpdateOptions();
      arrayFilterOptions.upsert(true);
      arrayFilterOptions.multi(false);
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put("elem." + HeatMapRiskKeys.startTime, heatMapStartTime);
      filterMap.put("elem." + HeatMapRiskKeys.endTime, heatMapEndTime);
      filterMap.put("elem." + HeatMapRiskKeys.riskScore, new BasicDBObject("$lt", riskScore));
      arrayFilterOptions.arrayFilters(Lists.newArrayList(new BasicDBObject(filterMap)));
      hPersistence.getCollection(HeatMap.class)
          .update(heatMapQuery.getQueryObject(),
              new BasicDBObject("$set",
                  new BasicDBObject(HeatMapKeys.heatMapRisks + ".$[elem]." + HeatMapRiskKeys.riskScore, riskScore)),
              arrayFilterOptions);
    }
  }

  @Override
  public Map<CVMonitoringCategory, SortedSet<HeatMapDTO>> getHeatMap(String accountId, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String envIdentifier, Instant startTime, Instant endTime) {
    Map<CVMonitoringCategory, SortedSet<HeatMapDTO>> heatMaps = new HashMap<>();
    HeatMapResolution heatMapResolution = getHeatMapResolution(startTime, endTime);
    Instant startTimeBoundary = getBoundaryOfResolution(startTime, heatMapResolution.getResolution());
    Instant endTimeBoundary = getBoundaryOfResolution(endTime, heatMapResolution.getResolution());

    Set<CVMonitoringCategory> cvMonitoringCategories = cvConfigService.getAvailableCategories(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier);
    for (CVMonitoringCategory category : cvMonitoringCategories) {
      Map<Instant, HeatMapDTO> heatMapsFromDB = getHeatMapsFromDB(accountId, orgIdentifier, projectIdentifier,
          serviceIdentifier, envIdentifier, category, startTime, endTime, heatMapResolution);

      SortedSet<HeatMapDTO> heatMapDTOS = new TreeSet<>();
      for (long timeStampMs = startTimeBoundary.toEpochMilli(); timeStampMs <= endTimeBoundary.toEpochMilli();
           timeStampMs += heatMapResolution.getResolution().toMillis()) {
        if (heatMapsFromDB.containsKey(Instant.ofEpochMilli(timeStampMs))) {
          heatMapDTOS.add(heatMapsFromDB.get(Instant.ofEpochMilli(timeStampMs)));
          continue;
        }

        heatMapDTOS.add(HeatMapDTO.builder()
                            .startTime(timeStampMs)
                            .endTime(timeStampMs + heatMapResolution.getResolution().toMillis())
                            .build());
      }
      heatMaps.put(category, heatMapDTOS);
    }
    return heatMaps;
  }

  @Override
  public List<EnvServiceRiskDTO> getEnvServiceRiskScores(
      String accountId, String orgIdentifier, String projectIdentifier) {
    List<EnvToServicesDTO> envToServicesDTOS =
        cvConfigService.getEnvToServicesList(accountId, orgIdentifier, projectIdentifier);
    List<Callable<List<EnvServiceRiskDTO>>> callables = new ArrayList<>();
    envToServicesDTOS.forEach(envToServicesDTO -> callables.add(() -> {
      List<ServiceRisk> serviceRisks = new ArrayList<>();
      List<EnvServiceRiskDTO> riskDTOS = new ArrayList<>();
      envToServicesDTO.getServices().forEach(service -> {
        CategoryRisksDTO categoryRisk = getCategoryRiskScoresForSpecificServiceEnv(accountId, orgIdentifier,
            projectIdentifier, service.getIdentifier(), envToServicesDTO.getEnvironment().getIdentifier());

        if (categoryRisk != null && isNotEmpty(categoryRisk.getCategoryRisks())) {
          Integer risk = Collections.max(
              categoryRisk.getCategoryRisks().stream().map(CategoryRisk::getRisk).collect(Collectors.toList()));
          serviceRisks.add(ServiceRisk.builder()
                               .serviceIdentifier(service.getIdentifier())
                               .serviceName(service.getName())
                               .risk(risk)
                               .build());
        }
      });
      if (isNotEmpty(serviceRisks)) {
        Collections.sort(serviceRisks, Comparator.comparing(ServiceRisk::getServiceName));
        riskDTOS.add(EnvServiceRiskDTO.builder()
                         .envIdentifier(envToServicesDTO.getEnvironment().getIdentifier())
                         .envName(envToServicesDTO.getEnvironment().getName())
                         .orgIdentifier(orgIdentifier)
                         .projectIdentifier(projectIdentifier)
                         .serviceRisks(serviceRisks)
                         .build());
      }
      return riskDTOS;
    }));
    List<List<EnvServiceRiskDTO>> envDTOsList = cvngParallelExecutor.executeParallel(callables);
    List<EnvServiceRiskDTO> envServiceRiskDTOList = new ArrayList<>();
    envDTOsList.forEach(envServiceRiskDTOS -> envServiceRiskDTOList.addAll(envServiceRiskDTOS));
    Collections.sort(envServiceRiskDTOList, Comparator.comparing(EnvServiceRiskDTO::getEnvName));
    return envServiceRiskDTOList;
  }

  @Override
  public RiskSummaryPopoverDTO getRiskSummaryPopover(String accountId, String orgIdentifier, String projectIdentifier,
      Instant endTime, @Nullable String serviceIdentifier, @Nullable CVMonitoringCategory category) {
    List<EnvToServicesDTO> envToServicesDTOS =
        cvConfigService.getEnvToServicesList(accountId, orgIdentifier, projectIdentifier);
    Map<String, Set<String>> envToServicesMap = new HashMap<>();

    envToServicesDTOS.forEach(envToServicesDTO -> {
      if (envToServicesDTO.getEnvironment().getType().equals(EnvironmentType.Production)) {
        Set<String> services =
            envToServicesDTO.getServices().stream().map(ServiceResponseDTO::getIdentifier).collect(Collectors.toSet());
        if (serviceIdentifier != null) {
          services = services.stream()
                         .filter(serviceIdentifierInSet -> serviceIdentifier.equals(serviceIdentifierInSet))
                         .collect(Collectors.toSet());
        }
        if (!services.isEmpty()) {
          envToServicesMap.put(envToServicesDTO.getEnvironment().getIdentifier(), services);
        }
      }
    });

    List<EnvServiceRiskDTO> envServiceRiskDTOList = new ArrayList<>();
    envToServicesMap.forEach((envIdentifier, serviceSet) -> {
      PriorityQueue<ServiceRisk> serviceRisks = new PriorityQueue<>();
      int maxRisk[] = new int[] {Integer.MIN_VALUE};
      serviceSet.forEach(service -> {
        CategoryRisksDTO categoryRisk = getCategoryRiskScoresForSpecificServiceEnv(
            accountId, orgIdentifier, projectIdentifier, service, envIdentifier, category, endTime);

        if (categoryRisk != null && isNotEmpty(categoryRisk.getCategoryRisks())) {
          Integer risk = Collections.max(
              categoryRisk.getCategoryRisks().stream().map(CategoryRisk::getRisk).collect(Collectors.toList()));
          if (risk >= RISK_THRESHOLD) {
            serviceRisks.add(ServiceRisk.builder().serviceIdentifier(service).risk(risk).build());
          }
          if (serviceRisks.size() > POPOVER_NUMBER_OF_MAX_RISKS) {
            serviceRisks.remove();
          }
          maxRisk[0] = Math.max(maxRisk[0], risk);
        }
      });
      if (isNotEmpty(serviceRisks)) {
        envServiceRiskDTOList.add(EnvServiceRiskDTO.builder()
                                      .envIdentifier(envIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .risk(maxRisk[0])
                                      .serviceRisks(serviceRisks)
                                      .build());
      }
    });

    RiskSummaryPopoverDTOBuilder riskSummaryPopoverBuilder = RiskSummaryPopoverDTO.builder().category(category);
    envServiceRiskDTOList.forEach(envServiceRiskDTO -> {
      RiskSummaryPopoverDTO.EnvSummary.EnvSummaryBuilder envSummaryBuilder =
          RiskSummaryPopoverDTO.EnvSummary.builder()
              .envIdentifier(envServiceRiskDTO.getEnvIdentifier())
              .envName(nextGenService
                           .getEnvironment(accountId, envServiceRiskDTO.getOrgIdentifier(),
                               envServiceRiskDTO.getProjectIdentifier(), envServiceRiskDTO.getEnvIdentifier())
                           .getName())
              .riskScore(envServiceRiskDTO.getRisk());
      envServiceRiskDTO.getServiceRisks().forEach(serviceRisk -> {
        List<RiskSummaryPopoverDTO.AnalysisRisk> analysisRisk =
            analysisService.getTop3AnalysisRisks(accountId, orgIdentifier, projectIdentifier,
                serviceRisk.getServiceIdentifier(), endTime.minus(Duration.ofMinutes(RISK_TIME_BUFFER_MINS)), endTime);
        envSummaryBuilder.addServiceSummary(
            RiskSummaryPopoverDTO.ServiceSummary.builder()
                .risk(serviceRisk.getRisk())
                .serviceIdentifier(serviceRisk.getServiceIdentifier())
                .analysisRisks(analysisRisk)
                .serviceName(
                    nextGenService
                        .getService(accountId, orgIdentifier, projectIdentifier, serviceRisk.getServiceIdentifier())
                        .getName())
                .build());
      });
      riskSummaryPopoverBuilder.addEnvSummary(envSummaryBuilder.build());
    });
    return riskSummaryPopoverBuilder.build();
  }

  @Override
  public CategoryRisksDTO getCategoryRiskScores(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, String serviceIdentifier, String envIdentifier) {
    if (isNotEmpty(serviceIdentifier) && isEmpty(envIdentifier)) {
      throw new UnsupportedOperationException("Illeagal state in getCategoryRiskScores. EnvIdentifier is null but"
          + "serviceIdentifier is not null");
    }

    if (isEmpty(envIdentifier) && isEmpty(serviceIdentifier)) {
      serviceIdentifier = null;
      envIdentifier = null;
    } else if (isEmpty(serviceIdentifier)) {
      serviceIdentifier = null;
    }
    return getCategoryRiskScoresForSpecificServiceEnv(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, envIdentifier);
  }

  private CategoryRisksDTO getCategoryRiskScoresForSpecificServiceEnv(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, String serviceIdentifier,
      String envIdentifier) {
    return getCategoryRiskScoresForSpecificServiceEnv(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, envIdentifier, null, null);
  }
  private CategoryRisksDTO getCategoryRiskScoresForSpecificServiceEnv(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, String serviceIdentifier, String envIdentifier,
      @Nullable CVMonitoringCategory cvMonitoringCategory, @Nullable Instant endTime) {
    HeatMapResolution heatMapResolution = HeatMapResolution.FIVE_MIN;
    Map<CVMonitoringCategory, Integer> categoryScoreMap = new HashMap<>();
    Instant roundedDownTime = roundDownTo5MinBoundary(clock.instant());

    Set<CVMonitoringCategory> cvMonitoringCategories;
    if (cvMonitoringCategory != null) {
      cvMonitoringCategories = Collections.singleton(cvMonitoringCategory);
    } else {
      cvMonitoringCategories = cvConfigService.getAvailableCategories(
          accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier);
    }
    List<CategoryRisk> categoryRiskList = new ArrayList<>();
    Instant latestAnalysisTime = Instant.MIN;
    for (CVMonitoringCategory category : cvMonitoringCategories) {
      Query<HeatMap> heatMapQuery = hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                        .filter(HeatMapKeys.accountId, accountId)
                                        .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                        .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                        .filter(HeatMapKeys.serviceIdentifier, serviceIdentifier)
                                        .filter(HeatMapKeys.envIdentifier, envIdentifier)
                                        .filter(HeatMapKeys.category, category)
                                        .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                        .order(Sort.descending(HeatMapKeys.heatMapBucketEndTime));
      if (endTime != null) {
        heatMapQuery.field(HeatMapKeys.heatMapBucketStartTime).lessThan(endTime);
      }
      HeatMap latestHeatMap = heatMapQuery.get();

      if (latestHeatMap != null) {
        SortedSet<HeatMapRisk> risks = new TreeSet<>(latestHeatMap.getHeatMapRisks());
        if (risks.last().getEndTime().isAfter(roundedDownTime.minus(RISK_TIME_BUFFER_MINS, ChronoUnit.MINUTES))) {
          Double risk = risks.last().getRiskScore() * 100;
          categoryRiskList.add(CategoryRisk.builder().category(category).risk(risk.intValue()).build());
          categoryScoreMap.put(category, risk.intValue());
          if (risks.last().getEndTime().isAfter(latestAnalysisTime)) {
            latestAnalysisTime = risks.last().getEndTime();
          }
        }
      }
    }
    Arrays.asList(CVMonitoringCategory.values()).forEach(category -> {
      if (!categoryScoreMap.containsKey(category)) {
        categoryRiskList.add(CategoryRisk.builder().category(category).risk(-1).build());
      }
    });

    return CategoryRisksDTO.builder()
        .categoryRisks(categoryRiskList)
        .endTimeEpoch(
            latestAnalysisTime.equals(Instant.MIN) ? roundedDownTime.toEpochMilli() : latestAnalysisTime.toEpochMilli())
        .startTimeEpoch(latestAnalysisTime.equals(Instant.MIN)
                ? roundedDownTime.minus(HeatMapResolution.FIFTEEN_MINUTES.getResolution()).toEpochMilli()
                : latestAnalysisTime.minus(heatMapResolution.getResolution()).toEpochMilli())
        .build();
  }

  private Map<Instant, HeatMapDTO> getHeatMapsFromDB(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, String envIdentifier, CVMonitoringCategory category, Instant startTime, Instant endTime,
      HeatMapResolution heatMapResolution) {
    Instant startTimeBucketBoundary = getBoundaryOfResolution(startTime, heatMapResolution.getBucketSize());
    Instant endTimeBucketBoundary = getBoundaryOfResolution(endTime, heatMapResolution.getBucketSize());
    Map<Instant, HeatMapDTO> heatMapDTOS = new HashMap<>();
    try (HIterator<HeatMap> heatMapRecords =
             new HIterator<>(hPersistence.createQuery(HeatMap.class, excludeAuthority)
                                 .filter(HeatMapKeys.accountId, accountId)
                                 .filter(HeatMapKeys.orgIdentifier, orgIdentifier)
                                 .filter(HeatMapKeys.projectIdentifier, projectIdentifier)
                                 .filter(HeatMapKeys.serviceIdentifier, serviceIdentifier)
                                 .filter(HeatMapKeys.envIdentifier, envIdentifier)
                                 .filter(HeatMapKeys.category, category)
                                 .filter(HeatMapKeys.heatMapResolution, heatMapResolution)
                                 .field(HeatMapKeys.heatMapBucketStartTime)
                                 .greaterThanOrEq(startTimeBucketBoundary)
                                 .field(HeatMapKeys.heatMapBucketStartTime)
                                 .lessThanOrEq(endTimeBucketBoundary)
                                 .fetch())) {
      while (heatMapRecords.hasNext()) {
        HeatMap heatMap = heatMapRecords.next();
        heatMap.getHeatMapRisks()
            .stream()
            .filter(heatMapRisk
                -> heatMapRisk.getStartTime().compareTo(startTime) >= 0
                    && heatMapRisk.getStartTime().compareTo(endTime) <= 0)
            .forEach(heatMapRisk
                -> heatMapDTOS.put(heatMapRisk.getStartTime(),
                    HeatMapDTO.builder()
                        .startTime(heatMapRisk.getStartTime().toEpochMilli())
                        .endTime(heatMapRisk.getEndTime().toEpochMilli())
                        .riskScore(heatMapRisk.getRiskScore())
                        .build()));
      }
    }
    return heatMapDTOS;
  }

  private Instant getBoundaryOfResolution(Instant input, Duration resolution) {
    long timeStamp = input.toEpochMilli();
    return Instant.ofEpochMilli(timeStamp - (timeStamp % resolution.toMillis()));
  }
}
