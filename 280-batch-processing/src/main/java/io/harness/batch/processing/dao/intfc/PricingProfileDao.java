package io.harness.batch.processing.dao.intfc;

import io.harness.ccm.cluster.entities.PricingProfile;

public interface PricingProfileDao {
  boolean create(PricingProfile pricingProfile);

  PricingProfile fetchPricingProfile(String accountId);
}
