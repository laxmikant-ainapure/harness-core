package migrations.all;

import static io.harness.marketplace.gcp.GcpMarketPlaceConstants.ENTITLEMENT_ACTIVATED;

import io.harness.marketplace.gcp.procurement.GcpProcurementService;

import software.wings.beans.MarketPlace;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.beans.marketplace.gcp.GCPMarketplaceProduct;
import software.wings.dl.WingsPersistence;

import com.google.cloudcommerceprocurement.v1.model.Entitlement;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

@Slf4j
@Singleton
public class GCPMarketplaceCustomerMigration implements Migration {
  private final WingsPersistence wingsPersistence;
  private final GcpProcurementService gcpProcurementService;

  @Inject
  public GCPMarketplaceCustomerMigration(
      WingsPersistence wingsPersistence, GcpProcurementService gcpProcurementService) {
    this.wingsPersistence = wingsPersistence;
    this.gcpProcurementService = gcpProcurementService;
  }

  @Override
  public void migrate() {
    try {
      log.info("Starting migration of all GCP marketplace entities");

      // We first need to do cleanup. NOTE: This should be done only once!
      deleteAllGcpMarketplaceCustomers();

      List<MarketPlace> gcpMarketplaces =
          wingsPersistence.createQuery(MarketPlace.class).filter("type", "GCP").asList();
      for (MarketPlace marketPlace : gcpMarketplaces) {
        migrateGcpMarketplace(marketPlace);
      }
    } catch (Exception e) {
      log.error("Failure occurred in GCPMarketplaceCustomerMigration", e);
    }
    log.info("GCPMarketplaceCustomerMigration has completed");
  }

  private void deleteAllGcpMarketplaceCustomers() {
    wingsPersistence.delete(wingsPersistence.createQuery(GCPMarketplaceCustomer.class));
  }

  private void migrateGcpMarketplace(MarketPlace marketPlace) {
    log.info("Migrating GCP marketPlace : {}", marketPlace.getUuid());
    try {
      if (marketPlace.getAccountId() == null) {
        return;
      }

      Optional<Entitlement> activeEntitlement =
          gcpProcurementService.listEntitlementsForGcpAccountId(marketPlace.getCustomerIdentificationCode())
              .stream()
              .filter(entitlement -> ENTITLEMENT_ACTIVATED.equals(entitlement.getState()))
              .findFirst();

      List<GCPMarketplaceProduct> products = new ArrayList<>();
      if (activeEntitlement.isPresent()) {
        Entitlement entitlement = activeEntitlement.get();
        products.add(GCPMarketplaceProduct.builder()
                         .plan(entitlement.getPlan())
                         .product((String) entitlement.get("productExternalName"))
                         .quoteId((String) entitlement.get("quoteExternalName"))
                         .startTime(Instant.parse(entitlement.getCreateTime()))
                         .usageReportingId(entitlement.getUsageReportingId())
                         .build());
      }

      GCPMarketplaceCustomer gcpMarketplaceCustomer = GCPMarketplaceCustomer.builder()
                                                          .gcpAccountId(marketPlace.getCustomerIdentificationCode())
                                                          .harnessAccountId(marketPlace.getAccountId())
                                                          .products(products)
                                                          .build();
      wingsPersistence.save(gcpMarketplaceCustomer);
    } catch (Exception e) {
      log.error("Error occurred while migrating marketPlace: {} ", marketPlace.getUuid(), e);
    }
  }
}
