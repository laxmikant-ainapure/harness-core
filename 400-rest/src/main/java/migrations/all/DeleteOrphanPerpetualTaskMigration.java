package migrations.all;

import static java.lang.String.format;
import static org.reflections.Reflections.log;

import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.beans.Account;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskInfo;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskService;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import migrations.Migration;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

public class DeleteOrphanPerpetualTaskMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;

  private final String DEBUG_LINE = "DELETE_ORPHAN_PTASK_MIGRATION: ";

  @Override
  public void migrate() {
    List<Account> allAccounts = accountService.listAllAccountWithDefaultsWithoutLicenseInfo();
    for (Account account : allAccounts) {
      Set<String> pTasksToBeDeleted = new HashSet<>();
      Set<String> instanceSyncPTaskInfoToBeDeleted = new HashSet<>();
      String accountId = account.getUuid();
      log.info(
          StringUtils.join(DEBUG_LINE, "Starting delete orphaned perpetual task migration for accountId:", accountId));

      try {
        List<Key<InfrastructureMapping>> infraKeyList = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                            .field(InfrastructureMappingKeys.accountId)
                                                            .equal(accountId)
                                                            .asKeyList();
        Set<String> infraMappingsForAccount =
            infraKeyList.stream().map(key -> (String) key.getId()).collect(Collectors.toCollection(HashSet::new));

        List<InstanceSyncPerpetualTaskInfo> instanceSyncPerpetualTaskInfos =
            wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class)
                .field(InstanceSyncPerpetualTaskInfo.ACCOUNT_ID_KEY)
                .equal(accountId)
                .asList();

        Map<String, List<String>> infraMappingsPTasksMapForAccount = instanceSyncPerpetualTaskInfos.stream().collect(
            Collectors.toMap(InstanceSyncPerpetualTaskInfo::getInfrastructureMappingId,
                InstanceSyncPerpetualTaskInfo::getPerpetualTaskIds));

        Map<String, String> infraMappingsInstanceSyncPTaskInfoMap =
            instanceSyncPerpetualTaskInfos.stream().collect(Collectors.toMap(
                InstanceSyncPerpetualTaskInfo::getInfrastructureMappingId, InstanceSyncPerpetualTaskInfo::getUuid));

        infraMappingsPTasksMapForAccount.forEach((infraMappingId, pTasks) -> {
          if (!infraMappingsForAccount.contains(infraMappingId)
              && StringUtils.isNotEmpty(infraMappingsInstanceSyncPTaskInfoMap.get(infraMappingId))) {
            pTasksToBeDeleted.addAll(pTasks);
            instanceSyncPTaskInfoToBeDeleted.add(infraMappingsInstanceSyncPTaskInfoMap.get(infraMappingId));
          }
        });

        pTasksToBeDeleted.forEach(pTaskId -> {
          try {
            perpetualTaskService.deleteTask(accountId, pTaskId);
            log.info(StringUtils.join(DEBUG_LINE,
                format("Successfully deleted orphaned perpetualTask %s for acountId %s", pTaskId, accountId)));
          } catch (Exception ex) {
            log.error(StringUtils.join(DEBUG_LINE,
                format("Error deleting orphaned perpetualTask %s for accountId %s", pTaskId, accountId),
                ex.getMessage()));
          }
        });

        try {
          Query<InstanceSyncPerpetualTaskInfo> query = wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class)
                                                           .field(InstanceSyncPerpetualTaskInfo.ACCOUNT_ID_KEY)
                                                           .equal(accountId)
                                                           .field(InstanceSyncPerpetualTaskInfo.UUID_KEY)
                                                           .in(instanceSyncPTaskInfoToBeDeleted);

          wingsPersistence.delete(query);

          log.info(StringUtils.join(DEBUG_LINE,
              format(
                  "Successfully deleted all InstanceSyncPerpetualTaskInfo corresponds to orphaned perpetualTasks for accountId %s",
                  accountId)));

        } catch (Exception ex) {
          log.error(StringUtils.join(
              DEBUG_LINE, format("Error deleting InstanceSyncPTaskInfo for accountId %s", accountId), ex.getMessage()));
        }

      } catch (Exception ex) {
        log.error(StringUtils.join(
            DEBUG_LINE, format("Error deleting orphaned perpetualTasks for accountId %s", accountId), ex.getMessage()));
      }
      log.info(StringUtils.join(DEBUG_LINE,
          format("Successfully deleted all InstanceSyncPerpetualTaskInfo and orphaned perpetualTasks for accountId %s",
              accountId)));
    }
  }
}
