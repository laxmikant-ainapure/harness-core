package software.wings.graphql.datafetcher.workflow;

import static software.wings.graphql.utils.GraphQLConstants.NO_RECORDS_FOUND_FOR_APP_ID_AND_ENTITY;
import static software.wings.graphql.utils.GraphQLConstants.WORKFLOW_TYPE;

import com.google.inject.Inject;

import graphql.schema.DataFetchingEnvironment;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.wings.beans.Workflow;
import software.wings.graphql.datafetcher.AbstractDataFetcher;
import software.wings.graphql.schema.type.QLWorkflow;
import software.wings.graphql.utils.GraphQLConstants;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.WorkflowService;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkflowDataFetcher extends AbstractDataFetcher<QLWorkflow> {
  WorkflowService workflowService;

  @Inject
  public WorkflowDataFetcher(WorkflowService workflowService, AuthHandler authHandler) {
    super(authHandler);
    this.workflowService = workflowService;
  }

  private boolean isAuthorizedToView(String appId, String workflowId) {
    PermissionAttribute permissionAttribute = new PermissionAttribute(PermissionType.WORKFLOW, Action.READ);
    return isAuthorizedToView(appId, permissionAttribute, workflowId);
  }

  @Override
  public QLWorkflow fetch(DataFetchingEnvironment dataFetchingEnvironment) {
    QLWorkflow workflowInfo = QLWorkflow.builder().build();
    String appId = (String) getArgumentValue(dataFetchingEnvironment, GraphQLConstants.APP_ID);
    String workflowId = (String) getArgumentValue(dataFetchingEnvironment, GraphQLConstants.WORKFLOW_ID);
    // Pre-checks
    if (StringUtils.isBlank(appId)) {
      addInvalidInputInfo(workflowInfo, GraphQLConstants.APP_ID);
      return workflowInfo;
    }

    if (StringUtils.isBlank(workflowId)) {
      addInvalidInputInfo(workflowInfo, GraphQLConstants.WORKFLOW_ID);
      return workflowInfo;
    }

    if (!this.isAuthorizedToView(appId, workflowId)) {
      throw notAuthorizedException(WORKFLOW_TYPE, workflowId, appId);
    }

    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    if (workflow != null) {
      workflowInfo = WorkflowController.getWorkflow(workflow);
    } else {
      addNoRecordFoundInfo(workflowInfo, NO_RECORDS_FOUND_FOR_APP_ID_AND_ENTITY, WORKFLOW_TYPE, workflowId, appId);
    }
    return workflowInfo;
  }
}
