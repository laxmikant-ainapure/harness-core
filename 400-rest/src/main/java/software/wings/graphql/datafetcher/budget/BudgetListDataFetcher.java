package software.wings.graphql.datafetcher.budget;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.budget.Budget;
import io.harness.ccm.budget.BudgetService;

import software.wings.graphql.datafetcher.AbstractArrayDataFetcher;
import software.wings.graphql.schema.query.QLBudgetQueryParameters;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetTableData;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(Module._380_CG_GRAPHQL)
public class BudgetListDataFetcher extends AbstractArrayDataFetcher<QLBudgetTableData, QLBudgetQueryParameters> {
  @Inject BudgetService budgetService;
  @Inject CeAccountExpirationChecker accountChecker;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected List<QLBudgetTableData> fetch(QLBudgetQueryParameters parameters, String accountId) {
    accountChecker.checkIsCeEnabled(accountId);
    List<Budget> budgets = budgetService.list(accountId);
    List<QLBudgetTableData> budgetTableDataList = new ArrayList<>();
    budgets.forEach(budget -> budgetTableDataList.add(budgetService.getBudgetDetails(budget)));
    budgetTableDataList.sort(Comparator.comparing(QLBudgetTableData::getLastUpdatedAt).reversed());
    return budgetTableDataList;
  }

  @Override
  protected QLBudgetTableData unusedReturnTypePassingDummyMethod() {
    return null;
  }
}
