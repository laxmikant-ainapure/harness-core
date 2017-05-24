package software.wings.app;

import static org.eclipse.jetty.util.LazyList.isEmpty;
import static software.wings.dl.PageRequest.Builder.aPageRequest;
import static software.wings.waitnotify.ErrorNotifyResponseData.Builder.anErrorNotifyResponseData;

import org.atmosphere.cpr.BroadcasterFactory;
import org.mongodb.morphia.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.DelegateTask;
import software.wings.beans.DelegateTask.Status;
import software.wings.beans.SearchFilter.Operator;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.lock.PersistentLocker;
import software.wings.utils.CacheHelper;
import software.wings.waitnotify.WaitNotifyEngine;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Scheduled Task to look for finished WaitInstances and send messages to NotifyEventQueue.
 *
 * @author Rishi
 */
public class DelegateQueueTask implements Runnable {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private PersistentLocker persistentLocker;

  @Inject private BroadcasterFactory broadcasterFactory;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    boolean lockAcquired = false;
    try {
      lockAcquired = persistentLocker.acquireLock(DelegateQueueTask.class, DelegateQueueTask.class.getName());
      if (!lockAcquired) {
        log().warn("Persistent lock could not be acquired for the DelegateQueue");
        return;
      }

      Query<DelegateTask> releaseLongQueuedTasks =
          wingsPersistence.createQuery(DelegateTask.class)
              .field("status")
              .equal(Status.QUEUED)
              .field("lastUpdatedAt")
              .lessThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2));

      wingsPersistence.update(
          releaseLongQueuedTasks, wingsPersistence.createUpdateOperations(DelegateTask.class).unset("delegateId"));

      Query<DelegateTask> killLongRunningTasksQuery =
          wingsPersistence.createQuery(DelegateTask.class)
              .field("status")
              .equal(DelegateTask.Status.STARTED)
              .field("lastUpdatedAt")
              .lessThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2));

      DelegateTask delegateTask = null;
      do {
        delegateTask = wingsPersistence.getDatastore().findAndModify(killLongRunningTasksQuery,
            wingsPersistence.createUpdateOperations(DelegateTask.class).set("status", DelegateTask.Status.ERROR));

        if (delegateTask != null) {
          waitNotifyEngine.notify(delegateTask.getWaitId(),
              anErrorNotifyResponseData()
                  .withErrorMessage("Delegate " + delegateTask.getDelegateId() + " timeout out.")
                  .build());
        }
      } while (delegateTask != null);

      CacheHelper.getCache("delegateSyncCache", String.class, DelegateTask.class).forEach(stringDelegateTaskEntry -> {
        DelegateTask syncDelegateTask = stringDelegateTaskEntry.getValue();
        if (syncDelegateTask.getStatus().equals(Status.QUEUED) && syncDelegateTask.getDelegateId() == null) {
          logger.info("Broadcast queued sync task [{}, {}, {}]", syncDelegateTask.getUuid(),
              syncDelegateTask.getDelegateId(), syncDelegateTask.getStatus());
          broadcasterFactory.lookup("/stream/delegate/" + syncDelegateTask.getAccountId(), true)
              .broadcast(syncDelegateTask);
        }
      });

      /*stream(spliteratorUnknownSize(CacheHelper.getCache("delegateSyncCache", String.class,
         DelegateTask.class).iterator(), Spliterator.NONNULL), false) .filter(stringDelegateTaskEntry ->
         stringDelegateTaskEntry.getValue().getStatus().equals(Status.QUEUED) &&
              stringDelegateTaskEntry.getValue().getDelegateId() == null).forEach(
          stringDelegateTaskEntry -> broadcasterFactory.lookup("/stream/delegate/" +
         stringDelegateTaskEntry.getValue().getAccountId(), true) .broadcast(stringDelegateTaskEntry.getValue()));*/

      PageResponse<DelegateTask> delegateTasks = wingsPersistence.query(
          DelegateTask.class, aPageRequest().addFilter("status", Operator.EQ, Status.QUEUED).build());

      if (isEmpty(delegateTasks)) {
        log().debug("There are no delegateTasks to process");
        return;
      }

      delegateTasks.getResponse().forEach(delegateTask1
          -> broadcasterFactory.lookup("/stream/delegate/" + delegateTask1.getAccountId(), true)
                 .broadcast(delegateTask1));

    } catch (Exception exception) {
      log().error("Error seen in the Notifier call", exception);
    } finally {
      if (lockAcquired) {
        persistentLocker.releaseLock(DelegateQueueTask.class, DelegateQueueTask.class.getName());
      }
    }
  }

  private Logger log() {
    return LoggerFactory.getLogger(getClass());
  }
}
