package io.harness.serializer.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
public class ProtoJsonSerializer<T extends Message> extends StdSerializer<T> {
  public ProtoJsonSerializer(Class<T> t) {
    super(t);
  }
  public ProtoJsonSerializer() {
    this(null);
  }
  @Override
  public void serialize(T entity, JsonGenerator jgen, SerializerProvider serializerProvider) throws IOException {
    jgen.writeRawValue(JsonFormat.printer().print(entity));
  }
}
