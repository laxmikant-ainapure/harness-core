package io.harness.pms.sdk.core.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import java.util.Map;

/**
 * Use this interface when you want spawn a child
 *
 * This Node will spawn child and the response is passed to handleChildResponse as {@link
 * StepResponseNotifyData}
 *
 */

@OwnedBy(CDC)
public interface ChildExecutable<T extends StepParameters> extends Step<T> {
  ChildExecutableResponse obtainChild(Ambiance ambiance, T stepParameters, StepInputPackage inputPackage);

  StepResponse handleChildResponse(Ambiance ambiance, T stepParameters, Map<String, ResponseData> responseDataMap);
}
