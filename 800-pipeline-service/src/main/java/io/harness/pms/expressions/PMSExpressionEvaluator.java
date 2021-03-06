package io.harness.pms.expressions;

import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.expression.VariableResolverTracker;
import io.harness.ng.core.account.remote.AccountClient;
import io.harness.ngpipeline.expressions.functors.EventPayloadFunctor;
import io.harness.organizationmanagerclient.remote.OrganizationManagerClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expressions.functors.AccountFunctor;
import io.harness.pms.expressions.functors.ImagePullSecretFunctor;
import io.harness.pms.expressions.functors.OrgFunctor;
import io.harness.pms.expressions.functors.ProjectFunctor;
import io.harness.pms.expressions.functors.SidecarImagePullSecretFunctor;
import io.harness.pms.expressions.utils.ImagePullSecretUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.projectmanagerclient.remote.ProjectManagerClient;
import io.harness.steps.StepOutcomeGroup;

import com.google.inject.Inject;
import java.util.Set;

public class PMSExpressionEvaluator extends AmbianceExpressionEvaluator {
  @Inject private AccountClient accountClient;
  @Inject private OrganizationManagerClient organizationManagerClient;
  @Inject private ProjectManagerClient projectManagerClient;
  @Inject private ImagePullSecretUtils imagePullSecretUtils;

  public PMSExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific) {
    super(variableResolverTracker, ambiance, entityTypes, refObjectSpecific);
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("account", new AccountFunctor(accountClient, ambiance));
    addToContext("org", new OrgFunctor(organizationManagerClient, ambiance));
    addToContext("project", new ProjectFunctor(projectManagerClient, ambiance));
    addToContext(ImagePullSecretFunctor.IMAGE_PULL_SECRET,
        ImagePullSecretFunctor.builder()
            .imagePullSecretUtils(imagePullSecretUtils)
            .pmsOutcomeService(getPmsOutcomeService())
            .ambiance(ambiance)
            .build());
    addToContext(ImagePullSecretFunctor.SIDECAR_IMAGE_PULL_SECRET,
        SidecarImagePullSecretFunctor.builder()
            .imagePullSecretUtils(imagePullSecretUtils)
            .pmsOutcomeService(getPmsOutcomeService())
            .ambiance(ambiance)
            .build());
    addToContext(SetupAbstractionKeys.eventPayload, new EventPayloadFunctor(ambiance));
    addStaticAlias("artifact", "service.artifacts.primary.output");
    addStaticAlias("serviceVariables", "service.variables.output");
    addStaticAlias("env", "infrastructureSection.environment");
    addGroupAlias(YAMLFieldNameConstants.STAGE, StepOutcomeGroup.STAGE.name());
    addGroupAlias(YAMLFieldNameConstants.STEP, StepOutcomeGroup.STEP.name());
  }
}
