package software.wings.graphql.schema.mutation.userGroup.payload;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.mutation.QLMutationPayload;
import software.wings.graphql.schema.type.usergroup.QLUserGroup;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLCreateUserGroupPayloadKeys")
@Scope(PermissionAttribute.ResourceType.USER)
@TargetModule(Module._380_CG_GRAPHQL)
public class QLCreateUserGroupPayload implements QLMutationPayload {
  String clientMutationId;
  QLUserGroup userGroup;
}
