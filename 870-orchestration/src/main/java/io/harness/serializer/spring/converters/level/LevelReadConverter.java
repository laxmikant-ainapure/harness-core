package io.harness.serializer.spring.converters.level;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.inject.Singleton;
import org.springframework.data.convert.ReadingConverter;

@OwnedBy(CDC)
@Singleton
@ReadingConverter
public class LevelReadConverter extends ProtoReadConverter<Level> {
  public LevelReadConverter() {
    super(Level.class);
  }
}
