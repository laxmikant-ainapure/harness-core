package io.harness.ccm.views.entities;

import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViewTimeRange {
  ViewTimeRangeType viewTimeRangeType;
  @Nullable long startTime;
  @Nullable long endTime;
}
