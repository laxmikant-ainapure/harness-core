package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.data.K8sGranularUtilizationData;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.utils.DataUtils;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class K8sUtilizationGranularDataServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private DataUtils utils;

  private static final int MAX_RETRY_COUNT = 2;
  private static final int BATCH_SIZE = 500;
  static final String INSERT_STATEMENT =
      "INSERT INTO KUBERNETES_UTILIZATION_DATA (STARTTIME, ENDTIME, CPU, MEMORY, MAXCPU, MAXMEMORY,  INSTANCEID, INSTANCETYPE, CLUSTERID, ACCOUNTID, SETTINGID, STORAGEREQUESTVALUE, STORAGEUSAGEVALUE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING";
  static final String SELECT_DISTINCT_INSTANCEID =
      "SELECT DISTINCT INSTANCEID FROM KUBERNETES_UTILIZATION_DATA WHERE ACCOUNTID = '%s' AND STARTTIME >= '%s' AND STARTTIME < '%s'";
  static final String UTILIZATION_DATA_QUERY =
      "SELECT MAX(MAXCPU) as CPUUTILIZATIONMAX, MAX(MAXMEMORY) as MEMORYUTILIZATIONMAX, AVG(CPU) as CPUUTILIZATIONAVG, AVG(MEMORY) as MEMORYUTILIZATIONAVG, "
      + " SETTINGID, CLUSTERID, INSTANCEID, INSTANCETYPE FROM KUBERNETES_UTILIZATION_DATA WHERE ACCOUNTID = '%s' AND INSTANCEID IN ('%s') AND STARTTIME >= '%s' AND STARTTIME < '%s' "
      + " GROUP BY SETTINGID, CLUSTERID, INSTANCEID, INSTANCETYPE ";

  static final String UTILIZATION_DATA_QUERY_OF_INSTANCETYPE =
      "SELECT AVG(STORAGEREQUESTVALUE) as STORAGEREQUESTVALUEAVG, AVG(STORAGEUSAGEVALUE) as STORAGEUSAGEVALUEAVG, "
      + " SETTINGID, CLUSTERID, INSTANCEID, INSTANCETYPE FROM KUBERNETES_UTILIZATION_DATA WHERE ACCOUNTID = '%s' AND INSTANCETYPE = '%s' AND STARTTIME >= '%s' AND STARTTIME < '%s' "
      + " GROUP BY SETTINGID, CLUSTERID, INSTANCEID, INSTANCETYPE ";

  static final String PURGE_DATA_QUERY = "SELECT drop_chunks(interval '16 days', 'kubernetes_utilization_data')";

  public boolean create(List<K8sGranularUtilizationData> k8sGranularUtilizationDataList) {
    if (k8sGranularUtilizationDataList.isEmpty()) {
      return true;
    }

    boolean successfulInsert = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulInsert && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(INSERT_STATEMENT)) {
          int index = 0;
          for (K8sGranularUtilizationData k8sGranularUtilizationData : k8sGranularUtilizationDataList) {
            updateInsertStatement(statement, k8sGranularUtilizationData);
            statement.addBatch();
            index++;

            if (index % BATCH_SIZE == 0 || index == k8sGranularUtilizationDataList.size()) {
              statement.executeBatch();
            }
          }
          successfulInsert = true;
        } catch (SQLException e) {
          log.error("Failed to save K8s Utilization data,[{}],retryCount=[{}], Exception: ",
              k8sGranularUtilizationDataList, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Not processing K8s Utilization data:[{}]", k8sGranularUtilizationDataList);
    }
    return successfulInsert;
  }

  public boolean purgeOldKubernetesUtilData() {
    boolean purgedK8sUtilData = false;
    log.info("Purging old k8s util data !!");
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;

      while (retryCount < MAX_RETRY_COUNT && !purgedK8sUtilData) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             Statement statement = connection.createStatement()) {
          statement.execute(PURGE_DATA_QUERY);
          purgedK8sUtilData = true;
        } catch (SQLException e) {
          log.error("Failed to execute query=[{}]", PURGE_DATA_QUERY, e);
          retryCount++;
        }
      }
    }
    return purgedK8sUtilData;
  }

  private void updateInsertStatement(PreparedStatement statement, K8sGranularUtilizationData k8sGranularUtilizationData)
      throws SQLException {
    statement.setTimestamp(
        1, new Timestamp(k8sGranularUtilizationData.getStartTimestamp()), utils.getDefaultCalendar());
    statement.setTimestamp(2, new Timestamp(k8sGranularUtilizationData.getEndTimestamp()), utils.getDefaultCalendar());
    statement.setDouble(3, k8sGranularUtilizationData.getCpu());
    statement.setDouble(4, k8sGranularUtilizationData.getMemory());
    statement.setDouble(5, k8sGranularUtilizationData.getMaxCpu());
    statement.setDouble(6, k8sGranularUtilizationData.getMaxMemory());
    statement.setString(7, k8sGranularUtilizationData.getInstanceId());
    statement.setString(8, k8sGranularUtilizationData.getInstanceType());
    statement.setString(9, k8sGranularUtilizationData.getClusterId());
    statement.setString(10, k8sGranularUtilizationData.getAccountId());
    statement.setString(11, k8sGranularUtilizationData.getSettingId());
    statement.setDouble(12, k8sGranularUtilizationData.getStorageRequestValue());
    statement.setDouble(13, k8sGranularUtilizationData.getStorageUsageValue());
  }

  public List<String> getDistinctInstantIds(String accountId, long startDate, long endDate) {
    ResultSet resultSet = null;
    List<String> instanceIdsList = new ArrayList<>();

    String query = String.format(
        SELECT_DISTINCT_INSTANCEID, accountId, Instant.ofEpochMilli(startDate), Instant.ofEpochMilli(endDate));

    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        instanceIdsList.add(resultSet.getString("INSTANCEID"));
      }
      return instanceIdsList;
    } catch (SQLException e) {
      log.error("Error while fetching instanceIds List : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  public Map<String, InstanceUtilizationData> getAggregatedUtilizationData(
      String accountId, List<String> distinctIdsList, long startDate, long endDate) {
    ResultSet resultSet = null;
    String query = String.format(UTILIZATION_DATA_QUERY, accountId, String.join("','", distinctIdsList),
        Instant.ofEpochMilli(startDate), Instant.ofEpochMilli(endDate));

    Map<String, InstanceUtilizationData> instanceUtilizationDataMap = new HashMap<>();
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        String instanceType = resultSet.getString("INSTANCETYPE");
        String instanceId = resultSet.getString("INSTANCEID");
        String clusterId = resultSet.getString("CLUSTERID");
        String settingId = resultSet.getString("SETTINGID");
        double cpuMax = resultSet.getDouble("CPUUTILIZATIONMAX");
        double memMax = resultSet.getDouble("MEMORYUTILIZATIONMAX");
        double cpuAvg = resultSet.getDouble("CPUUTILIZATIONAVG");
        double memAvg = resultSet.getDouble("MEMORYUTILIZATIONAVG");

        instanceUtilizationDataMap.put(instanceId,
            InstanceUtilizationData.builder()
                .accountId(accountId)
                .clusterId(clusterId)
                .settingId(settingId)
                .instanceType(instanceType)
                .instanceId(instanceId)
                .cpuUtilizationMax(cpuMax)
                .cpuUtilizationAvg(cpuAvg)
                .memoryUtilizationMax(memMax)
                .memoryUtilizationAvg(memAvg)
                .build());
      }
      return instanceUtilizationDataMap;
    } catch (SQLException e) {
      log.error("Error while fetching Aggregated Utilization Data : exception ", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  public Map<String, InstanceUtilizationData> getAggregatedUtilizationDataOfType(
      String accountId, InstanceType instanceType, long startDate, long endDate) {
    ResultSet resultSet = null;
    String query = String.format(UTILIZATION_DATA_QUERY_OF_INSTANCETYPE, accountId, instanceType.name(),
        Instant.ofEpochMilli(startDate), Instant.ofEpochMilli(endDate));

    Map<String, InstanceUtilizationData> instanceUtilizationDataMap = new HashMap<>();

    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        String instanceId = resultSet.getString("INSTANCEID");
        String clusterId = resultSet.getString("CLUSTERID");
        String settingId = resultSet.getString("SETTINGID");
        double storageUsageAvg = resultSet.getDouble("STORAGEUSAGEVALUEAVG");
        double storageRequestAvg = resultSet.getDouble("STORAGEREQUESTVALUEAVG");

        instanceUtilizationDataMap.put(instanceId,
            InstanceUtilizationData.builder()
                .accountId(accountId)
                .clusterId(clusterId)
                .settingId(settingId)
                .instanceType(instanceType.name())
                .instanceId(instanceId)
                .storageUsageAvgValue(storageUsageAvg)
                .storageRequestAvgValue(storageRequestAvg)
                .build());
      }
      return instanceUtilizationDataMap;
    } catch (SQLException e) {
      log.error("Error while fetching Aggregated Utilization Data : exception ", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }
}
