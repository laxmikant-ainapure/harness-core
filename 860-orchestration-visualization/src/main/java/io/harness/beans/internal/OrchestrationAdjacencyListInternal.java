package io.harness.beans.internal;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class OrchestrationAdjacencyListInternal {
  Map<String, GraphVertex> graphVertexMap;
  Map<String, EdgeListInternal> adjacencyMap;
}
