package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.preAggQueryKey;
import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.scheduledQueryKey;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.pricing.data.CloudProvider;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.billing.entities.BillingDataPipelineRecord;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class AwsBillingDataPipelineTasklet implements Tasklet {
  @Autowired private BatchMainConfig mainConfig;
  @Autowired private BillingDataPipelineService billingDataPipelineService;
  @Autowired private BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Autowired protected CloudToHarnessMappingService cloudToHarnessMappingService;
  private JobParameters parameters;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    Account account = cloudToHarnessMappingService.getAccountInfoFromId(accountId);
    String accountName = account.getAccountName();

    List<SettingAttribute> ceConnectorsList = cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(
        accountId, SettingCategory.CE_CONNECTOR, SettingVariableTypes.CE_AWS,
        CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_START_DATE).toEpochMilli(),
        CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE).toEpochMilli());

    boolean awsUseNewPipeline = mainConfig.getBillingDataPipelineConfig().isAwsUseNewPipeline();
    ceConnectorsList.forEach(settingAttribute -> {
      String settingId = settingAttribute.getUuid();
      CEAwsConfig awsConfig = (CEAwsConfig) settingAttribute.getValue();
      String masterAccountId = awsConfig.getAwsMasterAccountId();
      BillingDataPipelineRecord billingDataPipelineRecord =
          billingDataPipelineRecordDao.getByMasterAccountId(accountId, masterAccountId);
      if (null == billingDataPipelineRecord) {
        try {
          String dataSetId = billingDataPipelineService.createDataSet(account);
          String dataTransferJobName = "";
          HashMap<String, String> scheduledQueryJobsMap = new HashMap<>();
          if (awsUseNewPipeline) {
            log.info("Using new AWS pipeline. Skipping creation of AWS transfer jobs and scheduled queries");
          } else {
            log.info("Using old AWS pipeline. Creating AWS transfer jobs and scheduled queries");
            dataTransferJobName = billingDataPipelineService.createDataTransferJobFromGCS(
                dataSetId, settingId, accountId, accountName, awsConfig.getCurReportName(), false);
            // Prev Month Transfer Job
            billingDataPipelineService.createDataTransferJobFromGCS(
                dataSetId, settingId, accountId, accountName, awsConfig.getCurReportName(), true);
            scheduledQueryJobsMap =
                billingDataPipelineService.createScheduledQueriesForAWS(dataSetId, accountId, accountName);
          }
          BillingDataPipelineRecord dataPipelineRecord =
              BillingDataPipelineRecord.builder()
                  .accountId(accountId)
                  .accountName(accountName)
                  .cloudProvider(CloudProvider.AWS.name())
                  .awsMasterAccountId(masterAccountId)
                  .settingId(settingId)
                  .dataSetId(dataSetId)
                  .dataTransferJobName(dataTransferJobName)
                  .awsFallbackTableScheduledQueryName(scheduledQueryJobsMap.getOrDefault(scheduledQueryKey, ""))
                  .preAggregatedScheduledQueryName(scheduledQueryJobsMap.getOrDefault(preAggQueryKey, ""))
                  .build();
          billingDataPipelineRecordDao.create(dataPipelineRecord);
        } catch (IOException e) {
          log.error("Error while creating Billing Data Pipeline {}", e);
        }
      }
    });
    return null;
  }
}
