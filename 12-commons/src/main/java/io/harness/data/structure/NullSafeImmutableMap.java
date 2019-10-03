package io.harness.data.structure;

import lombok.experimental.UtilityClass;

@UtilityClass
// Note that the name of the class is chosen to allow for a simple replacement of the original ImmutableMap
// ImmutableMap.builder()... simply becomes NullSafeImmutableMap.builder()...
// Technically it can inherit the ImmutableMap and became a an object class, but this will serve no purpose
// since it does not extend the functionality of the class.
public class NullSafeImmutableMap {
  public static class NullSafeBuilder<K, V> extends com.google.common.collect.ImmutableMap.Builder<K, V> {
    @Override
    public NullSafeBuilder<K, V> put(K key, V value) {
      super.put(key, value);
      return this;
    }

    public NullSafeBuilder<K, V> putIfNotNull(K key, V value) {
      if (value != null) {
        put(key, value);
      }
      return this;
    }
  }

  public static <K, V> NullSafeBuilder builder() {
    return new NullSafeBuilder<K, V>();
  }
}
