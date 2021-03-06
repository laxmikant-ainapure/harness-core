package io.harness.walktree.beans;

import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;

public class DummyVisitorFieldProcessor implements VisitableFieldProcessor<DummyVisitorField> {
  @Override
  public String getExpressionFieldValue(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public DummyVisitorField updateCurrentField(DummyVisitorField actualField, DummyVisitorField overrideField) {
    return null;
  }

  @Override
  public DummyVisitorField cloneField(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public String getFieldWithStringValue(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public DummyVisitorField createNewFieldWithStringValue(String stringValue) {
    return DummyVisitorField.builder().value(stringValue).build();
  }

  @Override
  public boolean isNull(DummyVisitorField actualField) {
    return false;
  }
}
