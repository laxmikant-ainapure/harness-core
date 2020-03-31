package io.harness.batch.processing.billing.timeseries.service.impl;

import com.google.inject.Singleton;

import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.ccm.ActualIdleCostWriterData;
import io.harness.timescaledb.TimeScaleDBService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.wings.graphql.datafetcher.DataFetcherUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

@Service
@Singleton
@Slf4j
public class BillingDataServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private DataFetcherUtils utils;

  private static final int MAX_RETRY_COUNT = 5;
  static final String INSERT_STATEMENT =
      "INSERT INTO BILLING_DATA (STARTTIME, ENDTIME, ACCOUNTID, INSTANCETYPE, BILLINGACCOUNTID, BILLINGAMOUNT, CPUBILLINGAMOUNT, MEMORYBILLINGAMOUNT, USAGEDURATIONSECONDS, INSTANCEID, CLUSTERNAME, CLUSTERID, SETTINGID,  SERVICEID, APPID, CLOUDPROVIDERID, ENVID, CPUUNITSECONDS, MEMORYMBSECONDS, PARENTINSTANCEID, REGION, LAUNCHTYPE, CLUSTERTYPE, CLOUDPROVIDER, WORKLOADNAME, WORKLOADTYPE, NAMESPACE, CLOUDSERVICENAME, TASKID, IDLECOST, CPUIDLECOST, MEMORYIDLECOST, MAXCPUUTILIZATION, MAXMEMORYUTILIZATION, AVGCPUUTILIZATION, AVGMEMORYUTILIZATION, SYSTEMCOST, CPUSYSTEMCOST, MEMORYSYSTEMCOST, ACTUALIDLECOST, CPUACTUALIDLECOST, MEMORYACTUALIDLECOST, UNALLOCATEDCOST, CPUUNALLOCATEDCOST, MEMORYUNALLOCATEDCOST ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  static final String UPDATE_STATEMENT =
      "UPDATE BILLING_DATA SET ACTUALIDLECOST = ?, CPUACTUALIDLECOST = ?, MEMORYACTUALIDLECOST = ?, UNALLOCATEDCOST = ?, CPUUNALLOCATEDCOST = ?, MEMORYUNALLOCATEDCOST = ? WHERE ACCOUNTID = ? AND CLUSTERID = ? AND INSTANCEID = ? AND STARTTIME = ?";

  public boolean create(InstanceBillingData instanceBillingData) {
    boolean successfulInsert = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulInsert && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(INSERT_STATEMENT)) {
          updateInsertStatement(statement, instanceBillingData);
          statement.execute();
          logger.debug("Prepared Statement in BillingDataServiceImpl: {} ", statement);
          successfulInsert = true;
        } catch (SQLException e) {
          logger.error(
              "Failed to save instance data,[{}],retryCount=[{}], Exception: ", instanceBillingData, retryCount, e);
          retryCount++;
        }
      }
    } else {
      logger.warn("Not processing instance billing data:[{}]", instanceBillingData);
    }
    return successfulInsert;
  }

  public boolean update(ActualIdleCostWriterData actualIdleCostWriterData) {
    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;

      while (!successfulUpdate && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(UPDATE_STATEMENT)) {
          updateBillingDataUpdateStatement(statement, actualIdleCostWriterData);
          logger.debug("Prepared Statement in BillingDataServiceImpl for actual idle cost: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          logger.error("Failed to update actual idle cost data,[{}],retryCount=[{}], Exception: ",
              actualIdleCostWriterData, retryCount, e);
          retryCount++;
        }
      }
    } else {
      logger.warn("Not processing actual idle cost data:[{}]", actualIdleCostWriterData);
    }
    return successfulUpdate;
  }

  void updateBillingDataUpdateStatement(PreparedStatement statement, ActualIdleCostWriterData actualIdleCostWriterData)
      throws SQLException {
    statement.setBigDecimal(1, actualIdleCostWriterData.getActualIdleCost());
    statement.setBigDecimal(2, actualIdleCostWriterData.getCpuActualIdleCost());
    statement.setBigDecimal(3, actualIdleCostWriterData.getMemoryActualIdleCost());
    statement.setBigDecimal(4, actualIdleCostWriterData.getUnallocatedCost());
    statement.setBigDecimal(5, actualIdleCostWriterData.getCpuUnallocatedCost());
    statement.setBigDecimal(6, actualIdleCostWriterData.getMemoryUnallocatedCost());
    statement.setString(7, actualIdleCostWriterData.getAccountId());
    statement.setString(8, actualIdleCostWriterData.getClusterId());
    statement.setString(9, actualIdleCostWriterData.getInstanceId());
    statement.setTimestamp(10, new Timestamp(actualIdleCostWriterData.getStartTime()), utils.getDefaultCalendar());
  }

  void updateInsertStatement(PreparedStatement statement, InstanceBillingData instanceBillingData) throws SQLException {
    statement.setTimestamp(1, new Timestamp(instanceBillingData.getStartTimestamp()), utils.getDefaultCalendar());
    statement.setTimestamp(2, new Timestamp(instanceBillingData.getEndTimestamp()), utils.getDefaultCalendar());
    statement.setString(3, instanceBillingData.getAccountId());
    statement.setString(4, instanceBillingData.getInstanceType());
    statement.setString(5, instanceBillingData.getBillingAccountId());
    statement.setBigDecimal(6, instanceBillingData.getBillingAmount());
    statement.setBigDecimal(7, instanceBillingData.getCpuBillingAmount());
    statement.setBigDecimal(8, instanceBillingData.getMemoryBillingAmount());
    statement.setDouble(9, instanceBillingData.getUsageDurationSeconds());
    statement.setString(10, instanceBillingData.getInstanceId());
    statement.setString(11, instanceBillingData.getClusterName());
    statement.setString(12, instanceBillingData.getClusterId());
    statement.setString(13, instanceBillingData.getSettingId());
    statement.setString(14, instanceBillingData.getServiceId());
    statement.setString(15, instanceBillingData.getAppId());
    statement.setString(16, instanceBillingData.getCloudProviderId());
    statement.setString(17, instanceBillingData.getEnvId());
    statement.setDouble(18, instanceBillingData.getCpuUnitSeconds());
    statement.setDouble(19, instanceBillingData.getMemoryMbSeconds());
    statement.setString(20, instanceBillingData.getParentInstanceId());
    statement.setString(21, instanceBillingData.getRegion());
    statement.setString(22, instanceBillingData.getLaunchType());
    statement.setString(23, instanceBillingData.getClusterType());
    statement.setString(24, instanceBillingData.getCloudProvider());
    statement.setString(25, instanceBillingData.getWorkloadName());
    statement.setString(26, instanceBillingData.getWorkloadType());
    statement.setString(27, instanceBillingData.getNamespace());
    statement.setString(28, instanceBillingData.getCloudServiceName());
    statement.setString(29, instanceBillingData.getTaskId());
    statement.setBigDecimal(30, instanceBillingData.getIdleCost());
    statement.setBigDecimal(31, instanceBillingData.getCpuIdleCost());
    statement.setBigDecimal(32, instanceBillingData.getMemoryIdleCost());
    statement.setDouble(33, instanceBillingData.getMaxCpuUtilization());
    statement.setDouble(34, instanceBillingData.getMaxMemoryUtilization());
    statement.setDouble(35, instanceBillingData.getAvgCpuUtilization());
    statement.setDouble(36, instanceBillingData.getAvgMemoryUtilization());
    statement.setBigDecimal(37, instanceBillingData.getSystemCost());
    statement.setBigDecimal(38, instanceBillingData.getCpuSystemCost());
    statement.setBigDecimal(39, instanceBillingData.getMemorySystemCost());
    statement.setBigDecimal(40, instanceBillingData.getActualIdleCost());
    statement.setBigDecimal(41, instanceBillingData.getCpuActualIdleCost());
    statement.setBigDecimal(42, instanceBillingData.getMemoryActualIdleCost());
    statement.setBigDecimal(43, instanceBillingData.getUnallocatedCost());
    statement.setBigDecimal(44, instanceBillingData.getCpuUnallocatedCost());
    statement.setBigDecimal(45, instanceBillingData.getMemoryUnallocatedCost());
  }
}
