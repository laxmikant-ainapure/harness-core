package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.pricing.data.CloudProvider;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.billing.entities.BillingDataPipelineRecord;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;
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
public class AzureBillingDataPipelineTasklet implements Tasklet {
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
        accountId, SettingCategory.CE_CONNECTOR, SettingVariableTypes.CE_AZURE,
        CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_START_DATE).toEpochMilli(),
        CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE).toEpochMilli());

    boolean awsUseNewPipeline = mainConfig.getBillingDataPipelineConfig().isAwsUseNewPipeline();
    ceConnectorsList.forEach(settingAttribute -> {
      String settingId = settingAttribute.getUuid();
      BillingDataPipelineRecord billingDataPipelineRecord = billingDataPipelineRecordDao.getByAccountId(accountId);
      if (null == billingDataPipelineRecord) {
        String dataSetId = billingDataPipelineService.createDataSet(account);
        BillingDataPipelineRecord dataPipelineRecord = BillingDataPipelineRecord.builder()
                                                           .accountId(accountId)
                                                           .accountName(accountName)
                                                           .cloudProvider(CloudProvider.AZURE.name())
                                                           .settingId(settingId)
                                                           .dataSetId(dataSetId)
                                                           .build();
        billingDataPipelineRecordDao.create(dataPipelineRecord);
      }
    });
    return null;
  }
}
