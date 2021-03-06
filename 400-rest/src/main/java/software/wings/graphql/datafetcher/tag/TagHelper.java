package software.wings.graphql.datafetcher.tag;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.EntityType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 09/08/2019
 */
@Slf4j
@Singleton
@TargetModule(Module._380_CG_GRAPHQL)
public class TagHelper {
  @Inject protected HarnessTagService tagService;
  @Inject protected WorkflowExecutionService workflowExecutionService;

  // Returns set of all unique entity ids that match the tags for given entity type
  public Set<String> getEntityIdsFromTags(String accountId, List<QLTagInput> tags, EntityType entityType) {
    if (isNotEmpty(tags)) {
      Set<String> entityIds = new HashSet<>();
      tags.forEach(tag -> {
        Set<String> entityIdsForTag =
            tagService.getEntityIdsWithTag(accountId, tag.getName(), entityType, tag.getValue());
        if (isNotEmpty(entityIdsForTag)) {
          entityIds.addAll(entityIdsForTag);
        }
      });
      return entityIds;
    }
    return null;
  }

  public Set<String> getWorkExecutionsWithTags(String accountId, List<QLTagInput> tags) {
    if (isNotEmpty(tags)) {
      Set<String> entityIds = new HashSet<>();
      tags.forEach(tag -> {
        Set<String> workflowExecutionsWithTags =
            workflowExecutionService.getWorkflowExecutionsWithTag(accountId, tag.getName(), tag.getValue());
        if (isNotEmpty(workflowExecutionsWithTags)) {
          entityIds.addAll(workflowExecutionsWithTags);
        }
      });
      return entityIds;
    }
    return null;
  }
}
