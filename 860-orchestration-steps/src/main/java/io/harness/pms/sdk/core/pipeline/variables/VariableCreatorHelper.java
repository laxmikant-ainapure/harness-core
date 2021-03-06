package io.harness.pms.sdk.core.pipeline.variables;

import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VariableCreatorHelper {
  public void addVariablesForVariables(
      YamlField variablesField, Map<String, YamlProperties> yamlPropertiesMap, String fieldName) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode());
        String localName = YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), fieldName);
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).build());
      }
    });
  }

  public VariableCreationResponse createVariableResponseForVariables(YamlField variablesField, String fieldName) {
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    addVariablesForVariables(variablesField, yamlPropertiesMap, fieldName);
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  public void addFieldToPropertiesMap(
      YamlField fieldNode, Map<String, YamlProperties> yamlPropertiesMap, String fieldName) {
    String fqn = YamlUtils.getFullyQualifiedName(fieldNode.getNode());
    String localName = YamlUtils.getQualifiedNameTillGivenField(fieldNode.getNode(), fieldName);
    yamlPropertiesMap.put(fieldNode.getNode().getCurrJsonNode().textValue(),
        YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).build());
  }

  public boolean isNotYamlFieldEmpty(YamlField yamlField) {
    if (yamlField == null) {
      return false;
    }
    return !(
        yamlField.getNode().fields().size() == 1 && yamlField.getNode().getField(YAMLFieldNameConstants.UUID) != null);
  }
}
