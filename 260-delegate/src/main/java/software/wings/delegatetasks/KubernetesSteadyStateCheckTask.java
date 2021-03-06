package software.wings.delegatetasks;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.exception.WingsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.KubernetesSteadyStateCheckParams;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.sm.states.KubernetesSteadyStateCheckResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class KubernetesSteadyStateCheckTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private TimeLimiter timeLimiter;

  public KubernetesSteadyStateCheckTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public KubernetesSteadyStateCheckResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public KubernetesSteadyStateCheckResponse run(Object[] parameters) {
    KubernetesSteadyStateCheckParams kubernetesSteadyStateCheckParams =
        (KubernetesSteadyStateCheckParams) parameters[0];

    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(delegateLogService,
        kubernetesSteadyStateCheckParams.getAccountId(), kubernetesSteadyStateCheckParams.getAppId(),
        kubernetesSteadyStateCheckParams.getActivityId(), kubernetesSteadyStateCheckParams.getCommandName());

    if (containerDeploymentDelegateBaseHelper.getControllerCountByLabels(
            containerDeploymentDelegateHelper.getKubernetesConfig(
                kubernetesSteadyStateCheckParams.getContainerServiceParams()),
            kubernetesSteadyStateCheckParams.getLabels())
        == 0) {
      String msg = String.format(
          "No controller found for the specified Labels: %s", kubernetesSteadyStateCheckParams.getLabels());
      executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return KubernetesSteadyStateCheckResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }

    List<ContainerInfo> containerInfos = new ArrayList<>();
    try {
      timeLimiter.callWithTimeout(
          ()
              -> containerInfos.addAll(doSteadyStateCheck(kubernetesSteadyStateCheckParams, executionLogCallback)),
          kubernetesSteadyStateCheckParams.getTimeoutMillis(), TimeUnit.MILLISECONDS, true);

      executionLogCallback.saveExecutionLog(
          "Command finished with status " + ExecutionStatus.SUCCESS, LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return KubernetesSteadyStateCheckResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .containerInfoList(containerInfos)
          .build();
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out waiting for controller to reach steady state";
      log.error(msg, e);
      executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return KubernetesSteadyStateCheckResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    } catch (WingsException e) {
      Misc.logAllMessages(e, executionLogCallback);
      throw e;
    } catch (Exception e) {
      log.error("Exception in KubernetesSteadyStateCheck", e);
      Misc.logAllMessages(e, executionLogCallback);
      executionLogCallback.saveExecutionLog("Exception occurred while waiting for controller to reach steady state",
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return KubernetesSteadyStateCheckResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }
  }

  private List<ContainerInfo> doSteadyStateCheck(
      KubernetesSteadyStateCheckParams kubernetesSteadyStateCheckParams, ExecutionLogCallback executionLogCallback) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        kubernetesSteadyStateCheckParams.getContainerServiceParams());

    return containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
        kubernetesConfig, executionLogCallback, kubernetesSteadyStateCheckParams.getLabels(), Collections.emptyList());
  }
}
