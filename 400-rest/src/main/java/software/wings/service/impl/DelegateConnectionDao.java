package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.upToOne;

import static software.wings.beans.DelegateConnection.EXPIRY_TIME;
import static software.wings.beans.DelegateConnection.TTL;
import static software.wings.beans.ManagerConfiguration.MATCH_ALL_VERSION;

import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.persistence.HPersistence;

import software.wings.beans.DelegateConnection;
import software.wings.beans.DelegateConnection.DelegateConnectionKeys;
import software.wings.beans.DelegateStatus;
import software.wings.beans.ManagerConfiguration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
@TargetModule(Module._420_DELEGATE_SERVICE)
public class DelegateConnectionDao {
  @Inject private HPersistence persistence;

  public void delegateDisconnected(String accountId, String delegateConnectionId) {
    log.info("Mark as disconnected delegateConnectionId: {}", delegateConnectionId);
    Query<DelegateConnection> query = persistence.createQuery(DelegateConnection.class)
                                          .filter(DelegateConnectionKeys.accountId, accountId)
                                          .filter(DelegateConnectionKeys.uuid, delegateConnectionId);
    UpdateOperations<DelegateConnection> updateOperations = persistence.createUpdateOperations(DelegateConnection.class)
                                                                .set(DelegateConnectionKeys.disconnected, Boolean.TRUE);
    persistence.update(query, updateOperations);
  }

  public Map<String, List<DelegateStatus.DelegateInner.DelegateConnectionInner>> obtainActiveDelegateConnections(
      String accountId) {
    List<DelegateConnection> delegateConnections = persistence.createQuery(DelegateConnection.class)
                                                       .filter(DelegateConnectionKeys.accountId, accountId)
                                                       .field(DelegateConnectionKeys.disconnected)
                                                       .notEqual(Boolean.TRUE)
                                                       .field(DelegateConnectionKeys.lastHeartbeat)
                                                       .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
                                                       .project(DelegateConnectionKeys.delegateId, true)
                                                       .project(DelegateConnectionKeys.version, true)
                                                       .project(DelegateConnectionKeys.lastHeartbeat, true)
                                                       .asList();

    return delegateConnections.stream().collect(Collectors.groupingBy(delegateConnection
        -> delegateConnection.getDelegateId(),
        Collectors.mapping(delegateConnection
            -> DelegateStatus.DelegateInner.DelegateConnectionInner.builder()
                   .uuid(delegateConnection.getUuid())
                   .lastHeartbeat(delegateConnection.getLastHeartbeat())
                   .version(delegateConnection.getVersion())
                   .build(),
            toList())));
  }

  public Set<String> obtainConnectedDelegates(String accountId) {
    Query<DelegateConnection> query = persistence.createQuery(DelegateConnection.class)
                                          .filter(DelegateConnectionKeys.accountId, accountId)
                                          .filter(DelegateConnectionKeys.disconnected, Boolean.FALSE)
                                          .field(DelegateConnectionKeys.lastHeartbeat)
                                          .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis());
    String primaryVersion = persistence.createQuery(ManagerConfiguration.class).get().getPrimaryVersion();
    if (isNotEmpty(primaryVersion) && !StringUtils.equals(primaryVersion, MATCH_ALL_VERSION)) {
      query.filter(DelegateConnectionKeys.version, primaryVersion);
    }
    return query.project(DelegateConnectionKeys.delegateId, true)
        .asList()
        .stream()
        .map(DelegateConnection::getDelegateId)
        .collect(toSet());
  }

  public List<DelegateConnection> list(String accountId, String delegateId) {
    return persistence.createQuery(DelegateConnection.class)
        .filter(DelegateConnectionKeys.accountId, accountId)
        .filter(DelegateConnectionKeys.delegateId, delegateId)
        .filter(DelegateConnectionKeys.disconnected, Boolean.FALSE)
        .field(DelegateConnectionKeys.lastHeartbeat)
        .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
        .asList();
  }

  public boolean checkDelegateConnected(String accountId, String delegateId, String version) {
    return persistence.createQuery(DelegateConnection.class)
               .filter(DelegateConnectionKeys.accountId, accountId)
               .filter(DelegateConnectionKeys.delegateId, delegateId)
               .filter(DelegateConnectionKeys.version, version)
               .filter(DelegateConnectionKeys.disconnected, Boolean.FALSE)
               .field(DelegateConnectionKeys.lastHeartbeat)
               .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
               .count(upToOne)
        > 0;
  }

  public DelegateConnection upsertCurrentConnection(
      String accountId, String delegateId, String delegateConnectionId, String version, String location) {
    Query<DelegateConnection> query = persistence.createQuery(DelegateConnection.class)
                                          .filter(DelegateConnectionKeys.accountId, accountId)
                                          .filter(DelegateConnectionKeys.uuid, delegateConnectionId);

    UpdateOperations<DelegateConnection> updateOperations =
        persistence.createUpdateOperations(DelegateConnection.class)
            .set(DelegateConnectionKeys.accountId, accountId)
            .set(DelegateConnectionKeys.uuid, delegateConnectionId)
            .set(DelegateConnectionKeys.delegateId, delegateId)
            .set(DelegateConnectionKeys.version, version)
            .set(DelegateConnectionKeys.lastHeartbeat, currentTimeMillis())
            .set(DelegateConnectionKeys.disconnected, Boolean.FALSE)
            .set(DelegateConnectionKeys.validUntil,
                Date.from(OffsetDateTime.now().plusMinutes(TTL.toMinutes()).toInstant()));
    if (location != null) {
      updateOperations.set(DelegateConnectionKeys.location, location);
    }

    return persistence.upsert(query, updateOperations, HPersistence.upsertReturnOldOptions);
  }

  public DelegateConnection findAndDeletePreviousConnections(
      String accountId, String delegateId, String delegateConnectionId, String version) {
    return persistence.findAndDelete(persistence.createQuery(DelegateConnection.class)
                                         .filter(DelegateConnectionKeys.accountId, accountId)
                                         .filter(DelegateConnectionKeys.delegateId, delegateId)
                                         .filter(DelegateConnectionKeys.version, version)
                                         .field(DelegateConnectionKeys.uuid)
                                         .notEqual(delegateConnectionId),
        HPersistence.returnOldOptions);
  }

  public void replaceWithNewerConnection(String delegateConnectionId, DelegateConnection existingConnection) {
    persistence.delete(DelegateConnection.class, delegateConnectionId);
    persistence.save(existingConnection);
  }
}
