package io.harness;

import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.schema.YamlBaseUrlService;

import com.google.inject.Inject;

public class YamlBaseUrlServiceImpl implements YamlBaseUrlService {
  @Inject NextGenConfiguration nextGenConfiguration;

  @Override
  public String getBaseUrl() {
    return nextGenConfiguration.getNgManagerClientConfig().getBaseUrl();
  }
}
