package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.service.GraphGenerationService;
import io.harness.service.impl.GraphGenerationServiceImpl;
import io.harness.skip.service.VertexSkipperService;
import io.harness.skip.service.impl.VertexSkipperServiceImpl;

import com.google.inject.AbstractModule;

@OwnedBy(CDC)
public class OrchestrationVisualizationModule extends AbstractModule {
  private static OrchestrationVisualizationModule instance;

  public static OrchestrationVisualizationModule getInstance() {
    if (instance == null) {
      instance = new OrchestrationVisualizationModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(GraphGenerationService.class).to(GraphGenerationServiceImpl.class);
    bind(VertexSkipperService.class).to(VertexSkipperServiceImpl.class);
  }
}
