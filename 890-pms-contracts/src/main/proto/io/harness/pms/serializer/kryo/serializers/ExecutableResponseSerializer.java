package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class ExecutableResponseSerializer extends ProtobufKryoSerializer<ExecutableResponse> {
  private static ExecutableResponseSerializer instance;

  private ExecutableResponseSerializer() {}

  public static synchronized ExecutableResponseSerializer getInstance() {
    if (instance == null) {
      instance = new ExecutableResponseSerializer();
    }
    return instance;
  }
}
