package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

@TargetModule(Module._930_DELEGATE_TASKS)
public abstract class CloudFormationCommandTaskHandler {
  @Inject protected DelegateFileManager delegateFileManager;
  @Inject protected EncryptionService encryptionService;
  @Inject protected AwsHelperService awsHelperService;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Inject private DelegateLogService delegateLogService;

  protected static final String stackNamePrefix = "HarnessStack-";

  public Optional<Stack> getIfStackExists(String customStackName, String suffix, AwsConfig awsConfig, String region) {
    List<Stack> stacks = awsHelperService.getAllStacks(region, new DescribeStacksRequest(), awsConfig);
    if (isEmpty(stacks)) {
      return Optional.empty();
    }

    if (isNotEmpty(customStackName)) {
      return stacks.stream().filter(stack -> stack.getStackName().equals(customStackName)).findFirst();
    } else {
      return stacks.stream().filter(stack -> stack.getStackName().endsWith(suffix)).findFirst();
    }
  }

  // ten minutes default timeout for polling stack operations
  static final int DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;

  public CloudFormationCommandExecutionResponse execute(
      CloudFormationCommandRequest request, List<EncryptedDataDetail> details) {
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(delegateLogService, request.getAccountId(),
        request.getAppId(), request.getActivityId(), request.getCommandName());
    try {
      return executeInternal(request, details, executionLogCallback);
    } catch (Exception ex) {
      String errorMessage = format("Exception: %s while executing CF task.", ExceptionUtils.getMessage(ex));
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, FAILURE);
      return CloudFormationCommandExecutionResponse.builder()
          .errorMessage(errorMessage)
          .commandExecutionStatus(FAILURE)
          .build();
    }
  }

  @VisibleForTesting
  protected long printStackEvents(CloudFormationCommandRequest request, long stackEventsTs, Stack stack,
      ExecutionLogCallback executionLogCallback) {
    List<StackEvent> stackEvents = getStackEvents(request, stack);
    boolean printed = false;
    long currentLatestTs = -1;
    for (StackEvent event : stackEvents) {
      long tsForEvent = event.getTimestamp().getTime();
      if (tsForEvent > stackEventsTs) {
        if (!printed) {
          executionLogCallback.saveExecutionLog("******************** Could Formation Events ********************");
          executionLogCallback.saveExecutionLog("********[Status] [Type] [Logical Id] [Status Reason] ***********");
          printed = true;
        }
        executionLogCallback.saveExecutionLog(format("[%s] [%s] [%s] [%s] [%s]", event.getResourceStatus(),
            event.getResourceType(), event.getLogicalResourceId(), getStatusReason(event.getResourceStatusReason()),
            event.getPhysicalResourceId()));
        if (currentLatestTs == -1) {
          currentLatestTs = tsForEvent;
        }
      }
    }
    if (currentLatestTs != -1) {
      stackEventsTs = currentLatestTs;
    }
    return stackEventsTs;
  }

  private List<StackEvent> getStackEvents(CloudFormationCommandRequest request, Stack stack) {
    return awsHelperService.getAllStackEvents(request.getRegion(),
        new DescribeStackEventsRequest().withStackName(stack.getStackName()), request.getAwsConfig());
  }

  private String getStatusReason(String reason) {
    return isNotEmpty(reason) ? reason : StringUtils.EMPTY;
  }

  protected abstract CloudFormationCommandExecutionResponse executeInternal(CloudFormationCommandRequest request,
      List<EncryptedDataDetail> details, ExecutionLogCallback executionLogCallback);
}
