package io.harness;

import java.lang.reflect.Field;
import org.apache.commons.lang3.reflect.FieldUtils;

public interface MockableTestMixin {
  default void setStaticFieldValue(final Class<?> clz, final String fieldName, final Object value)
      throws IllegalAccessException {
    final Field f = FieldUtils.getField(clz, fieldName, true);
    FieldUtils.removeFinalModifier(f);
    f.set(null, value);
  }
}
