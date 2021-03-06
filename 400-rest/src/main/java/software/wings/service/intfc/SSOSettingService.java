package software.wings.service.intfc;

import io.harness.validation.Create;

import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.OauthProviderType;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

// TODO: Create settings helper classes such as LdapHelper, SamlHelper, etc.
public interface SSOSettingService extends OwnedByAccount {
  SamlSettings getSamlSettingsByIdpUrl(@NotNull String idpUrl);

  SamlSettings getSamlSettingsByAccountId(@NotNull String accountId);

  OauthSettings getOauthSettingsByAccountId(String accountId);

  @ValidationGroups(Create.class) SamlSettings saveSamlSettings(@Valid SamlSettings settings);

  OauthSettings saveOauthSettings(OauthSettings settings);

  boolean deleteSamlSettings(@NotNull String accountId);

  boolean deleteSamlSettings(SamlSettings samlSettings);

  SamlSettings getSamlSettingsByOrigin(@NotNull String origin);

  Iterator<SamlSettings> getSamlSettingsIteratorByOrigin(@NotNull String origin);

  LdapSettings createLdapSettings(@NotNull LdapSettings settings);

  LdapSettings updateLdapSettings(@NotNull LdapSettings settings);

  LdapSettings deleteLdapSettings(@NotBlank String accountId);

  LdapSettings deleteLdapSettings(@NotNull LdapSettings settings);

  LdapSettings getLdapSettingsByAccountId(@NotBlank String accountId);

  LdapSettings getLdapSettingsByUuid(@NotBlank String uuid);

  boolean isLdapSettingsPresent(@NotBlank String uuid);

  SSOSettings getSsoSettings(@NotBlank String uuid);

  /**
   * Raise group sync alert specifying the cause of the failure
   *
   * @param accountId   account id
   * @param ssoId       sso id
   * @param message     failure message
   */
  void raiseSyncFailureAlert(@NotBlank String accountId, @NotBlank String ssoId, @NotBlank String message);

  /**
   * Close existing alert if open
   *
   * @param accountId   account id
   * @param ssoId       sso id
   */
  void closeSyncFailureAlertIfOpen(@NotBlank String accountId, @NotBlank String ssoId);

  /**
   * Send email/slack notification for default SSO Provider not being reachable from any of the delegates.
   *
   * @param accountId   account id
   * @param settings    sso settings
   */
  void sendSSONotReachableNotification(@NotBlank String accountId, @NotNull SSOSettings settings);

  /**
   * Check if the given sso settings are default authentication settings
   * @param accountId   account id
   * @param ssoId       sso id
   */
  boolean isDefault(@NotBlank String accountId, @NotBlank String ssoId);

  OauthSettings updateOauthSettings(String accountId, String filter, Set<OauthProviderType> allowedProviders);

  boolean deleteOauthSettings(String accountId);

  /**
   * Get ALL SSO Setting required for an account. Required for Community version.
   * @param accountId
   * @return
   */
  List<SSOSettings> getAllSsoSettings(String accountId);
}
