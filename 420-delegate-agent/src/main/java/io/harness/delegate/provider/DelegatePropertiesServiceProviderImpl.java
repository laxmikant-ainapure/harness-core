package io.harness.delegate.provider;

import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.delegate.service.DelegatePropertyService;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegatePropertiesServiceProviderImpl implements DelegatePropertiesServiceProvider {
  @Inject DelegatePropertyService delegatePropertyService;

  @Override
  public GetDelegatePropertiesResponse getDelegateProperties(GetDelegatePropertiesRequest request) {
    try {
      return delegatePropertyService.getDelegateProperties(request);
    } catch (ExecutionException e) {
      log.warn("Unable to fetch Delegate Properties", e);
      e.printStackTrace();
    }
    return null;
  }
}
