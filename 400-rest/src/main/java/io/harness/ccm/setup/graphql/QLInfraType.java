package io.harness.ccm.setup.graphql;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@ToString
public class QLInfraType {
  QLInfraTypesEnum infraType;
}
