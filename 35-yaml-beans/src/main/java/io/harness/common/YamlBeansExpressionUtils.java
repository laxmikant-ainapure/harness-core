package io.harness.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class YamlBeansExpressionUtils {
  private static final Pattern InputSetVariablePattern = Pattern.compile("\\$\\{input}.*");
  public static final String DEFAULT_INPUT_SET_EXPRESSION = "${input}";

  public boolean matchesInputSetPattern(String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return YamlBeansExpressionUtils.InputSetVariablePattern.matcher(expression).matches();
  }

  // Function which matches pattern on given expression.
  public boolean containsPattern(Pattern pattern, String expression) {
    if (isEmpty(expression)) {
      return false;
    }
    return pattern.matcher(expression).find();
  }

  public String getInputSetValidatorPattern(String validatorName) {
    return "\\$\\{input}\\." + validatorName + "\\(";
  }
}
