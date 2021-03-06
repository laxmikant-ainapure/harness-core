package io.harness.repositories.activityhistory;

import io.harness.annotation.HarnessRepo;
import io.harness.ng.core.activityhistory.entity.NGActivity;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@Transactional
public interface NGActivityRepository
    extends PagingAndSortingRepository<NGActivity, String>, NGActivityCustomRepository {
  long deleteByReferredEntityFQNAndReferredEntityType(String referredEntityFQN, String referredEntityType);
}
