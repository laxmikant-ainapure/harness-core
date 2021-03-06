package io.harness.ccm.budget.entities;

import static io.harness.ccm.budget.BudgetScopeType.CLUSTER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@JsonTypeName("CLUSTER")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ClusterBudgetScopeKeys")
public class ClusterBudgetScope implements BudgetScope {
  String[] clusterIds;

  @Override
  public String getBudgetScopeType() {
    return CLUSTER;
  }

  @Override
  public List<String> getEntityIds() {
    return Arrays.asList(clusterIds);
  }

  @Override
  public List<String> getEntityNames() {
    return null;
  }
}
