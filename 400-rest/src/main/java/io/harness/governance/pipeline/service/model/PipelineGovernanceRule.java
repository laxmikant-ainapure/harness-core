package io.harness.governance.pipeline.service.model;

import io.harness.data.structure.CollectionUtils;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Associates a weight with a set of tags.
 */
@Value
public class PipelineGovernanceRule {
  private List<Tag> tags;

  @Nonnull private MatchType matchType;
  private int weight;
  @Nullable private String note;

  public List<Tag> getTags() {
    List<Tag> tags = CollectionUtils.emptyIfNull(this.tags);
    return Collections.unmodifiableList(tags);
  }
}
