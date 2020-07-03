package io.harness.beans.converters;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.SweepingOutput;
import io.harness.serializer.KryoUtils;
import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(CDC)
public class SweepingOutputWriteMongoConverter implements Converter<SweepingOutput, Binary> {
  @Override
  public Binary convert(SweepingOutput sweepingOutput) {
    return new Binary(KryoUtils.asBytes(sweepingOutput));
  }
}
