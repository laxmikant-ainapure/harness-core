package software.wings.service.impl.aws.manager;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.Application.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.Cd1SetupFields;

import software.wings.beans.AwsConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.AwsS3ListBucketNamesRequest;
import software.wings.service.impl.aws.model.AwsS3ListBucketNamesResponse;
import software.wings.service.impl.aws.model.AwsS3Request;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.aws.manager.AwsS3HelperServiceManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AwsS3HelperServiceManagerImpl implements AwsS3HelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;
  @Inject private AwsHelperServiceManager helper;

  @Override
  public List<String> listBucketNames(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsS3ListBucketNamesRequest.builder().awsConfig(awsConfig).encryptionDetails(encryptionDetails).build());
    return ((AwsS3ListBucketNamesResponse) response).getBucketNames();
  }

  private AwsResponse executeTask(String accountId, AwsS3Request request) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_S3_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTask(delegateTask);
      helper.validateDelegateSuccessForSyncTask(notifyResponseData);
      return (AwsResponse) notifyResponseData;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }
  }
}
