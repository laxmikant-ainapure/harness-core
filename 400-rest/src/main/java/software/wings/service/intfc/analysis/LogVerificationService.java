package software.wings.service.intfc.analysis;

import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.bugsnag.BugsnagApplication;
import software.wings.service.impl.bugsnag.BugsnagSetupTestData;
import software.wings.sm.StateType;

import java.util.Set;
import javax.validation.constraints.NotNull;

public interface LogVerificationService {
  Set<BugsnagApplication> getOrgProjectListBugsnag(
      @NotNull String settingId, @NotNull String orgId, @NotNull StateType stateType, boolean shouldGetProjects);
  VerificationNodeDataSetupResponse getTestLogData(String accountId, BugsnagSetupTestData bugsnagSetupTestData);
}
