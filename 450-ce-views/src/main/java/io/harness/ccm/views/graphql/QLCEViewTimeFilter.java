package io.harness.ccm.views.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PUBLIC)
public class QLCEViewTimeFilter {
  QLCEViewFieldInput field;
  QLCEViewTimeFilterOperator operator;
  Number value;
}
