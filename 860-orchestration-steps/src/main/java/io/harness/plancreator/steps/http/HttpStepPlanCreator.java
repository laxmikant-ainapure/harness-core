package io.harness.plancreator.steps.http;

import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class HttpStepPlanCreator extends PMSStepPlanCreatorV2<HttpStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.HTTP);
  }

  @Override
  public Class<HttpStepNode> getFieldClass() {
    return HttpStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, HttpStepNode field) {
    return super.createPlanForField(ctx, field);
  }
}