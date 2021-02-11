package software.wings.graphql.datafetcher.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLWorkflowQueryParameters;
import software.wings.graphql.schema.type.QLWorkflow;
import software.wings.graphql.schema.type.QLWorkflow.QLWorkflowBuilder;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(Module._380_CG_GRAPHQL)
public class WorkflowDataFetcher extends AbstractObjectDataFetcher<QLWorkflow, QLWorkflowQueryParameters> {
  public static final String WORKFLOW_DOES_NOT_EXIST_MSG = "Workflow does not exist";
  public static final String EMPTY_WORKFLOW_NAME = "Empty Workflow name";
  public static final String EMPTY_APPLICATION_ID = "Empty Application Id";

  @Inject HPersistence persistence;

  @Override
  @AuthRule(permissionType = PermissionType.WORKFLOW, action = Action.READ)
  public QLWorkflow fetch(QLWorkflowQueryParameters qlQuery, String accountId) {
    Workflow workflow = null;

    if (qlQuery.getWorkflowId() != null) {
      workflow = persistence.get(Workflow.class, qlQuery.getWorkflowId());
    } else if (qlQuery.getWorkflowName() != null) {
      if (EmptyPredicate.isEmpty(qlQuery.getApplicationId())) {
        throw new InvalidRequestException(EMPTY_APPLICATION_ID, WingsException.USER);
      }

      if (EmptyPredicate.isEmpty(qlQuery.getWorkflowName())) {
        throw new InvalidRequestException(EMPTY_WORKFLOW_NAME, WingsException.USER);
      }
      workflow = persistence.createQuery(Workflow.class)
                     .filter(WorkflowKeys.name, qlQuery.getWorkflowName())
                     .filter(WorkflowKeys.appId, qlQuery.getApplicationId())
                     .get();
    } else if (qlQuery.getExecutionId() != null) {
      // TODO: add this to in memory cache
      final String workflowId = persistence.createQuery(WorkflowExecution.class)
                                    .filter(WorkflowExecutionKeys.uuid, qlQuery.getExecutionId())
                                    .project(WorkflowExecutionKeys.workflowId, true)
                                    .get()
                                    .getWorkflowId();

      workflow = persistence.get(Workflow.class, workflowId);
    }

    if (workflow == null) {
      return null;
    }

    if (!workflow.getAccountId().equals(accountId)) {
      throw new InvalidRequestException(WORKFLOW_DOES_NOT_EXIST_MSG, WingsException.USER);
    }

    final QLWorkflowBuilder builder = QLWorkflow.builder();
    WorkflowController.populateWorkflow(workflow, builder);
    return builder.build();
  }
}
