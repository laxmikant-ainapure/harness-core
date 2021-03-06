package software.wings.graphql.datafetcher.user;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.user.QLUserFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class UserQueryHelper {
  private DataFetcherUtils utils;

  @Inject
  public UserQueryHelper(DataFetcherUtils utils) {
    this.utils = utils;
  }
  public void setQuery(List<QLUserFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }
    filters.forEach(filter -> {
      if (filter.getUser() != null) {
        FieldEnd<? extends Query<User>> field = query.field("_id");
        QLIdFilter userFilter = filter.getUser();
        utils.setIdFilter(field, userFilter);
      }
    });
  }

  public void setAccountFilter(Query<User> query, final String accountId) {
    query.or(query.criteria("accounts").equal(accountId), query.criteria("pendingAccounts").equal(accountId));
  }
}
