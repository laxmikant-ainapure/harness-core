package io.harness.springdata;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.StoreIn;
import io.harness.annotation.StoreInMultiple;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.Store;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class PersistenceStoreUtils {
  public static Set<Class<?>> getMatchingEntities(Set<Class<?>> allEntities, Store targetStore) {
    Set<Class<?>> finalClasses = new HashSet<>();
    for (Class<?> clz : allEntities) {
      Set<String> storeInSet = fetchStores(clz);
      if (isNotEmpty(storeInSet)) {
        if (storeInSet.contains(DbAliases.ALL) || (targetStore != null && storeInSet.contains(targetStore.getName()))) {
          finalClasses.add(clz);
        }
      } else {
        finalClasses.add(clz);
      }
    }
    return finalClasses;
  }

  private static Set<String> fetchStores(Class<?> clz) {
    Set<String> storeInSet = new HashSet<>();
    final StoreIn storeIn = clz.getAnnotation(StoreIn.class);
    final StoreInMultiple storeInMultiple = clz.getAnnotation(StoreInMultiple.class);
    if (storeIn != null) {
      storeInSet.add(storeIn.value());
    }
    if (storeInMultiple != null) {
      storeInSet.addAll(
          emptyIfNull(Arrays.stream(storeInMultiple.value()).map(StoreIn::value).collect(Collectors.toList())));
    }
    return storeInSet;
  }
}