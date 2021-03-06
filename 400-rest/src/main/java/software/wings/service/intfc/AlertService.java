package software.wings.service.intfc;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByApplication;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.ws.rs.QueryParam;

@TargetModule(Module._470_ALERT)
public interface AlertService extends OwnedByAccount, OwnedByApplication {
  PageResponse<Alert> list(PageRequest<Alert> pageRequest);

  List<AlertType> listCategoriesAndTypes(@QueryParam("accountId") String accountId);

  Future openAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  Future openAlertWithTTL(String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil);

  Future closeExistingAlertsAndOpenNew(
      String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil);

  void closeAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  void close(Alert alert);

  void closeAllAlerts(String accountId, String appId, AlertType alertType, AlertData alertData);

  void closeAlertsOfType(String accountId, String appId, AlertType alertType);

  void delegateAvailabilityUpdated(String accountId);

  void delegateEligibilityUpdated(String accountId, String delegateId);

  void deploymentCompleted(String appId, String executionId);

  Optional<Alert> findExistingAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  void deleteByArtifactStream(String appId, String artifactStreamId);
}
