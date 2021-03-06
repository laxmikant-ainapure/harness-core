package software.wings.delegatetasks.azure.arm;

import static io.harness.azure.model.AzureConstants.COMMAND_TYPE_BLANK_VALIDATION_MSG;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(Module._930_DELEGATE_TASKS)
public class AzureARMTaskFactory {
  @Inject private Map<String, AbstractAzureARMTaskHandler> azureARMTaskTypeToTaskHandlerMap;

  public AbstractAzureARMTaskHandler getAzureARMTask(String commandType) {
    if (isBlank(commandType)) {
      throw new InvalidArgumentsException(COMMAND_TYPE_BLANK_VALIDATION_MSG);
    }
    return azureARMTaskTypeToTaskHandlerMap.get(commandType);
  }
}
