package software.wings.graphql.datafetcher.user;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;

import software.wings.beans.User;
import software.wings.graphql.schema.type.QLUser;
import software.wings.graphql.schema.type.QLUser.QLUserBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
@TargetModule(Module._380_CG_GRAPHQL)
public class UserController {
  public static QLUser populateUser(EmbeddedUser user) {
    if (user == null) {
      return null;
    }
    return QLUser.builder().id(user.getUuid()).name(user.getName()).email(user.getEmail()).build();
  }

  public static QLUser populateUser(User user, QLUserBuilder builder) {
    if (user == null) {
      return null;
    }
    return builder.id(user.getUuid())
        .name(user.getName())
        .email(user.getEmail())
        .isEmailVerified(user.isEmailVerified())
        .isTwoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
        .isUserLocked(user.isUserLocked())
        .isPasswordExpired(user.isPasswordExpired())
        .isImportedFromIdentityProvider(user.isImported())
        .build();
  }
}
