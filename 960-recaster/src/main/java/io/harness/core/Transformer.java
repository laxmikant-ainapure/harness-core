package io.harness.core;

import static java.lang.String.format;

import io.harness.beans.CastedField;
import io.harness.exceptions.RecasterException;
import io.harness.transformers.DefaultRecastTransformer;
import io.harness.transformers.RecastTransformer;
import io.harness.transformers.simplevalue.CustomValueTransformer;
import io.harness.transformers.simplevalue.SimpleValueTransformer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
public abstract class Transformer {
  Recaster recaster;
  Map<Class<?>, RecastTransformer> converterMap = new HashMap<>();
  private final List<RecastTransformer> untypedTypeTransformers = new LinkedList<>();

  public Transformer(Recaster recaster) {
    this.recaster = recaster;
  }

  public void addCustomTransformer(RecastTransformer recastTransformer) {
    if (!(recastTransformer instanceof CustomValueTransformer)) {
      throw new RecasterException(
          format("RecasterTransformer %s implement CustomValueTransformer interface", recastTransformer));
    }

    addTransformer(recastTransformer);
  }

  protected void addTransformer(RecastTransformer recastTransformer) {
    if (recastTransformer.getSupportedTypes() != null) {
      for (final Class<?> c : recastTransformer.getSupportedTypes()) {
        addTypedConverter(c, recastTransformer);
      }
    } else {
      untypedTypeTransformers.add(recastTransformer);
    }
    recastTransformer.setRecaster(recaster);
  }

  private void addTypedConverter(final Class<?> type, final RecastTransformer rc) {
    if (converterMap.containsKey(type)) {
      log.error("Added duplicate converter for " + type + " ; " + converterMap.get(type));
      // TODO : Create a custom exception here
      throw new RuntimeException();
    } else {
      converterMap.put(type, rc);
    }
  }

  public Object decode(final Class<?> c, final Object docObject, final CastedField cf) {
    Class<?> toDecode = c;
    if (toDecode == null) {
      toDecode = docObject.getClass();
    }
    return getTransformer(toDecode).decode(toDecode, docObject, cf);
  }

  public Object encode(final Object o) {
    if (o == null) {
      return null;
    }
    return encode(o.getClass(), o);
  }

  public void fromDocument(final Object targetEntity, final CastedField cf, final Document document) {
    final Object object = cf.getDocumentValue(document);
    if (object != null) {
      RecastTransformer transformer = getTransformer(cf.getType());
      Object decodedValue = transformer.decode(cf.getType(), object, cf);
      try {
        cf.setFieldValue(targetEntity, decodedValue);
      } catch (IllegalArgumentException e) {
        throw new RecasterException(format("Error setting value from converter (%s) for %s to %s",
                                        transformer.getClass().getSimpleName(), cf.getFullName(), decodedValue),
            e);
      }
    }
  }

  public void toDocument(final Object containingObject, final CastedField cf, final Document document) {
    final Object fieldValue = cf.getFieldValue(containingObject);
    RecastTransformer enc = getTransformer(fieldValue, cf);
    if (!(enc instanceof SimpleValueTransformer)) {
      enc = getTransformer(fieldValue != null ? fieldValue.getClass() : containingObject.getClass());
    }

    if (enc instanceof DefaultRecastTransformer && fieldValue != null) {
      log.warn("Default transformer is used for {} with value {}", cf.getField(), fieldValue);
    }

    final Object encoded = enc.encode(fieldValue, cf);
    document.put(cf.getNameToStore(), encoded);
  }

  public Object encode(final Class<?> c, final Object o) {
    return getTransformer(c).encode(o, null);
  }

  protected RecastTransformer getTransformer(final Class<?> c) {
    RecastTransformer recastTransformer = converterMap.get(c);
    if (recastTransformer != null) {
      return recastTransformer;
    }

    for (RecastTransformer rc : untypedTypeTransformers) {
      if (rc.canTransform(c)) {
        return rc;
      }
    }

    return null;
  }

  protected RecastTransformer getTransformer(final Object val, final CastedField cf) {
    RecastTransformer rc = null;
    if (val != null) {
      rc = converterMap.get(val.getClass());
    }

    if (rc == null) {
      rc = converterMap.get(cf.getType());
    }

    if (rc != null) {
      return rc;
    }

    for (RecastTransformer recastTransformer : untypedTypeTransformers) {
      if (recastTransformer.canTransform(cf) && (val != null && recastTransformer.isSupported(val.getClass(), cf))) {
        return recastTransformer;
      }
    }

    return null;
  }

  public boolean hasSimpleValueTransformer(final Object o) {
    if (o == null) {
      return false;
    }
    if (o instanceof Class) {
      return hasSimpleValueTransformer((Class<?>) o);
    } else {
      return hasSimpleValueTransformer(o.getClass());
    }
  }

  public boolean hasSimpleValueTransformer(Class<?> c) {
    return getTransformer(c) instanceof SimpleValueTransformer;
  }

  public boolean hasCustomTransformer(Class<?> c) {
    return getTransformer(c) instanceof CustomValueTransformer;
  }
}
