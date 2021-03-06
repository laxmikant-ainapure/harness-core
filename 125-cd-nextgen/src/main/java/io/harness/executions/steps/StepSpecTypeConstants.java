package io.harness.executions.steps;

public interface StepSpecTypeConstants {
  String HTTP = "Http";

  String K8S_ROLLING_DEPLOY = "K8sRollingDeploy";
  String K8S_ROLLING_ROLLBACK = "K8sRollingRollback";
  String K8S_BLUE_GREEN_DEPLOY = "K8sBlueGreenDeploy";
  String K8S_APPLY = "K8sApply";
  String K8S_SCALE = "K8sScale";
  String K8S_BG_SWAP_SERVICES = "K8sBGSwapServices";
  String K8S_CANARY_DELETE = "K8sCanaryDelete";
  String K8S_CANARY_DEPLOY = "K8sCanaryDeploy";
  String K8S_DELETE = "K8sDelete";

  String SHELL_SCRIPT = "ShellScript";
  String PLACEHOLDER = "Placeholder";
}
