package software.wings.graphql.datafetcher;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
@TargetModule(Module._380_CG_GRAPHQL)
public abstract class AbstractConnectionDataFetcher<T, P> extends AbstractObjectDataFetcher<T, P> {
  @Inject protected WingsPersistence persistence;

  // Adds closed open range filter to the query
  protected <M> void filterDatetimeRange(Query<M> query, String fieldName, OffsetDateTime from, OffsetDateTime to) {
    if (from != null) {
      query.field(fieldName).greaterThanOrEq(from.toInstant().toEpochMilli());
    }
    if (to != null) {
      query.field(fieldName).lessThan(to.toInstant().toEpochMilli());
    }
  }

  protected abstract T fetchConnection(P parameters);

  @Override
  protected final T fetch(P parameters, String accountId) {
    try {
      return fetchConnection(parameters);
    } catch (WingsException ex) {
      if (ErrorCode.ACCESS_DENIED == ex.getCode()) {
        log.warn("User doesn't have access to resource or no entities exist in that app");
        return null;
      }
      throw ex;
    }
  }
}
