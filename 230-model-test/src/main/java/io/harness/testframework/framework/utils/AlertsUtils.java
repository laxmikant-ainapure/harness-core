package io.harness.testframework.framework.utils;

import io.harness.notifications.beans.Conditions;
import io.harness.notifications.beans.Conditions.Operator;

import software.wings.alerts.AlertCategory;
import software.wings.beans.alert.AlertFilter;
import software.wings.beans.alert.AlertNotificationRule;
import software.wings.beans.alert.AlertType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AlertsUtils {
  public static AlertNotificationRule createAlertNotificationRule(
      String accountId, Set<String> userGroups, AlertCategory alertCategory, AlertType alertType) {
    Conditions conditions = new Conditions(Operator.MATCHING, null, null);
    AlertFilter alertFilter = new AlertFilter(alertType, conditions);
    return new AlertNotificationRule(accountId, alertCategory, alertFilter, userGroups);
  }

  public static AlertNotificationRule createAlertNotificationRuleWithConditions(String accountId,
      Set<String> userGroups, AlertCategory alertCategory, AlertType alertType, Conditions conditions) {
    AlertFilter alertFilter = new AlertFilter(alertType, conditions);
    return new AlertNotificationRule(accountId, alertCategory, alertFilter, userGroups);
  }

  public static boolean isAlertAvailable(List<AlertNotificationRule> alertsList, AlertNotificationRule alertToCheck) {
    for (AlertNotificationRule alertNotificationRule : alertsList) {
      if (alertToCheck.getUuid().equals(alertNotificationRule.getUuid())) {
        return true;
      }
    }
    return false;
  }

  public static List<AlertType> getSetupAlertTypes() {
    List<AlertType> alertTypeList = new ArrayList<>();
    alertTypeList.add(AlertType.DelegatesDown);
    alertTypeList.add(AlertType.DelegatesScalingGroupDownAlert);
    alertTypeList.add(AlertType.NoActiveDelegates);
    alertTypeList.add(AlertType.DelegateProfileError);
    alertTypeList.add(AlertType.NoEligibleDelegates);
    alertTypeList.add(AlertType.INSTANCE_USAGE_APPROACHING_LIMIT);
    alertTypeList.add(AlertType.InvalidKMS);
    alertTypeList.add(AlertType.GitSyncError);
    alertTypeList.add(AlertType.RESOURCE_USAGE_APPROACHING_LIMIT);
    alertTypeList.add(AlertType.DEPLOYMENT_RATE_APPROACHING_LIMIT);
    alertTypeList.add(AlertType.GitConnectionError);
    alertTypeList.add(AlertType.USAGE_LIMIT_EXCEEDED);
    alertTypeList.add(AlertType.USERGROUP_SYNC_FAILED);
    return alertTypeList;
  }

  public static List<AlertType> getCVAlertTypes() {
    List<AlertType> alertTypeList = new ArrayList<>();
    alertTypeList.add(AlertType.CONTINUOUS_VERIFICATION_ALERT);
    alertTypeList.add(AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT);
    return alertTypeList;
  }
}
