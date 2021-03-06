package io.harness.cdng.expressions;

import io.harness.cdng.pipeline.plancreators.PipelinePlanCreator;
import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.expression.VariableResolverTracker;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ngpipeline.expressions.functors.EventPayloadFunctor;
import io.harness.ngpipeline.expressions.functors.OrgFunctor;
import io.harness.ngpipeline.expressions.functors.ProjectFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepOutcomeGroup;

import com.google.inject.Inject;
import java.util.Set;

public class CDExpressionEvaluator extends AmbianceExpressionEvaluator {
  //  @Inject private AccountService accountService;
  @Inject private OrganizationService organizationService;
  @Inject private ProjectService projectService;

  public CDExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific) {
    super(variableResolverTracker, ambiance, entityTypes, refObjectSpecific);
  }

  @Override
  protected void initialize() {
    super.initialize();
    //    addToContext("account", new AccountFunctor(accountService, ambiance));
    // TODO(archit): Add new AccountService when done for NG
    addToContext("org", new OrgFunctor(organizationService, ambiance));
    addToContext("project", new ProjectFunctor(projectService, ambiance));
    addToContext(PipelinePlanCreator.EVENT_PAYLOAD_KEY, new EventPayloadFunctor(ambiance));
    addStaticAlias("artifact", "service.artifacts.primary.output");
    addStaticAlias("serviceVariables", "service.variables.output");
    addStaticAlias("env", "infrastructure.environment");
    addGroupAlias(YAMLFieldNameConstants.STAGE, StepOutcomeGroup.STAGE.name());
    addGroupAlias(YAMLFieldNameConstants.STEP, StepOutcomeGroup.STEP.name());
  }
}
