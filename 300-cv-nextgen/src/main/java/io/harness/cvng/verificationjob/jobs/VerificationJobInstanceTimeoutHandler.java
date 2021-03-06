package io.harness.cvng.verificationjob.jobs;

import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class VerificationJobInstanceTimeoutHandler
    implements MongoPersistenceIterator.Handler<VerificationJobInstance> {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  @Override
  public void handle(VerificationJobInstance entity) {
    verificationJobInstanceService.markTimedOutIfNoProgress(entity);
  }
}
