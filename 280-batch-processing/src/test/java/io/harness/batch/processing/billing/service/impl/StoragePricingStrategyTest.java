package io.harness.batch.processing.billing.service.impl;

import static io.harness.batch.processing.pricing.data.StoragePricingData.GBMONTH_TO_MBHOUR;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.service.PricingData;
import io.harness.batch.processing.ccm.PricingSource;
import io.harness.batch.processing.pricing.service.support.StorageCustomPricingProvider;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.ccm.commons.entities.InstanceData;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StoragePricingStrategyTest extends CategoryTest {
  @InjectMocks private StoragePricingStrategy storagePricingStrategy;

  private static final Instant NOW = Instant.now();
  private static final double STORAGE_CAPACITY = 1024D;

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetPricePerHour() {
    InstanceData instanceData =
        InstanceData.builder().storageResource(StorageResource.builder().capacity(STORAGE_CAPACITY).build()).build();

    PricingData pricingData =
        storagePricingStrategy.getPricePerHour(instanceData, NOW.minus(24, ChronoUnit.HOURS), NOW, 24 * 3600D);

    assertThat(pricingData.getPricingSource()).isEqualTo(PricingSource.HARDCODED);
    assertThat(pricingData.getStorageMb()).isEqualTo(STORAGE_CAPACITY);
    assertThat(pricingData.getPricePerHour())
        .isEqualTo(
            StorageCustomPricingProvider.Unknown.DEFAULT.getDefaultPrice() * STORAGE_CAPACITY / GBMONTH_TO_MBHOUR);
  }
}