package io.harness.ng.authenticationsettings.dtos.mechanisms;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.loginSettings.PasswordExpirationPolicy;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.loginSettings.UserLockoutPolicy;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PL)
public class UsernamePasswordSettings extends NGAuthSettings {
  @NotNull @Valid private UserLockoutPolicy userLockoutPolicy;
  @NotNull @Valid private PasswordExpirationPolicy passwordExpirationPolicy;
  @NotNull @Valid private PasswordStrengthPolicy passwordStrengthPolicy;
}
