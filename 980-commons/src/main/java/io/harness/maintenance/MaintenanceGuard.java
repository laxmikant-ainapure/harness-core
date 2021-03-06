package io.harness.maintenance;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public class MaintenanceGuard implements AutoCloseable {
  private boolean old;
  public MaintenanceGuard(boolean maintenance) {
    old = MaintenanceController.getMaintenanceFlag();
    MaintenanceController.forceMaintenance(maintenance);
  }

  @Override
  public void close() {
    MaintenanceController.forceMaintenance(old);
  }
}
