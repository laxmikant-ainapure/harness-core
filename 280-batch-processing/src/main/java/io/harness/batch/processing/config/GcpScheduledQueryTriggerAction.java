package io.harness.batch.processing.config;

import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.billing.dao.BillingDataPipelineRecordDao;
import io.harness.ccm.billing.dao.CloudBillingTransferRunDao;
import io.harness.ccm.billing.entities.BillingDataPipelineRecord;
import io.harness.ccm.billing.entities.CloudBillingTransferRun;
import io.harness.ccm.billing.entities.TransferJobRunState;
import io.harness.ccm.config.GcpOrganization;
import io.harness.ccm.config.GcpOrganizationDao;

import com.google.cloud.bigquery.datatransfer.v1.TransferRun;
import com.google.cloud.bigquery.datatransfer.v1.TransferState;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Singleton
public class GcpScheduledQueryTriggerAction {
  @Autowired BillingDataPipelineService billingDataPipelineService;
  @Autowired BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired CloudBillingTransferRunDao cloudBillingTransferRunDao;
  @Autowired GcpOrganizationDao gcpOrganizationDao;

  public RepeatStatus execute(String accountId) {
    List<CloudBillingTransferRun> cloudBillingTransferRuns =
        cloudBillingTransferRunDao.list(accountId, TransferJobRunState.PENDING);

    cloudBillingTransferRuns.forEach(cloudBillingTransferRun -> {
      GcpOrganization gcpOrganization = gcpOrganizationDao.get(cloudBillingTransferRun.getOrganizationUuid());
      try {
        TransferRun transferRun = billingDataPipelineService.getTransferRuns(
            cloudBillingTransferRun.getTransferRunResourceName(), gcpOrganization.getServiceAccountEmail());
        if (transferRun.getState() == TransferState.SUCCEEDED) {
          BillingDataPipelineRecord billingDataPipelineRecord =
              billingDataPipelineRecordDao.get(cloudBillingTransferRun.getBillingDataPipelineRecordId());
          try {
            billingDataPipelineService.triggerTransferJobRun(
                billingDataPipelineRecord.getPreAggregatedScheduleQueryResourceName(),
                gcpOrganization.getServiceAccountEmail());
          } catch (IOException e) {
            log.error("Error while starting manual run for Scheduled Queries {}",
                billingDataPipelineRecord.getPreAggregatedScheduledQueryName(), e);
          }
          cloudBillingTransferRun.setState(TransferJobRunState.SUCCEEDED);
          cloudBillingTransferRunDao.upsert(cloudBillingTransferRun);
        }
      } catch (IOException e) {
        log.error("Failed to get the details for the TransferRun {}",
            cloudBillingTransferRun.getTransferRunResourceName(), e);
      }
    });

    return null;
  }
}
