package migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.data.structure.EmptyPredicate;
import io.harness.persistence.HIterator;

import software.wings.beans.GitConfig;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.query.Query;

@Slf4j
public class TerraformIsTemplatizedMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject @Transient private transient SettingsService settingsService;

  @Override
  public void migrate() {
    Query<InfrastructureProvisioner> query =
        wingsPersistence.createQuery(InfrastructureProvisioner.class, excludeAuthority)
            .field("infrastructureProvisionerType")
            .equal("TERRAFORM");
    try (HIterator<InfrastructureProvisioner> provisioners = new HIterator<>(query.fetch())) {
      while (provisioners.hasNext()) {
        TerraformInfrastructureProvisioner provisioner = null;
        try {
          provisioner = (TerraformInfrastructureProvisioner) provisioners.next();
          if (infrastructureProvisionerService.isTemplatizedProvisioner(provisioner)) {
            provisioner.setTemplatized(true);
          } else {
            provisioner.setTemplatized(false);
          }

          if (EmptyPredicate.isEmpty(provisioner.getSourceRepoBranch())) {
            SettingAttribute gitSettingAttribute = settingsService.get(provisioner.getSourceRepoSettingId());
            if (gitSettingAttribute != null && gitSettingAttribute.getValue() instanceof GitConfig) {
              GitConfig gitConfig = (GitConfig) gitSettingAttribute.getValue();
              provisioner.setSourceRepoBranch(gitConfig.getBranch());
            }
          }

          if (EmptyPredicate.isNotEmpty(provisioner.getSourceRepoBranch()) && provisioner.getPath() != null) {
            infrastructureProvisionerService.update(provisioner);
          }
        } catch (Exception ex) {
          log.error("Error while updating isTemplatized field for provisioner: {}",
              provisioner != null ? provisioner.getName() : "", ex);
        }
      }
    } catch (Exception ex) {
      log.error("Failed - Populating templatized field in TerraformInfrastructureProvisioner", ex);
    }
  }
}
