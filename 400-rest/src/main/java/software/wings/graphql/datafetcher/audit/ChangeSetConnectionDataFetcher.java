package software.wings.graphql.datafetcher.audit;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.Principal;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.audit.QLChangeSetFilter;
import software.wings.graphql.schema.type.audit.QLChangeSet;
import software.wings.graphql.schema.type.audit.QLChangeSetConnection;
import software.wings.graphql.schema.type.audit.QLChangeSetConnection.QLChangeSetConnectionBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@TargetModule(Module._380_CG_GRAPHQL)
public class ChangeSetConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLChangeSetFilter, QLNoOpSortCriteria, QLChangeSetConnection> {
  @Inject ChangeSetQueryHelper changeSetQueryHelper;
  @Inject private ChangeContentHelper changeContentHelper;
  @Inject private ChangeSetController changeSetController;

  @Override
  @AuthRule(permissionType = PermissionType.AUDIT_VIEWER)
  public QLChangeSetConnection fetchConnection(List<QLChangeSetFilter> serviceFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<AuditHeader> query = populateFilters(wingsPersistence, serviceFilters, AuditHeader.class, true);
    query.order(Sort.descending(AuditHeaderKeys.createdAt));

    QLChangeSetConnectionBuilder connectionBuilder = QLChangeSetConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, audit -> {
      final QLChangeSet changeSet = changeSetController.populateChangeSet(audit);
      connectionBuilder.node(changeSet);
    }));
    final String accountId = getAccountId();
    final Principal triggeredBy = getTriggeredBy();
    changeContentHelper.reportAuditTrailExportToSegment(accountId, triggeredBy);
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLChangeSetFilter> filters, Query query) {
    changeSetQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLChangeSetFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
