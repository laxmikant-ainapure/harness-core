package io.harness.delegate.expression;

import io.harness.exception.FunctorException;
import io.harness.expression.ExpressionFunctor;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SecretDelegateFunctor implements ExpressionFunctor {
  private Map<String, char[]> secrets;
  private int expressionFunctorToken;

  public Object obtain(String secretDetailsUuid, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }
    if (secrets.containsKey(secretDetailsUuid)) {
      return new String(secrets.get(secretDetailsUuid));
    }
    throw new FunctorException("Secret details not found");
  }
}
