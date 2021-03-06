package software.wings.graphql.datafetcher.secrets;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class SecretController {
  @Inject SecretManager secretManager;
}
