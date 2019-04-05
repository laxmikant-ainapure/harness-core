package software.wings.beans.command;

import static software.wings.sm.states.AwsAmiSwitchRoutesState.SWAP_AUTO_SCALING_ROUTES;
import static software.wings.sm.states.EcsBGUpdateListnerState.ECS_UPDATE_LISTENER_COMMAND;
import static software.wings.sm.states.EcsBGUpdateRoute53DNSWeightState.UPDATE_ROUTE_53_DNS_WEIGHTS;
import static software.wings.sm.states.EcsBlueGreenServiceSetup.ECS_SERVICE_SETUP_COMMAND_ELB;
import static software.wings.sm.states.EcsBlueGreenServiceSetupRoute53DNS.ECS_SERVICE_SETUP_COMMAND_ROUTE53;
import static software.wings.sm.states.EcsDaemonServiceSetup.ECS_DAEMON_SERVICE_SETUP_COMMAND;
import static software.wings.sm.states.EcsServiceDeploy.ECS_SERVICE_DEPLOY;
import static software.wings.sm.states.EcsServiceSetup.ECS_SERVICE_SETUP_COMMAND;
import static software.wings.sm.states.EcsSetupRollback.ECS_DAEMON_SERVICE_ROLLBACK_COMMAND;
import static software.wings.sm.states.EcsSteadyStateCheck.ECS_STEADY_STATE_CHECK_COMMAND_NAME;
import static software.wings.sm.states.HelmDeployState.HELM_COMMAND_NAME;
import static software.wings.sm.states.JenkinsState.COMMAND_UNIT_NAME;
import static software.wings.sm.states.KubernetesSteadyStateCheck.KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME;
import static software.wings.sm.states.KubernetesSwapServiceSelectors.KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME;
import static software.wings.sm.states.pcf.MapRouteState.PCF_MAP_ROUTE_COMMAND;
import static software.wings.sm.states.pcf.PcfDeployState.PCF_RESIZE_COMMAND;
import static software.wings.sm.states.pcf.PcfSetupState.PCF_SETUP_COMMAND;
import static software.wings.sm.states.pcf.PcfSwitchBlueGreenRoutes.PCF_BG_SWAP_ROUTE_COMMAND;

import io.harness.delegate.command.CommandExecutionResult.CommandExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.wings.beans.Variable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by rsingh on 11/17/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandUnitDetails {
  private String name;
  private CommandExecutionStatus commandExecutionStatus;
  private CommandUnitType commandUnitType;
  @Builder.Default private List<Variable> variables = new ArrayList<>();

  public enum CommandUnitType {
    COMMAND("COMMAND"),
    JENKINS(COMMAND_UNIT_NAME),
    HELM(HELM_COMMAND_NAME),
    KUBERNETES_STEADY_STATE_CHECK(KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME),
    ECS_STEADY_STATE_CHECK(ECS_STEADY_STATE_CHECK_COMMAND_NAME),
    PCF_SETUP(PCF_SETUP_COMMAND),
    PCF_RESIZE(PCF_RESIZE_COMMAND),
    PCF_MAP_ROUTE(PCF_MAP_ROUTE_COMMAND),
    PCF_BG_SWAP_ROUTE(PCF_BG_SWAP_ROUTE_COMMAND),
    KUBERNETES_SWAP_SERVICE_SELECTORS(KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME),
    KUBERNETES("KUBERNETES"),
    AWS_AMI_SWITCH_ROUTES(SWAP_AUTO_SCALING_ROUTES),
    AWS_ECS_UPDATE_LISTENER_BG(ECS_UPDATE_LISTENER_COMMAND),
    AWS_ECS_UPDATE_ROUTE_53_DNS_WEIGHT(UPDATE_ROUTE_53_DNS_WEIGHTS),
    AWS_ECS_SERVICE_SETUP(ECS_SERVICE_SETUP_COMMAND),
    AWS_ECS_SERVICE_SETUP_ROUTE53(ECS_SERVICE_SETUP_COMMAND_ROUTE53),
    AWS_ECS_SERVICE_SETUP_ELB(ECS_SERVICE_SETUP_COMMAND_ELB),
    AWS_ECS_SERVICE_SETUP_DAEMON(ECS_DAEMON_SERVICE_SETUP_COMMAND),
    AWS_ECS_SERVICE_ROLLBACK_DAEMON(ECS_DAEMON_SERVICE_ROLLBACK_COMMAND),
    AWS_ECS_SERVICE_DEPLOY(ECS_SERVICE_DEPLOY);

    private String name;

    public String getName() {
      return name;
    }

    CommandUnitType(String name) {
      this.name = name;
    }
  }
}
