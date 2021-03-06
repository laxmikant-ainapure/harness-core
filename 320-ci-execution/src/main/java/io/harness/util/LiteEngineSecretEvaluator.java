package io.harness.util;

import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.stateutils.buildstate.SecretUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class LiteEngineSecretEvaluator extends ExpressionEvaluator {
  private SecretUtils secretUtils;
  public List<SecretVariableDetails> resolve(Object o, NGAccess ngAccess) {
    CINgSecretManagerFunctor ciNgSecretManagerFunctor =
        CINgSecretManagerFunctor.builder().secretUtils(secretUtils).ngAccess(ngAccess).build();

    ResolveFunctorImpl resolveFunctor = new ResolveFunctorImpl(new ExpressionEvaluator(), ciNgSecretManagerFunctor);

    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);

    return ciNgSecretManagerFunctor.getSecretVariableDetails();
  }

  public LiteEngineSecretEvaluator(SecretUtils secretUtils) {
    this.secretUtils = secretUtils;
  }

  public class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final ExpressionEvaluator expressionEvaluator;
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    public ResolveFunctorImpl(
        ExpressionEvaluator expressionEvaluator, CINgSecretManagerFunctor ciNgSecretManagerFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("ngSecretManager", ciNgSecretManagerFunctor);
    }

    @Override
    public String renderExpression(String expression) {
      return expressionEvaluator.substitute(expression, evaluatorResponseContext);
    }

    @Override
    public Object evaluateExpression(String expression) {
      return expressionEvaluator.evaluate(expression, evaluatorResponseContext);
    }

    @Override
    public boolean hasVariables(String expression) {
      return ExpressionEvaluator.containsVariablePattern(expression);
    }
  }
}
