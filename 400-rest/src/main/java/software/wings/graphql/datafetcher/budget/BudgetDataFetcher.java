package software.wings.graphql.datafetcher.budget;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.budget.Budget;
import io.harness.ccm.budget.BudgetService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.datafetcher.billing.QLBillingStatsHelper;
import software.wings.graphql.schema.query.QLBudgetQueryParameters;
import software.wings.graphql.schema.type.aggregation.budget.QLBudgetDataList;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(Module._380_CG_GRAPHQL)
public class BudgetDataFetcher extends AbstractObjectDataFetcher<QLBudgetDataList, QLBudgetQueryParameters> {
  public static final String BUDGET_DOES_NOT_EXIST_MSG = "Budget does not exist";

  @Inject BudgetService budgetService;
  @Inject QLBillingStatsHelper statsHelper;
  @Inject CeAccountExpirationChecker accountChecker;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLBudgetDataList fetch(QLBudgetQueryParameters qlQuery, String accountId) {
    accountChecker.checkIsCeEnabled(accountId);
    Budget budget = null;
    if (qlQuery.getBudgetId() != null) {
      log.info("Fetching budget data");
      budget = budgetService.get(qlQuery.getBudgetId(), accountId);
    }
    if (budget == null) {
      throw new InvalidRequestException(BUDGET_DOES_NOT_EXIST_MSG, WingsException.USER);
    }
    return budgetService.getBudgetData(budget);
  }
}
