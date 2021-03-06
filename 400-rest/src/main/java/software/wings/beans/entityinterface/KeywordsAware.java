package software.wings.beans.entityinterface;

import io.harness.beans.EmbeddedUser;
import io.harness.persistence.CreatedByAccess;
import io.harness.persistence.UpdatedByAccess;

import java.util.HashSet;
import java.util.Set;

public interface KeywordsAware {
  Set<String> getKeywords();
  void setKeywords(Set<String> keywords);

  default Set<String> generateKeywords() {
    Set<String> keyWordList = new HashSet<>();

    if (this instanceof CreatedByAccess) {
      CreatedByAccess createdByAccess = (CreatedByAccess) this;
      final EmbeddedUser createdBy = createdByAccess.getCreatedBy();
      if (createdBy != null) {
        keyWordList.add(createdBy.getName());
        keyWordList.add(createdBy.getEmail());
      }
    }

    if (this instanceof UpdatedByAccess) {
      UpdatedByAccess updatedByAccess = (UpdatedByAccess) this;
      final EmbeddedUser updatedBy = updatedByAccess.getLastUpdatedBy();
      if (updatedBy != null) {
        keyWordList.add(updatedBy.getName());
        keyWordList.add(updatedBy.getEmail());
      }
    }

    return keyWordList;
  }
}
