package io.harness.delegate.beans.executioncapability;

import static java.util.Collections.singletonList;

import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Value;

@Value
public class ConnectivityCapabilityDemander implements ExecutionCapabilityDemander {
  String host;
  int port;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return singletonList(
        SocketConnectivityExecutionCapability.builder().hostName(host).port(String.valueOf(port)).build());
  }
}
