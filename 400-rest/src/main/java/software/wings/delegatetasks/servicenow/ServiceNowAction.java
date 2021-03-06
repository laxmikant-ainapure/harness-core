package software.wings.delegatetasks.servicenow;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

@TargetModule(Module._930_DELEGATE_TASKS)
public enum ServiceNowAction {
  CREATE("Create"),
  UPDATE("Update"),
  IMPORT_SET("Import Set");

  private String displayName;
  ServiceNowAction(String s) {
    displayName = s;
  }
}
