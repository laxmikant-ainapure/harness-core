package io.harness.facilitate.modes.child;

import io.harness.delegate.beans.ResponseData;
import io.harness.facilitate.PassThroughData;
import io.harness.state.io.StateParameters;
import io.harness.state.io.StateResponse;
import io.harness.state.io.StateTransput;
import io.harness.state.io.ambiance.Ambiance;

import java.util.List;
import java.util.Map;

public interface ChildExecutable {
  ChildExecutableResponse obtainChild(
      Ambiance ambiance, StateParameters parameters, List<StateTransput> inputs, PassThroughData childPassThroughData);

  StateResponse handleAsyncResponse(
      Ambiance ambiance, StateParameters stateParameters, Map<String, ResponseData> responseDataMap);
}
