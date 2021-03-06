package software.wings.delegatetasks.container;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/*
 This is a dummy task added to support container instance sync via perpetual task. Container Instance sync runs using
 delegate Proxy Factory and sync tasks which cannot be supported as validation tasks in the perpetual task framework.
 All we want is the validation to run and the task being assigned to some valid delegate.
 */
@TargetModule(Module._930_DELEGATE_TASKS)
public class ContainerDummyTask extends AbstractDelegateRunnableTask {
  public ContainerDummyTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    return null;
  }
}
