package io.harness.walktree.visitor;

import io.harness.walktree.beans.ParentQualifier;
import io.harness.walktree.beans.VisitableChildren;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This interface should be implemented by each element which wants the element
 * to be visited and whether there are any children to traverse.
 */
public interface Visitable extends WithMetadata, ParentQualifier {
  /**
   * @return List of objects referring to children on which you want traverse.
   */
  @JsonIgnore
  default VisitableChildren getChildrenToWalk() {
    return null;
  };

  @Override
  default String getMetadata() {
    return null;
  }

  @Override
  default void setMetadata(String metadata) {
    // Nothing to set.
  }
}
