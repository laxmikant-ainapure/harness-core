package software.wings.graphql.datafetcher.k8sLabel;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.k8sLabel.QLK8sLabelFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class K8sLabelQueryHelper {
  @Inject protected DataFetcherUtils utils;

  public void setQuery(List<QLK8sLabelFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;

      if (filter.getAccountId() != null) {
        field = query.field("accountId");
        QLIdFilter accountFilter = filter.getAccountId();
        utils.setIdFilter(field, accountFilter);
      }

      if (filter.getCluster() != null) {
        field = query.field("clusterId");
        QLIdFilter clusterFilter = filter.getCluster();
        utils.setIdFilter(field, clusterFilter);
      }

      if (filter.getNamespace() != null) {
        field = query.field("namespace");
        QLIdFilter namespaceFilter = filter.getNamespace();
        utils.setIdFilter(field, namespaceFilter);
      }

      if (filter.getWorkloadName() != null) {
        field = query.field("name");
        QLIdFilter workloadName = filter.getWorkloadName();
        utils.setIdFilter(field, workloadName);
      }
    });
  }
}
