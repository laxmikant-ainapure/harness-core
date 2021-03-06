package io.harness.lock;

import java.time.Duration;

/**
 * Locker interface to acquire and release locks.
 *
 * @author Rishi
 */
public interface PersistentLocker {
  /**
   * Acquire lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireLock(String name, Duration timeout);

  /**
   * Acquire ephemeral lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireEphemeralLock(String name, Duration timeout);

  /**
   * Acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param timeout     for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireLock(Class entityClass, String entityId, Duration timeout);

  /**
   * Try to acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param timeout     for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireLock(Class entityClass, String entityId, Duration timeout);

  /**
   * Try to acquire ephemeral lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param timeout     for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireEphemeralLock(Class entityClass, String entityId, Duration timeout);

  /**
   * Try to acquire lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireLock(String name, Duration timeout);

  /**
   * Try to acquire ephemeral lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireEphemeralLock(String name, Duration timeout);

  /**
   * Acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param lockTimeout for how long to keep the lock if the app crashes
   * @param waitTimeout  how long to wait to acquire the lock
   * @return AcquiredLock object
   */
  AcquiredLock waitToAcquireLock(Class entityClass, String entityId, Duration lockTimeout, Duration waitTimeout);

  AcquiredLock waitToAcquireLock(String name, Duration lockTimeout, Duration waitTimeout);

  /**
   * Destroy lock.
   *
   * @param acquiredLock  already acquired lock
   */
  void destroy(AcquiredLock acquiredLock);
}
