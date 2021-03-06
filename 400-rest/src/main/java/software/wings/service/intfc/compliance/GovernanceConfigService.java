package software.wings.service.intfc.compliance;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.governance.DeploymentFreezeInfo;
import io.harness.governance.GovernanceFreezeConfig;

import software.wings.beans.governance.GovernanceConfig;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author rktummala on 02/04/19
 */
@TargetModule(Module._960_API_SERVICES)
public interface GovernanceConfigService extends OwnedByAccount {
  GovernanceConfig get(String accountId);

  GovernanceConfig upsert(String accountId, GovernanceConfig governanceConfig);

  /**
   * @param accountId the accountId
   * @return Returns a deployment freeze info object consisting information about master deployment freeze status,
   *     frozen apps and environments
   */
  DeploymentFreezeInfo getDeploymentFreezeInfo(String accountId);

  /**
   * @param accountId the accountId
   * @param deploymentFreezeIds the deployment freeze window ids needed to be fetched
   * @return Returns a list of deployment freeze windows corresponding to the ids
   */
  List<GovernanceFreezeConfig> getGovernanceFreezeConfigs(String accountId, List<String> deploymentFreezeIds);

  /**
   * @param accountId the accountId
   * @param appId the appId for which frozen environments need to be fetched
   * @return Returns a map which has window names as keys and values as environments in the app frozen by that
   *     particular window
   */
  Map<String, Set<String>> getFrozenEnvIdsForApp(String accountId, String appId, GovernanceConfig governanceConfig);
}
