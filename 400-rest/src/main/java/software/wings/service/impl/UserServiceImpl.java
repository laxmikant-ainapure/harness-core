package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.HAS;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.app.ManagerCacheRegistrar.PRIMARY_CACHE_PREFIX;
import static software.wings.app.ManagerCacheRegistrar.USER_CACHE;
import static software.wings.beans.AccountRole.AccountRoleBuilder.anAccountRole;
import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.beans.ApplicationRole.ApplicationRoleBuilder.anApplicationRole;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;
import static software.wings.security.PermissionAttribute.ResourceType.ARTIFACT;
import static software.wings.security.PermissionAttribute.ResourceType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.ResourceType.ENVIRONMENT;
import static software.wings.security.PermissionAttribute.ResourceType.SERVICE;
import static software.wings.security.PermissionAttribute.ResourceType.WORKFLOW;
import static software.wings.security.authentication.AuthenticationMechanism.USER_PASSWORD;
import static software.wings.service.impl.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
import static software.wings.service.impl.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
import static software.wings.service.impl.InviteOperationResponse.FAIL;
import static software.wings.service.impl.InviteOperationResponse.USER_ALREADY_ADDED;
import static software.wings.service.impl.InviteOperationResponse.USER_ALREADY_INVITED;
import static software.wings.service.impl.InviteOperationResponse.USER_INVITED_SUCCESSFULLY;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.sql.Date.from;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mindrot.jbcrypt.BCrypt.hashpw;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.cache.HarnessCacheManager;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.ccm.license.CeLicenseType;
import io.harness.data.encoding.EncodingUtils;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.model.EventType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UserAlreadyPresentException;
import io.harness.exception.UserRegistrationException;
import io.harness.exception.WingsException;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.marketplace.gcp.procurement.GcpProcurementService;
import io.harness.persistence.UuidAware;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.KryoSerializer;
import io.harness.version.VersionInfoManager;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountJoinRequest;
import software.wings.beans.AccountRole;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.ApplicationRole;
import software.wings.beans.EmailVerificationToken;
import software.wings.beans.EmailVerificationToken.EmailVerificationTokenKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Event;
import software.wings.beans.Event.Type;
import software.wings.beans.LicenseInfo;
import software.wings.beans.MarketPlace;
import software.wings.beans.Role;
import software.wings.beans.TrialSignupOptions;
import software.wings.beans.TrialSignupOptions.Products;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteKeys;
import software.wings.beans.UserInviteSource;
import software.wings.beans.UserInviteSource.SourceType;
import software.wings.beans.ZendeskSsoLoginResponse;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.loginSettings.PasswordSource;
import software.wings.beans.loginSettings.PasswordStrengthViolations;
import software.wings.beans.loginSettings.UserLockoutInfo;
import software.wings.beans.marketplace.MarketPlaceConstants;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.licensing.LicenseService;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.SecretManager;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.AuthenticationMechanism;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.LogoutResponse;
import software.wings.security.authentication.OauthProviderType;
import software.wings.security.authentication.TOTPAuthHandler;
import software.wings.security.authentication.TwoFactorAuthenticationMechanism;
import software.wings.security.authentication.TwoFactorAuthenticationSettings;
import software.wings.security.authentication.oauth.OauthUserInfo;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.signup.SignupException;
import software.wings.service.intfc.signup.SignupSpamChecker;

import com.amazonaws.services.codedeploy.model.InvalidOperationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import graphql.VisibleForTesting;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.mindrot.jbcrypt.BCrypt;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Created by anubhaw on 3/9/16.
 */
@OwnedBy(PL)
@ValidateOnExecution
@Singleton
@Slf4j
public class UserServiceImpl implements UserService {
  static final String ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME = "add_group";
  static final String USER_PASSWORD_CHANGED_EMAIL_TEMPLATE_NAME = "password_changed";
  private static final String ADD_ACCOUNT_EMAIL_TEMPLATE_NAME = "add_account";
  public static final String SIGNUP_EMAIL_TEMPLATE_NAME = "signup";
  public static final String INVITE_EMAIL_TEMPLATE_NAME = "invite";
  private static final String JOIN_EXISTING_TEAM_TEMPLATE_NAME = "join_existing_team";
  public static final int REGISTRATION_SPAM_THRESHOLD = 3;
  private static final String EXC_MSG_RESET_PASS_LINK_NOT_GEN = "Reset password link could not be generated";
  private static final String EXC_MSG_USER_DOESNT_EXIST = "User does not exist";
  private static final String EXC_MSG_USER_INVITE_INVALID =
      "User was not invited to access account or the invitation is obsolete";
  private static final String EXC_USER_ALREADY_REGISTERED = "User is already registered";
  private static final String INCORRECT_PORTAL_SETUP = "Incorrect portal setup";
  private static final String RESET_ERROR = "Reset password email couldn't be sent";
  private static final String INVITE_URL_FORMAT = "/invite?email=%s&inviteId=%s";
  private static final String LOGIN_URL_FORMAT = "/login?company=%s&account=%s&email=%s";
  private static final String HARNESS_ISSUER = "Harness Inc";
  private static final int MINIMAL_ORDER_QUANTITY = 1;

  /**
   * The Executor service.
   */
  @Inject ExecutorService executorService;
  @Inject private UserServiceLimitChecker userServiceLimitChecker;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private EmailNotificationService emailNotificationService;
  @Inject private MainConfiguration configuration;
  @Inject private RoleService roleService;
  @Inject private AccountService accountService;
  @Inject private AuthService authService;
  @Inject private UserGroupService userGroupService;
  @Inject private HarnessUserGroupService harnessUserGroupService;
  @Inject private AppService appService;
  @Inject private AuthHandler authHandler;
  @Inject private SecretManager secretManager;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private AuthenticationManager authenticationManager;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private SSOService ssoService;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private GcpProcurementService gcpProcurementService;
  @Inject private SignupService signupService;
  @Inject private SignupSpamChecker spamChecker;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private TOTPAuthHandler totpAuthHandler;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private LicenseService licenseService;
  @Inject private HarnessCacheManager harnessCacheManager;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private ConfigurationController configurationController;

  private Cache<String, User> getUserCache() {
    if (configurationController.isPrimary()) {
      return harnessCacheManager.getCache(PRIMARY_CACHE_PREFIX + USER_CACHE, String.class, User.class,
          AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES));
    }
    return harnessCacheManager.getCache(USER_CACHE, String.class, User.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#register(software.wings.beans.User)
   */
  @Override
  public User register(User user) {
    if (isNotBlank(user.getEmail())) {
      user.setEmail(user.getEmail().trim().toLowerCase());
    }

    if (isNotBlank(user.getAccountName())) {
      user.setAccountName(user.getAccountName().trim());
    }

    if (isNotBlank(user.getName())) {
      user.setName(user.getName().trim());
    }

    if (isNotBlank(user.getCompanyName())) {
      user.setCompanyName(user.getCompanyName().trim());
    }

    verifyRegisteredOrAllowed(user.getEmail());

    Account account = setupTrialAccount(user.getAccountName(), user.getCompanyName());

    User savedUser = registerNewUser(user, account);
    executorService.execute(() -> sendVerificationEmail(savedUser));
    eventPublishHelper.publishUserInviteFromAccountEvent(account.getUuid(), savedUser.getEmail());
    return savedUser;
  }

  @Override
  public UserInvite createUserInviteForMarketPlace() {
    UserInvite userInvite = new UserInvite();
    userInvite.setSource(UserInviteSource.builder().type(SourceType.MARKETPLACE).build());
    userInvite.setCompleted(false);

    String inviteId = wingsPersistence.save(userInvite);
    userInvite.setUuid(inviteId);

    log.info("Created a new user invite {} for a signup request from market place", inviteId);

    return userInvite;
  }

  /*
   * This function validates the account Name give in the userInvite is correct
   *
   */
  @VisibleForTesting
  void validateAccountName(String companyName, String accountName) {
    Account account = Account.Builder.anAccount().withCompanyName(companyName).withAccountName(accountName).build();
    accountService.validateAccount(account);
  }

  @Override
  public boolean hasPermission(String accountId, PermissionType permissionType) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }
    return userHasPermission(accountId, user, permissionType);
  }

  @Override
  public boolean userHasPermission(String accountId, User user, PermissionType permissionType) {
    UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      return true;
    }

    if (!accountId.equals(userRequestContext.getAccountId())) {
      return false;
    }

    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    return userPermissionInfo.hasAccountPermission(permissionType);
  }

  /**
   * Trial/Freemium user invitation won't create account. The freemium account will be created only at time of
   * invitation completion.
   */
  @Override
  public boolean trialSignup(UserInvite userInvite) {
    final String emailAddress = userInvite.getEmail().toLowerCase();
    validateTrialSignup(emailAddress);
    signupService.validateName(userInvite.getName());
    signupService.validatePassword(userInvite.getPassword());

    UserInvite userInviteInDB = signupService.getUserInviteByEmail(emailAddress);

    Map<String, String> params = new HashMap<>();
    params.put("email", userInvite.getEmail());
    String url = "/activation.html";

    if (userInviteInDB == null) {
      userInvite.setSource(UserInviteSource.builder().type(SourceType.TRIAL).build());
      userInvite.setCompleted(false);
      String hashed = hashpw(new String(userInvite.getPassword()), BCrypt.gensalt());
      userInvite.setPasswordHash(hashed);

      // validate that the account name or the company name is correct
      validateAccountName(userInvite.getCompanyName(), userInvite.getCompanyName());

      String inviteId = wingsPersistence.save(userInvite);
      userInvite.setUuid(inviteId);
      params.put("userInviteId", inviteId);

      log.info("Created a new user invite {} for company {}", inviteId, userInvite.getCompanyName());

      // Send an email invitation for the trial user to finish up the sign-up with additional information
      // such as password, account/company name information.
      sendVerificationEmail(userInvite, url, params);
      eventPublishHelper.publishTrialUserSignupEvent(inviteId, emailAddress, userInvite);
    } else if (userInviteInDB.isCompleted()) {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }
      // HAR-7590: If user invite has completed. Send an email saying so and ask the user to login directly.
      signupService.sendTrialSignupCompletedEmail(userInviteInDB);
    } else {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }

      params.put("userInviteId", userInviteInDB.getUuid());
      // HAR-7250: If the user invite was not completed. Resend the verification/invitation email.
      sendVerificationEmail(userInviteInDB, url, params);
    }
    return true;
  }

  @Override
  public boolean accountJoinRequest(AccountJoinRequest accountJoinRequest) {
    final String emailAddress = accountJoinRequest.getEmail().toLowerCase();
    signupService.validateName(accountJoinRequest.getName());
    signupService.validateEmail(emailAddress);
    Map<String, String> params = new HashMap<>();
    params.put("email", emailAddress);
    params.put("name", accountJoinRequest.getName());
    params.put("url", "https://app.harness.io/#/login");
    params.put("companyName", accountJoinRequest.getCompanyName());
    params.put("note", accountJoinRequest.getNote());
    String accountAdminEmail = accountJoinRequest.getAccountAdminEmail();
    boolean hasAccountAdminEmail = isNotEmpty(accountAdminEmail);
    User user = hasAccountAdminEmail ? getUserByEmail(accountAdminEmail) : null;
    String supportEmail = configuration.getSupportEmail();
    String to = supportEmail;
    String msg = "";
    if (user != null) {
      boolean isValidUser = hasAccountAdminEmail && isUserVerified(user);
      boolean isAdmin = isValidUser && isUserAdminOfAnyAccount(user);

      if (isAdmin) {
        to = accountAdminEmail;
      } else if (isValidUser) {
        to = supportEmail;
        msg = "Recipient is not a Harness admin in production cluster - ";
      } else {
        to = supportEmail;
        msg = "Recipient is not a Harness user in production cluster - ";
      }
    } else {
      msg = "Recipient is not a Harness user in production cluster - ";
    }

    params.put("msg", msg);
    boolean emailSent = sendEmail(to, JOIN_EXISTING_TEAM_TEMPLATE_NAME, params);
    eventPublishHelper.publishJoinAccountEvent(
        emailAddress, accountJoinRequest.getName(), accountJoinRequest.getCompanyName());
    return emailSent;
  }

  @Override
  public boolean postCustomEvent(String accountId, String event) {
    eventPublishHelper.publishCustomEvent(accountId, event);
    return true;
  }

  private void validateTrialSignup(String email) {
    signupService.validateCluster();
    signupService.validateEmail(email);
  }

  @Override
  public Account addAccount(Account account, User user, boolean addUser) {
    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (!configuration.isTrialRegistrationAllowed()) {
      if (licenseInfo != null && (AccountType.TRIAL.equals(licenseInfo.getAccountType()))) {
        throw new InvalidRequestException("Cannot create a trial account in this cluster.");
      }
    }
    if (isNotBlank(account.getAccountName())) {
      account.setAccountName(account.getAccountName().trim());
    }

    if (isNotBlank(account.getCompanyName())) {
      account.setCompanyName(account.getCompanyName().trim());
    }

    if (licenseInfo != null && AccountType.TRIAL.equals(licenseInfo.getAccountType())
        && account.getTrialSignupOptions() == null) {
      account.setTrialSignupOptions(TrialSignupOptions.getDefaultTrialSignupOptions());
    }

    account = setupAccount(account);
    if (addUser) {
      addAccountAdminRole(user, account);
      authHandler.addUserToDefaultAccountAdminUserGroup(user, account, true);
      sendSuccessfullyAddedToNewAccountEmail(user, account);
      evictUserFromCache(user.getUuid());
    }
    return account;
  }

  @Override
  public String getUserInvitationId(String email) {
    if (isNotEmpty(email)) {
      List<UserInvite> userInviteList = wingsPersistence.createQuery(UserInvite.class)
                                            .filter(UserInviteKeys.email, email)
                                            .order(Sort.descending("createdAt"))
                                            .asList();
      if (isNotEmpty(userInviteList)) {
        return userInviteList.get(0).getUuid();
      }
    }
    return "";
  }

  @Override
  public User getUserSummary(User user) {
    if (user == null) {
      return null;
    }
    User userSummary = new User();
    userSummary.setName(user.getName());
    userSummary.setUuid(user.getUuid());
    userSummary.setEmail(user.getEmail());
    userSummary.setEmailVerified(user.isEmailVerified());
    userSummary.setTwoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled());
    userSummary.setUserLocked(user.isUserLocked());
    userSummary.setPasswordExpired(user.isPasswordExpired());
    userSummary.setImported(user.isImported());
    return userSummary;
  }

  @Override
  public List<User> getUserSummary(List<User> userList) {
    if (isEmpty(userList)) {
      return Collections.emptyList();
    }
    return userList.stream().map(this::getUserSummary).collect(toList());
  }

  private void sendSuccessfullyAddedToNewAccountEmail(User user, Account account) {
    try {
      String loginUrl = buildAbsoluteUrl(
          format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
          account.getUuid());

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", user.getName());
      templateModel.put("url", loginUrl);
      templateModel.put("company", account.getCompanyName());
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_ACCOUNT_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .system(false)
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);
      emailNotificationService.send(emailData);
    } catch (URISyntaxException use) {
      log.error("Add account email couldn't be sent for accountId={}", account.getUuid(), use);
    }
  }

  @Override
  public User registerNewUser(User user, Account account) {
    String accountId = account.getUuid();

    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(accountId, ActionType.CREATE_USER));

    User existingUser = getUserByEmail(user.getEmail());
    if (existingUser == null) {
      return LimitEnforcementUtils.withLimitCheck(checker, () -> {
        user.setAppId(GLOBAL_APP_ID);
        user.getAccounts().add(account);
        user.setEmailVerified(false);
        String hashed = hashpw(new String(user.getPassword()), BCrypt.gensalt());
        user.setPasswordHash(hashed);
        user.setPasswordChangedAt(System.currentTimeMillis());
        user.setRoles(newArrayList(roleService.getAccountAdminRole(account.getUuid())));
        return save(user, accountId);
      });

    } else {
      Map<String, Object> map = new HashMap<>();
      map.put("name", user.getName());
      map.put("passwordHash", hashpw(new String(user.getPassword()), BCrypt.gensalt()));
      wingsPersistence.updateFields(User.class, existingUser.getUuid(), map);
      return existingUser;
    }
  }

  @Override
  public User getUserByEmail(String email) {
    User user = null;
    if (isNotEmpty(email)) {
      user = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase()).get();
      loadSupportAccounts(user);
      if (user != null && isEmpty(user.getAccounts())) {
        user.setAccounts(newArrayList());
      }
      if (user != null && isEmpty(user.getPendingAccounts())) {
        user.setPendingAccounts(newArrayList());
      }
    }

    return user;
  }

  @Override
  public User getUserByEmail(String email, String accountId) {
    User user = null;
    if (isNotEmpty(email)) {
      Query<User> query = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase());
      query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
          query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
      user = query.get();
      loadSupportAccounts(user);
    }

    return user;
  }

  @Override
  public User getUserWithAcceptedInviteByEmail(String email, String accountId) {
    User user = null;
    if (isNotEmpty(email)) {
      Query<User> query = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase());
      query.criteria(UserKeys.accounts).hasThisOne(accountId);
      user = query.get();
      loadSupportAccounts(user);
    }

    return user;
  }

  @Override
  public UserInvite getUserInviteByEmailAndAccount(String email, String accountId) {
    return getUserInviteByEmailAndAccount(email, accountId, true);
  }

  private UserInvite getUserInviteByEmailAndAccount(String email, String accountId, boolean fetchPendingInvitesOnly) {
    UserInvite userInvite = null;
    if (isNotEmpty(email)) {
      Query<UserInvite> query = wingsPersistence.createQuery(UserInvite.class)
                                    .filter(UserInviteKeys.email, email)
                                    .filter(UserInviteKeys.accountId, accountId);

      if (!fetchPendingInvitesOnly) {
        query.filter(UserInviteKeys.completed, Boolean.FALSE);
      }

      userInvite = query.get();
    }
    return userInvite;
  }

  private void loadUserGroups(String accountId, User user) {
    List<UserGroup> userGroupList = getUserGroupsOfUser(accountId, user.getUuid(), false);
    user.setUserGroups(userGroupList);
  }

  private Map<String, String> getUserInvitationToSsoTemplateModel(Account account, User user)
      throws URISyntaxException {
    String loginUrl =
        buildAbsoluteUrl(format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
            account.getUuid());
    Map<String, String> model = new HashMap<>();
    model.put("name", user.getName());
    model.put("url", loginUrl);
    model.put("company", account.getCompanyName());
    model.put(
        "subject", "You have been invited to the " + account.getCompanyName().toUpperCase() + " account at Harness");
    model.put("email", user.getEmail());
    model.put("authenticationMechanism", account.getAuthenticationMechanism().getType());
    model.put("message", "You have been added to Harness Account: " + account.getAccountName());

    SSOSettings ssoSettings = getSSOSettings(account);
    model.put("ssoUrl", checkGetDomainName(account, ssoSettings.getUrl()));

    boolean shouldMailContainTwoFactorInfo = user.isTwoFactorAuthenticationEnabled();
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(shouldMailContainTwoFactorInfo));
    model.put("totpSecret", user.getTotpSecretKey());
    String otpUrl = totpAuthHandler.generateOtpUrl(account.getCompanyName(), user.getEmail(), user.getTotpSecretKey());
    model.put("totpUrl", otpUrl);

    return model;
  }

  private SSOSettings getSSOSettings(Account account) {
    AuthenticationMechanism authenticationMechanism = account.getAuthenticationMechanism();
    switch (authenticationMechanism) {
      case OAUTH: {
        return ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
      }
      case SAML: {
        return ssoSettingService.getSamlSettingsByAccountId(account.getUuid());
      }
      case LDAP: {
        return ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
      }
      default: {
        log.error("New authentication mechanism detected. Needs to handle the added role email template flow.");
        throw new GeneralException(
            "New authentication mechanism detected while getting SSOSettings for account=" + account.getUuid());
      }
    }
  }

  private void sendVerificationEmail(User user) {
    EmailVerificationToken emailVerificationToken =
        wingsPersistence.saveAndGet(EmailVerificationToken.class, new EmailVerificationToken(user.getUuid()));
    try {
      String verificationUrl =
          buildAbsoluteUrl(configuration.getPortal().getVerificationUrl() + "/" + emailVerificationToken.getToken(),
              user.getDefaultAccountId());

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", user.getName());
      templateModel.put("url", verificationUrl);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(SIGNUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error("Verification email couldn't be sent", e);
    }
  }

  private Account getPrimaryAccount(User user) {
    return user.getAccounts().get(0);
  }

  private String buildAbsoluteUrl(String fragment, String accountId) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  @Override
  public void verifyRegisteredOrAllowed(String email) {
    signupService.checkIfEmailIsValid(email);

    final String emailAddress = email.trim();
    User existingUser = getUserByEmail(emailAddress);
    if (existingUser != null && existingUser.isEmailVerified()) {
      throw new UserRegistrationException("User Already Registered", ErrorCode.USER_ALREADY_REGISTERED, USER);
    }
  }

  @Override
  public boolean resendVerificationEmail(String email) {
    User existingUser = getUserByEmail(email);
    if (existingUser == null) {
      throw new UserRegistrationException(EXC_MSG_USER_DOESNT_EXIST, ErrorCode.USER_DOES_NOT_EXIST, USER);
    }

    sendVerificationEmail(existingUser);
    return true;
  }

  @Override
  public boolean resendInvitationEmail(String accountId, String email) {
    log.info("Initiating resending invitation email for user invite for: {}", email);
    UserInvite savedUserInvite = getUserInviteByEmailAndAccount(email, accountId, true);
    if (savedUserInvite == null) {
      log.info("Resending invitation email failed. User invite for: {} does not exist.", email);
      throw new InvalidOperationException("UserInvite not found.");
    }
    User user = getUserByEmail(email);
    if (user == null) {
      throw new InvalidRequestException("user does not exist");
    }
    Account account = accountService.get(accountId);
    user = checkIfTwoFactorAuthenticationIsEnabledForAccount(user, account);
    sendNewInvitationMail(savedUserInvite, account, user);
    log.info("Resent invitation email for user: {}", email);
    return true;
  }

  @Override
  public boolean verifyToken(String emailToken) {
    EmailVerificationToken verificationToken = wingsPersistence.createQuery(EmailVerificationToken.class)
                                                   .filter("appId", GLOBAL_APP_ID)
                                                   .filter(EmailVerificationTokenKeys.token, emailToken)
                                                   .get();

    if (verificationToken == null) {
      throw new GeneralException("Email verification token is not found");
    }
    wingsPersistence.updateFields(User.class, verificationToken.getUserId(), ImmutableMap.of("emailVerified", true));
    wingsPersistence.delete(EmailVerificationToken.class, verificationToken.getUuid());
    return true;
  }

  @Override
  public List<InviteOperationResponse> inviteUsers(UserInvite userInvite) {
    String accountId = userInvite.getAccountId();

    List<InviteOperationResponse> inviteOperationResponses = new ArrayList<>();
    if (userInvite.getEmails().isEmpty()) {
      String message = "No email provided. Please provide vaild email info";
      throw new InvalidArgumentsException(message);
    }
    for (String email : userInvite.getEmails()) {
      UserInvite userInviteClone = kryoSerializer.clone(userInvite);
      userInviteClone.setEmail(email.trim());
      inviteOperationResponses.add(inviteUser(userInviteClone, true, false));
    }

    List<String> alreadyAddedUsers = new ArrayList<>();
    List<String> alreadyInvitedUsers = new ArrayList<>();
    IntStream.range(0, inviteOperationResponses.size()).forEach(i -> {
      InviteOperationResponse response = inviteOperationResponses.get(i);
      if (response == USER_ALREADY_ADDED) {
        alreadyAddedUsers.add(userInvite.getEmails().get(i));
      }
      if (response == USER_ALREADY_INVITED) {
        alreadyInvitedUsers.add(userInvite.getEmails().get(i));
      }
    });

    String message = "";
    if (isNotEmpty(alreadyAddedUsers)) {
      message += "User(s) with email: " + String.join(", ", alreadyAddedUsers)
          + " are already part of the account. Please change their user groups individually on user's page."
          + "\n";
    }
    if (isNotEmpty(alreadyInvitedUsers)) {
      message += "User(s) with email: " + String.join(", ", alreadyInvitedUsers)
          + " are already invited. Their user groups have been updated.";
    }
    if (!message.equals("")) {
      throw new UserAlreadyPresentException(message);
    }

    return inviteOperationResponses;
  }

  private void limitCheck(String accountId, UserInvite userInvite) {
    try {
      Account account = accountService.get(accountId);
      if (null == account) {
        log.error("No account found for accountId={}", accountId);
        return;
      }
      Query<User> query = getListUserQuery(accountId, true);
      query.criteria(UserKeys.disabled).notEqual(true);
      List<User> existingUsersAndInvites = query.asList();
      userServiceLimitChecker.limitCheck(
          accountId, existingUsersAndInvites, new HashSet<>(Arrays.asList(userInvite.getEmail())));
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      // catching this because we don't want to stop user invites due to failure in limit check
      log.error("Error while checking limits. accountId={}", accountId, e);
    }
  }

  @Override
  public InviteOperationResponse inviteUser(
      UserInvite userInvite, boolean isInviteAcceptanceRequired, boolean markEmailVerified) {
    signupService.checkIfEmailIsValid(userInvite.getEmail());

    String accountId = userInvite.getAccountId();
    limitCheck(accountId, userInvite);

    Account account = accountService.get(accountId);

    User user = getUserByEmail(userInvite.getEmail());
    boolean createNewUser = user == null;
    if (createNewUser) {
      user = anUser().build();
    }

    if (isUserAssignedToAccount(user, accountId)) {
      updateUserGroupsOfUser(user.getUuid(), userGroupService.getUserGroupsFromUserInvite(userInvite), accountId, true);
      return USER_ALREADY_ADDED;
    } else if (isUserInvitedToAccount(user, accountId)) {
      if (isInviteAcceptanceRequired) {
        updateUserInvite(userInvite);
        return USER_ALREADY_INVITED;
      }
      user.getPendingAccounts().remove(account);
      user.getAccounts().add(account);
    } else {
      if (isInviteAcceptanceRequired) {
        userInvite.setUuid(wingsPersistence.save(userInvite));
        user.getPendingAccounts().add(account);
      } else {
        user.getAccounts().add(account);
      }
    }

    user.setEmail(userInvite.getEmail().trim().toLowerCase());
    user.setName(userInvite.getName().trim());
    user.setGivenName(userInvite.getGivenName());
    user.setFamilyName(userInvite.getFamilyName());
    user.setRoles(Collections.emptyList());
    if (!user.isEmailVerified()) {
      user.setEmailVerified(markEmailVerified);
    }
    user.setAppId(GLOBAL_APP_ID);
    user.setImported(userInvite.getImportedByScim());

    user = save(user, accountId);
    user = checkIfTwoFactorAuthenticationIsEnabledForAccount(user, account);

    if (!isInviteAcceptanceRequired) {
      addUserToUserGroups(accountId, user, userInvite.getUserGroups(), false, true);
    }
    if (!isInviteAcceptanceRequired && accountService.isSSOEnabled(account)) {
      sendUserInvitationToOnlySsoAccountMail(account, user);
    } else {
      sendNewInvitationMail(userInvite, account, user);
    }

    auditServiceHelper.reportForAuditingUsingAccountId(
        accountId, null, user, createNewUser ? Type.CREATE : Type.UPDATE);
    eventPublishHelper.publishUserInviteFromAccountEvent(accountId, userInvite.getEmail());

    return USER_INVITED_SUCCESSFULLY;
  }

  private void updateUserInvite(UserInvite updatedUserInvite) {
    String userEmail = updatedUserInvite.getEmail();
    String accountId = updatedUserInvite.getAccountId();

    Account account = accountService.get(accountId);
    User user = getUserByEmail(userEmail);

    UserInvite existingInvite = getUserInviteByEmailAndAccount(userEmail, accountId);
    if (existingInvite != null && !areUserGroupsIdenticalForInvites(updatedUserInvite, existingInvite)) {
      wingsPersistence.updateField(
          UserInvite.class, existingInvite.getUuid(), "userGroups", updatedUserInvite.getUserGroups());
      sendNewInvitationMail(existingInvite, account, user);
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, updatedUserInvite, Type.UPDATE);
    }
  }

  private boolean areUserGroupsIdenticalForInvites(UserInvite userInvite1, UserInvite userInvite2) {
    if (userInvite1.getUserGroups().size() != userInvite2.getUserGroups().size()) {
      return false;
    }
    Set<String> ids1 = userInvite1.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet());
    Set<String> ids2 = userInvite2.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet());

    return ids1.equals(ids2);
  }

  @Override
  public void addUserToUserGroups(
      String accountId, User user, List<UserGroup> userGroups, boolean sendNotification, boolean toBeAudited) {
    if (isEmpty(userGroups)) {
      return;
    }

    Set<String> newUserGroups = Sets.newHashSet();
    for (UserGroup userGroup : userGroups) {
      if (!userGroup.hasMember(user)) {
        if (userGroup.getMemberIds() == null) {
          userGroup.setMemberIds(new ArrayList<>());
        }
        userGroup.getMemberIds().add(user.getUuid());
        userGroupService.updateMembers(userGroup, false, toBeAudited);
        NotificationSettings notificationSettings = userGroup.getNotificationSettings();
        if (notificationSettings == null) {
          log.error("Notification settings not found for user group id: [{}]", userGroup.getUuid());
        } else if (notificationSettings.isSendMailToNewMembers()) {
          newUserGroups.add(userGroup.getUuid());
        }
      }
    }

    if (sendNotification && isNotEmpty(newUserGroups)) {
      sendAddedGroupEmail(user, accountService.get(accountId), userGroups);
    }
  }

  private List<UserGroup> addUserToUserGroups(
      String accountId, User user, SetView<String> userGroupIds, boolean sendNotification) {
    if (isNotEmpty(userGroupIds)) {
      List<UserGroup> userGroups = getUserGroups(accountId, userGroupIds);
      addUserToUserGroups(accountId, user, userGroups, sendNotification, false);
      return userGroups;
    }
    return new ArrayList<>();
  }

  private List<UserGroup> removeUserFromUserGroups(
      String accountId, User user, SetView<String> userGroupIds, boolean sendNotification) {
    if (isNotEmpty(userGroupIds)) {
      List<UserGroup> userGroups = getUserGroups(accountId, userGroupIds);
      removeUserFromUserGroups(user, userGroups, sendNotification);
      return userGroups;
    }
    return new ArrayList<>();
  }

  private void removeUserFromUserGroups(User user, List<UserGroup> userGroups, boolean sendNotification) {
    if (isNotEmpty(userGroups)) {
      final User userFinal = user;
      userGroups.forEach(userGroup -> {
        List<String> userGroupMembers = userGroup.getMemberIds();
        if (userGroupMembers != null) {
          userGroupMembers.remove(userFinal.getUuid());
          userGroupService.updateMembers(userGroup, sendNotification, false);
        }
      });
    }
  }

  private void removeRelatedUserInvite(String accountId, String email) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class)
                                .filter(UserInviteKeys.email, email)
                                .filter(UserInviteKeys.accountId, accountId)
                                .get();
    if (userInvite != null) {
      wingsPersistence.delete(userInvite);
    }
  }

  private List<UserGroup> getUserGroupsOfUser(String accountId, String userId, boolean loadUsers) {
    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                             .addFilter(UserGroupKeys.memberIds, EQ, userId)
                                             .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, loadUsers);
    return pageResponse.getResponse();
  }

  private List<UserGroup> getUserGroups(String accountId, SetView<String> userGroupIds) {
    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .addFilter("_id", IN, userGroupIds.toArray())
                                             .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                             .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true);
    return pageResponse.getResponse();
  }

  private Map<String, String> getNewInvitationTemplateModel(UserInvite userInvite, Account account, User user)
      throws URISyntaxException {
    Map<String, String> model = new HashMap<>();
    String inviteUrl = getUserInviteUrl(userInvite, account);
    model.put("company", account.getCompanyName());
    model.put("accountname", account.getAccountName());
    model.put("name", userInvite.getEmail());
    model.put("url", inviteUrl);
    boolean shouldMailContainTwoFactorInfo = user.isTwoFactorAuthenticationEnabled();
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(shouldMailContainTwoFactorInfo));
    model.put("totpSecret", user.getTotpSecretKey());
    String otpUrl = totpAuthHandler.generateOtpUrl(account.getCompanyName(), user.getEmail(), user.getTotpSecretKey());
    model.put("totpUrl", otpUrl);
    return model;
  }

  @Override
  public String getUserInviteUrl(UserInvite userInvite, Account account) throws URISyntaxException {
    if (userInvite == null) {
      return null;
    }
    return buildAbsoluteUrl(
        format("/invite?accountId=%s&account=%s&company=%s&email=%s&inviteId=%s", account.getUuid(),
            account.getAccountName(), account.getCompanyName(), userInvite.getEmail(), userInvite.getUuid()),
        account.getUuid());
  }

  @Override
  public String getUserInviteUrl(UserInvite userInvite) throws URISyntaxException {
    if (userInvite == null) {
      return null;
    }
    return buildAbsoluteUrl(
        format(INVITE_URL_FORMAT, userInvite.getEmail(), userInvite.getUuid()), userInvite.getAccountId());
  }

  private Map<String, String> getEmailVerificationTemplateModel(
      String name, String url, Map<String, String> params, String accountId) {
    Map<String, String> model = new HashMap<>();
    model.put("name", name);
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    model.put("url", authenticationUtils.buildAbsoluteUrl(baseUrl, url, params).toString());
    return model;
  }

  @Override
  public void sendNewInvitationMail(UserInvite userInvite, Account account, User user) {
    try {
      Map<String, String> templateModel = getNewInvitationTemplateModel(userInvite, account, user);
      signupService.sendEmail(userInvite, INVITE_EMAIL_TEMPLATE_NAME, templateModel);
    } catch (URISyntaxException e) {
      log.error("Invitation email couldn't be sent for userInviteId={}, userId={} & accountId={}", userInvite.getUuid(),
          user.getUuid(), account.getUuid(), e);
    }
  }

  @Override
  public void sendVerificationEmail(UserInvite userInvite, String url, Map<String, String> params) {
    Map<String, String> templateModel =
        getEmailVerificationTemplateModel(userInvite.getName(), url, params, userInvite.getAccountId());
    signupService.sendTrialSignupVerificationEmail(userInvite, templateModel);
  }

  private boolean sendEmail(String toEmail, String templateName, Map<String, String> templateModel) {
    List<String> toList = new ArrayList<>();
    toList.add(toEmail);
    EmailData emailData =
        EmailData.builder().to(toList).templateName(templateName).templateModel(templateModel).build();
    emailData.setRetries(2);
    return emailNotificationService.send(emailData);
  }

  private Map<String, Object> getAddedUserGroupTemplateModel(User user, Account account, List<UserGroup> userGroups)
      throws URISyntaxException {
    List<String> userGroupNamesList = new ArrayList<>();
    userGroups.forEach(userGroup -> userGroupNamesList.add(userGroup.getName()));
    String loginUrl =
        buildAbsoluteUrl(format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
            account.getUuid());

    Map<String, Object> model = new HashMap<>();
    model.put("name", user.getName());
    model.put("url", loginUrl);
    model.put("company", account.getCompanyName());
    model.put(
        "subject", "You have been assigned new user groups in " + account.getCompanyName().toUpperCase() + " account");
    model.put("email", user.getEmail());
    model.put("authenticationMechanism", account.getAuthenticationMechanism().getType());
    model.put("message", "You have been assigned new user groups: " + String.join(",", userGroupNamesList));
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(false));

    // In case of username-password authentication mechanism, we don't need to add the SSO details in the email.
    if (account.getAuthenticationMechanism() == USER_PASSWORD) {
      return model;
    }

    SSOSettings ssoSettings;
    if (account.getAuthenticationMechanism() == AuthenticationMechanism.SAML) {
      ssoSettings = ssoSettingService.getSamlSettingsByAccountId(account.getUuid());
    } else if (account.getAuthenticationMechanism() == AuthenticationMechanism.LDAP) {
      ssoSettings = ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
    } else if (account.getAuthenticationMechanism() == AuthenticationMechanism.OAUTH) {
      ssoSettings = ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
    } else {
      log.error("New authentication mechanism detected. Needs to handle the added role email template flow.");
      throw new GeneralException("New authentication mechanism detected.");
    }
    model.put("ssoUrl", checkGetDomainName(account, ssoSettings.getUrl()));
    return model;
  }

  private String checkGetDomainName(Account account, String url) {
    try {
      String domainName = getDomainName(url);
      return domainName != null ? domainName : account.getAuthenticationMechanism().name();
    } catch (URISyntaxException use) {
      return account.getAuthenticationMechanism().name();
    }
  }

  private static String getDomainName(String url) throws URISyntaxException {
    URI uri = new URIBuilder(url).build();
    return uri.getHost();
  }

  @Override
  public void sendAddedGroupEmail(User user, Account account, List<UserGroup> userGroups) {
    try {
      Map<String, Object> templateModel = getAddedUserGroupTemplateModel(user, account, userGroups);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error("Add to User Groups email couldn't be sent for userId={} in accountId={}", user.getUuid(),
          account.getUuid(), e);
    }
  }

  @Override
  public PageResponse<UserInvite> listInvites(PageRequest<UserInvite> pageRequest) {
    return wingsPersistence.query(UserInvite.class, pageRequest);
  }

  @Override
  public UserInvite getInvite(String accountId, String inviteId) {
    return wingsPersistence.createQuery(UserInvite.class)
        .filter(UserInviteKeys.accountId, accountId)
        .filter(UserInvite.UUID_KEY, inviteId)
        .get();
  }

  private UserInvite getInvite(String inviteId) {
    return wingsPersistence.createQuery(UserInvite.class).filter(UserInvite.UUID_KEY, inviteId).get();
  }

  @Override
  public List<UserInvite> getInvitesFromAccountId(String accountId) {
    return wingsPersistence.createQuery(UserInvite.class).filter(UserInvite.ACCOUNT_ID_KEY2, accountId).asList();
  }

  @Override
  public InviteOperationResponse completeInvite(UserInvite userInvite) {
    log.info("Completing invite for inviteId: {}", userInvite.getUuid());
    UserInvite existingInvite = getInvite(userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    if (existingInvite.isCompleted()) {
      return FAIL;
    }
    signupService.validateName(userInvite.getName());
    if (userInvite.getPassword() == null) {
      throw new InvalidRequestException("User name/password is not provided", USER);
    }
    loginSettingsService.verifyPasswordStrength(
        accountService.get(userInvite.getAccountId()), userInvite.getPassword());

    User existingUser = getUserByEmail(existingInvite.getEmail());
    Account account = getAccountFromInviteId(existingInvite.getUuid());
    if (existingUser == null || account == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }

    AuthenticationMechanism authenticationMechanism =
        account.getAuthenticationMechanism() == null ? USER_PASSWORD : account.getAuthenticationMechanism();
    Preconditions.checkState(authenticationMechanism == USER_PASSWORD,
        "Invalid request. Complete invite should only be called if Auth Mechanism is UsePass");

    io.harness.data.structure.CollectionUtils.isPresent(
        existingUser.getPendingAccounts(), acc -> existingUser.getPendingAccounts().contains(account));

    List<Account> pendingAccounts = existingUser.getPendingAccounts();
    List<Account> accounts = existingUser.getAccounts();
    if ((isEmpty(pendingAccounts) || !pendingAccounts.contains(account))
        && (isEmpty(accounts) || !accounts.contains(account))) {
      log.error("Processing of InviteId: {} failed. Account missing in both pendingAccounts and accounts",
          userInvite.getUuid());
      throw new InvalidRequestException("Invite processing failed", USER);
    }

    moveAccountFromPendingToConfirmed(
        existingUser, account, userGroupService.getUserGroupsFromUserInvite(existingInvite), true);

    completeUserInfo(userInvite, existingUser);
    markUserInviteComplete(userInvite);
    eventPublishHelper.publishUserRegistrationCompletionEvent(userInvite.getAccountId(), existingUser);
    auditServiceHelper.reportForAuditingUsingAccountId(
        userInvite.getAccountId(), null, userInvite, Type.ACCEPTED_INVITE);
    log.info(
        "Auditing accepted invite for userInvite={} in account={}", userInvite.getName(), userInvite.getAccountName());
    return ACCOUNT_INVITE_ACCEPTED;
  }

  @Override
  public User completeInviteAndSignIn(UserInvite userInvite) {
    completeInvite(userInvite);
    return authenticationManager.defaultLogin(userInvite.getEmail(), String.valueOf(userInvite.getPassword()));
  }

  @Override
  public User completeTrialSignupAndSignIn(String userInviteId) {
    UserInvite userInvite = getInvite(userInviteId);
    if (userInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    return completeTrialSignupAndSignIn(userInvite);
  }

  @Override
  public User completePaidSignupAndSignIn(UserInvite userInvite) {
    String accountName = accountService.suggestAccountName(userInvite.getAccountName());
    String companyName = userInvite.getCompanyName();

    User user = Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(userInvite.getName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(accountName)
                    .companyName(companyName != null ? companyName : accountName)
                    .build();

    completeSignup(user, userInvite, getDefaultPaidLicense());
    return authenticationManager.defaultLoginUsingPasswordHash(userInvite.getEmail(), userInvite.getPasswordHash());
  }

  @Override
  public User completeTrialSignupAndSignIn(UserInvite userInvite) {
    String accountName = accountService.suggestAccountName(userInvite.getAccountName());
    String companyName = userInvite.getCompanyName();

    User user = Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(userInvite.getName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(accountName)
                    .companyName(companyName != null ? companyName : accountName)
                    .utmInfo(userInvite.getUtmInfo())
                    .build();

    completeSignup(user, userInvite, getTrialLicense(), false);

    return authenticationManager.defaultLoginUsingPasswordHash(userInvite.getEmail(), userInvite.getPasswordHash());
  }

  @Override
  public User completeMarketPlaceSignup(User user, UserInvite userInvite, MarketPlaceType marketPlaceType) {
    if (userInvite.getPassword() == null) {
      throw new InvalidArgumentsException(Pair.of("args", "Password needs to be specified to login"));
    }

    marketPlaceSignup(user, userInvite, marketPlaceType);
    return authenticationManager.defaultLogin(userInvite.getEmail(), String.valueOf(userInvite.getPassword()));
  }

  private void marketPlaceSignup(User user, final UserInvite userInvite, MarketPlaceType marketPlaceType) {
    validateUser(user);

    UserInvite existingInvite = wingsPersistence.get(UserInvite.class, userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    if (existingInvite.isCompleted()) {
      log.error("Unexpected state: Existing invite is already completed. ID = {}", userInvite.getUuid());
      return;
    }

    String email = user.getEmail();
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    if (userInvite.getMarketPlaceToken() == null) {
      throw new GeneralException(
          String.format("Marketplace token not found for User Invite Id : [{%s}]", userInvite.getUuid()));
    }

    Map<String, Claim> claims =
        secretManager.verifyJWTToken(userInvite.getMarketPlaceToken(), JWT_CATEGORY.MARKETPLACE_SIGNUP);
    String userInviteID = claims.get(MarketPlaceConstants.USERINVITE_ID_CLAIM_KEY).asString();
    if (!userInviteID.equals(userInvite.getUuid())) {
      throw new GeneralException(
          String.format("User Invite Id in claim: [{%s}] does not match the User Invite Id : [{%s}]", userInviteID,
              userInvite.getUuid()));
    }

    LicenseInfo licenseInfo;
    MarketPlace marketPlace;
    if (marketPlaceType == MarketPlaceType.GCP) {
      String gcpAccountId = JWT.decode(claims.get(MarketPlaceConstants.GCP_MARKETPLACE_TOKEN).asString()).getSubject();
      licenseInfo = LicenseInfo.builder()
                        .accountType(AccountType.PAID)
                        .licenseUnits(50)
                        .expiryTime(LicenseUtils.getDefaultPaidExpiryTime())
                        .accountStatus(AccountStatus.INACTIVE)
                        .build();
      wingsPersistence.save(GCPMarketplaceCustomer.builder()
                                .gcpAccountId(gcpAccountId)
                                .harnessAccountId(setupAccountForUser(user, userInvite, licenseInfo))
                                .build());
      gcpProcurementService.approveAccount(gcpAccountId);
    } else {
      String marketPlaceID = claims.get(MarketPlaceConstants.MARKETPLACE_ID_CLAIM_KEY).asString();
      marketPlace = wingsPersistence.get(MarketPlace.class, marketPlaceID);
      if (marketPlace == null) {
        throw new GeneralException(String.format("No MarketPlace found with marketPlaceID=[{%s}]", marketPlaceID));
      }
      String accountId = setupAccountBasedOnProduct(user, userInvite, marketPlace);

      marketPlace.setAccountId(accountId);
      wingsPersistence.save(marketPlace);
    }
  }

  private String setupAccountForUser(User user, UserInvite userInvite, LicenseInfo licenseInfo) {
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withLicenseInfo(licenseInfo)
                          .withAppId(GLOBAL_APP_ID)
                          .build();

    account = setupAccount(account);
    String accountId = account.getUuid();
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(accountId);
    saveUserAndUserGroups(user, account, accountAdminGroups);

    completeUserInviteForSignup(userInvite, accountId);
    return accountId;
  }

  private void saveUserAndUserGroups(User user, Account account, List<UserGroup> accountAdminGroups) {
    user.setAppId(GLOBAL_APP_ID);
    user.setEmail(user.getEmail().trim().toLowerCase());
    if (isEmpty(user.getPasswordHash())) {
      user.setPasswordHash(hashpw(new String(user.getPassword()), BCrypt.gensalt()));
    }
    user.setEmailVerified(true);
    user.getAccounts().add(account);
    user.setUserGroups(accountAdminGroups);

    save(user, account.getUuid());

    addUserToUserGroups(account.getUuid(), user, accountAdminGroups, false, false);
  }

  private void validateUser(User user) {
    if (user.getAccountName() == null || user.getCompanyName() == null) {
      throw new InvalidRequestException("Account/company name is not provided", USER);
    }
    if (isEmpty(user.getName()) || isEmpty(user.getPassword())) {
      throw new InvalidRequestException("User's name/password is not provided", USER);
    }
  }

  @Override
  public UserInvite completeSignup(User user, UserInvite userInvite, LicenseInfo licenseInfo) {
    return completeSignup(user, userInvite, licenseInfo, true);
  }

  @Override
  public UserInvite completeSignup(
      User user, UserInvite userInvite, LicenseInfo licenseInfo, boolean shouldCreateSampleApp) {
    if (user.getAccountName() == null || user.getCompanyName() == null) {
      throw new InvalidRequestException("Account/company name is not provided", USER);
    }
    if (isEmpty(user.getName()) || (isEmpty(user.getPassword()) && isEmpty(user.getPasswordHash()))) {
      throw new InvalidRequestException("User's name/password is not provided", USER);
    }

    UserInvite existingInvite = wingsPersistence.get(UserInvite.class, userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    } else if (existingInvite.isCompleted()) {
      return existingInvite;
    }

    String email = existingInvite.getEmail();
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withAppId(GLOBAL_APP_ID)
                          .withLicenseInfo(licenseInfo)
                          .build();

    TrialSignupOptions trialSignupOptions =
        new TrialSignupOptions(Products.getProductsFromFullNames(existingInvite.getFreemiumProducts()),
            existingInvite.getFreemiumAssistedOption());
    account.setTrialSignupOptions(trialSignupOptions);

    // Create an trial account which license expires in 15 days.
    account = setupAccount(account, shouldCreateSampleApp);
    String accountId = account.getUuid();

    // For trial user just signed up, it will be assigned to the account admin role.
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(accountId);

    completeUserInviteForSignup(userInvite, accountId);

    saveUserAndUserGroups(user, account, accountAdminGroups);

    eventPublishHelper.publishUserRegistrationCompletionEvent(accountId, user);
    if (userInvite.getAccountId() != null) {
      auditServiceHelper.reportForAuditingUsingAccountId(
          userInvite.getAccountId(), null, userInvite, Type.ACCEPTED_INVITE);
      log.info(
          "Auditing accepted invite for userInvite={} in account={}", userInvite.getName(), account.getAccountName());
    }
    return userInvite;
  }

  private LicenseInfo getTrialLicense() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    return licenseInfo;
  }

  private LicenseInfo getDefaultPaidLicense() {
    final Instant defaultExpiry = Instant.now().plus(365, ChronoUnit.DAYS);
    return LicenseInfo.builder()
        .accountType(AccountType.PAID)
        .licenseUnits(50)
        .expiryTime(from(defaultExpiry).getTime())
        .accountStatus(AccountStatus.ACTIVE)
        .build();
  }

  private void completeUserInviteForSignup(UserInvite userInvite, String accountId) {
    userInvite.setAccountId(accountId);
    userInvite.setCompleted(true);
    userInvite.setAgreement(true);

    // Update existing invite with the associated account ID and set the status to be completed.
    wingsPersistence.save(userInvite);
  }

  @Override
  public User signUpUserUsingOauth(OauthUserInfo userInfo, String oauthProviderName) {
    log.info("User not found in db. Creating an account for: [{}]", userInfo.getEmail());
    checkForFreemiumCluster();
    User user = createUser(userInfo, oauthProviderName);
    notNullOrEmptyCheck(user.getAccountName(), "Account/Company name");
    notNullOrEmptyCheck(user.getName(), "User's name");

    throwExceptionIfUserIsAlreadyRegistered(user.getEmail());

    TrialSignupOptions trialSignupOptions =
        new TrialSignupOptions(userInfo.getFreemiumProducts(), userInfo.getFreemiumAssistedOption());

    // Create a trial account whose license expires in 15 days.
    Account account = createAccountWithTrialLicense(user, trialSignupOptions);

    // For trial user just signed up, it will be assigned to the account admin role.
    user = assignUserToAccountAdminGroup(user, account);

    String accountId = account.getUuid();
    createSSOSettingsAndMarkAsDefaultAuthMechanism(accountId);

    // PL-2698: UI lead-update call will be called only if it's first login. Will need to
    // make sure the isFirstLogin is always derived from lastLogin value.
    boolean isFirstLogin = user.getLastLogin() == 0L;
    user.setFirstLogin(isFirstLogin);

    return user;
  }

  private void checkForFreemiumCluster() {
    // A safe check to ensure that no signup is allowed in the paid cluster.
    if (!configuration.isTrialRegistrationAllowed()) {
      throw new InvalidRequestException("Signup is not allowed in paid cluster");
    }
  }

  private User createUser(OauthUserInfo userInfo, String oauthProvider) {
    String email = userInfo.getEmail();
    final String companyName = accountService.suggestAccountName(userInfo.getName());
    return Builder.anUser()
        .email(email)
        .name(userInfo.getName())
        .accountName(companyName)
        .companyName(companyName)
        .emailVerified(true)
        .oauthProvider(oauthProvider)
        .utmInfo(userInfo.getUtmInfo())
        .build();
  }

  private void notNullOrEmptyCheck(String name, String errorSubject) {
    if (Strings.isNullOrEmpty(name)) {
      throw new InvalidRequestException(errorSubject + " is empty", USER);
    }
  }

  private User assignUserToAccountAdminGroup(User user, Account account) {
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(account.getUuid());

    user.setAppId(GLOBAL_APP_ID);
    user.getAccounts().add(account);
    user.setUserGroups(accountAdminGroups);
    user = save(user, account.getUuid());

    addUserToUserGroups(account.getUuid(), user, accountAdminGroups, false, false);

    return user;
  }

  private void throwExceptionIfUserIsAlreadyRegistered(final String email) {
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }
  }

  private Account createAccountWithTrialLicense(User user, TrialSignupOptions trialSignupOptions) {
    LicenseInfo licenseInfo = getTrialLicense();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withAppId(GLOBAL_APP_ID)
                          .withLicenseInfo(licenseInfo)
                          .build();

    account.setTrialSignupOptions(trialSignupOptions);

    account = setupAccount(account, false);
    return account;
  }

  private void createSSOSettingsAndMarkAsDefaultAuthMechanism(String accountId) {
    OauthSettings oauthSettings =
        OauthSettings.builder()
            .accountId(accountId)
            .displayName(
                Arrays.stream(OauthProviderType.values()).map(OauthProviderType::name).collect(Collectors.joining(",")))
            .allowedProviders(Arrays.stream(OauthProviderType.values()).collect(Collectors.toSet()))
            .build();
    ssoSettingService.saveOauthSettings(oauthSettings);
    log.info("Setting authentication mechanism as oauth for account id: {}", accountId);
    ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.OAUTH);
  }

  @Override
  public UserInvite deleteInvite(String accountId, String inviteId) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class)
                                .filter(ID_KEY, inviteId)
                                .filter(UserInviteKeys.accountId, accountId)
                                .get();
    if (userInvite != null) {
      wingsPersistence.delete(userInvite);
    }
    return userInvite;
  }

  @Override
  public boolean deleteInvites(String accountId, String email) {
    Query userInvitesQuery = wingsPersistence.createQuery(UserInvite.class)
                                 .filter(UserInviteKeys.accountId, accountId)
                                 .filter(UserInviteKeys.email, email);
    return wingsPersistence.delete(userInvitesQuery);
  }

  @Override
  public boolean resetPassword(String email) {
    User user = getUserByEmail(email);

    if (user == null) {
      return true;
    }

    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      String token = JWT.create()
                         .withIssuer(HARNESS_ISSUER)
                         .withIssuedAt(new Date())
                         .withExpiresAt(new Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000)) // 4 hrs
                         .withClaim("email", email)
                         .sign(algorithm);
      sendResetPasswordEmail(user, token);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
    return true;
  }

  private void sendPasswordChangeEmail(User user) {
    Map<String, Object> templateModel = new HashMap<>();
    templateModel.put("name", user.getName());
    templateModel.put("email", user.getEmail());
    List<String> toList = new ArrayList<>();
    toList.add(user.getEmail());
    EmailData emailData = EmailData.builder()
                              .to(toList)
                              .templateName(USER_PASSWORD_CHANGED_EMAIL_TEMPLATE_NAME)
                              .templateModel(templateModel)
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);
    emailNotificationService.send(emailData);
  }

  @Override
  public boolean updatePassword(String resetPasswordToken, char[] password) {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(HARNESS_ISSUER).build();
      verifier.verify(resetPasswordToken);
      JWT decode = JWT.decode(resetPasswordToken);
      String email = decode.getClaim("email").asString();
      User user = resetUserPassword(email, password, decode.getIssuedAt().getTime());
      sendPasswordChangeEmail(user);
      user.getAccounts().forEach(account
          -> auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Type.RESET_PASSWORD));
    } catch (UnsupportedEncodingException exception) {
      throw new GeneralException("Invalid reset password link");
    } catch (JWTVerificationException exception) {
      throw new UnauthorizedException("Token has expired", USER);
    }
    return true;
  }

  @Override
  public void logout(User user) {
    authService.invalidateToken(user.getToken());
    evictUserFromCache(user.getUuid());
  }

  @Override
  public LogoutResponse logout(String accountId, String userId) {
    LogoutResponse logoutResponse = new LogoutResponse();
    log.info("Sending logout response from manager for user {} in account {}", userId, accountId);
    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    log.info("Samlsettings from accountId is {}", samlSettings);
    if (samlSettings != null && samlSettings.getLogoutUrl() != null) {
      logoutResponse.setLogoutUrl(samlSettings.getLogoutUrl());
      log.info("Logout URL from accountId is {}", samlSettings.getLogoutUrl());
    }
    try {
      User user = get(accountId, userId);
      log.info("Invalidating token for {}", user);
      if (user != null) {
        logout(user);
      }
    } catch (Exception e) {
      log.error("Invalidation of token and cache clear wasn't done", e);
    }
    log.info("Logout Response from manager {}", logoutResponse);
    return logoutResponse;
  }

  private User resetUserPassword(String email, char[] password, long tokenIssuedAt) {
    User user = getUserByEmail(email);
    if (user == null) {
      throw new InvalidRequestException("Email doesn't exist");
    } else if (user.getPasswordChangedAt() > tokenIssuedAt) {
      throw new UnauthorizedException("Token has expired", USER);
    }
    loginSettingsService.verifyPasswordStrength(accountService.get(user.getDefaultAccountId()), password);
    String hashed = hashpw(new String(password), BCrypt.gensalt());
    wingsPersistence.update(user,
        wingsPersistence.createUpdateOperations(User.class)
            .set("passwordHash", hashed)
            .set("passwordExpired", false)
            .set("passwordChangedAt", System.currentTimeMillis()));
    executorService.submit(() -> authService.invalidateAllTokensForUser(user.getUuid()));
    return user;
  }

  @Override
  public boolean isTwoFactorEnabled(String accountId, String userId) {
    // Check if admin has 2FA enabled
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }
    return user.isTwoFactorAuthenticationEnabled();
  }

  @Override
  public boolean overrideTwoFactorforAccount(String accountId, boolean adminOverrideTwoFactorEnabled) {
    try {
      Query<User> updateQuery = wingsPersistence.createQuery(User.class);
      updateQuery.filter(UserKeys.accounts, accountId);
      if (updateQuery.count() > 0) {
        for (User user : updateQuery) {
          Account defaultAccount = authenticationUtils.getDefaultAccount(user);
          log.info("User {} default account Id is {}", user.getEmail(), defaultAccount.getUuid());
          if (defaultAccount.getUuid().equals(accountId) && !user.isTwoFactorAuthenticationEnabled()) {
            user = enableTwoFactorAuthenticationForUser(user, defaultAccount);
            log.info("Sending 2FA reset email to user {}", user.getEmail());
            totpAuthHandler.sendTwoFactorAuthenticationResetEmail(user);
          }
        }
      }
    } catch (Exception ex) {
      throw new GeneralException("Exception occurred while enforcing Two factor authentication for users");
    }

    return true;
  }

  private User enableTwoFactorAuthenticationForUser(User user, Account account) {
    log.info("Enabling 2FA for user {}", user.getEmail());
    TwoFactorAuthenticationSettings twoFactorAuthenticationSettings =
        totpAuthHandler.createTwoFactorAuthenticationSettings(user, account);
    twoFactorAuthenticationSettings.setTwoFactorAuthenticationEnabled(true);
    return updateTwoFactorAuthenticationSettings(user, twoFactorAuthenticationSettings);
  }

  /**
   * Checks if the user's default account has 2FA enabled account wide. If yes, then setup 2FA for the user if not
   * already set
   *
   * @param user
   * @param account
   */
  private User checkIfTwoFactorAuthenticationIsEnabledForAccount(User user, Account account) {
    String defaultAccountId = null;
    if (isNotEmpty(user.getAccounts())) {
      defaultAccountId = authenticationUtils.getDefaultAccount(user).getUuid();
    } else {
      defaultAccountId = account.getUuid();
    }
    log.info("User {} default account Id is {}", user.getEmail(), defaultAccountId);
    log.info("2FA enabled is {} account wide for account {}", account.isTwoFactorAdminEnforced(), account.getUuid());
    if (defaultAccountId.equals(account.getUuid()) && account.isTwoFactorAdminEnforced()
        && !user.isTwoFactorAuthenticationEnabled()) {
      user = enableTwoFactorAuthenticationForUser(user, account);
    }
    return user;
  }

  private void sendResetPasswordEmail(User user, String token) {
    try {
      String resetPasswordUrl = getResetPasswordUrl(token, user);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", user.getName());
      templateModel.put("url", resetPasswordUrl);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName("reset_password")
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  @Override
  public void sendUserInvitationToOnlySsoAccountMail(Account account, User user) {
    try {
      Map<String, String> templateModel = getUserInvitationToSsoTemplateModel(account, user);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException use) {
      log.error("User Invitation email to SSO couldn't be sent for userId={} in accountId={}", user.getUuid(),
          account.getUuid(), use);
    }
  }

  private String getResetPasswordUrl(String token, User user) throws URISyntaxException {
    String accountIdParam = "?accountId=" + user.getDefaultAccountId();
    return buildAbsoluteUrl("/reset-password/" + token + accountIdParam, user.getDefaultAccountId());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#matchPassword(java.lang.String, java.lang.String)
   */
  @Override
  public boolean matchPassword(char[] password, String hash) {
    return BCrypt.checkpw(new String(password), hash);
  }

  @Override
  public User save(User user, String accountId) {
    user = wingsPersistence.saveAndGet(User.class, user);
    evictUserFromCache(user.getUuid());
    eventPublishHelper.publishSetupRbacEvent(accountId, user.getUuid(), EntityType.USER);
    return user;
  }

  @Override
  public User updateUserProfile(@NotNull User user) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    if (user.getName() != null) {
      updateOperations.set(UserKeys.name, user.getName());
    } else {
      updateOperations.unset(UserKeys.name);
    }
    if (isNotEmpty(user.getAccounts())) {
      user.getAccounts().forEach(account -> {
        auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Event.Type.UPDATE);
        log.info(
            "Auditing updation of User Profile for user={} in account={}", user.getUuid(), account.getAccountName());
      });
    }
    return applyUpdateOperations(user, updateOperations);
  }

  @Override
  public User addEventToUserMarketoCampaigns(String userId, EventType eventType) {
    User user = this.getUserFromCacheOrDB(userId);
    Set<String> consolidatedReportedMarketoCampaigns = Sets.newHashSet();
    Set<String> reportedMarketoCampaigns = user.getReportedMarketoCampaigns();
    if (isNotEmpty(reportedMarketoCampaigns)) {
      consolidatedReportedMarketoCampaigns.addAll(reportedMarketoCampaigns);
    }

    consolidatedReportedMarketoCampaigns.add(eventType.name());
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.reportedMarketoCampaigns, consolidatedReportedMarketoCampaigns);

    return applyUpdateOperations(user, updateOperations);
  }

  @Override
  public User updateTwoFactorAuthenticationSettings(User user, TwoFactorAuthenticationSettings settings) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.twoFactorAuthenticationEnabled, settings.isTwoFactorAuthenticationEnabled());
    addTwoFactorAuthenticationOperation(settings.getMechanism(), updateOperations);
    addTotpSecretKeyOperation(settings.getTotpSecretKey(), updateOperations);
    return this.applyUpdateOperations(user, updateOperations);
  }

  private void addTwoFactorAuthenticationOperation(
      @NotNull TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism,
      @NotNull UpdateOperations<User> updateOperations) {
    if (twoFactorAuthenticationMechanism != null) {
      updateOperations.set(UserKeys.twoFactorAuthenticationMechanism, twoFactorAuthenticationMechanism);
    } else {
      updateOperations.unset(UserKeys.twoFactorAuthenticationMechanism);
    }
  }

  private void addTotpSecretKeyOperation(
      @NotNull String totpSecretKey, @NotNull UpdateOperations<User> updateOperations) {
    if (totpSecretKey != null) {
      updateOperations.set(UserKeys.totpSecretKey, totpSecretKey);
    } else {
      updateOperations.unset(UserKeys.totpSecretKey);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#update(software.wings.beans.User)
   */
  @Override
  public User update(User user) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);

    if (user.getPassword() != null && user.getPassword().length > 0) {
      updateOperations.set(UserKeys.passwordHash, hashpw(new String(user.getPassword()), BCrypt.gensalt()));
      updateOperations.set(UserKeys.passwordChangedAt, System.currentTimeMillis());
    }
    if (isNotEmpty(user.getRoles())) {
      updateOperations.set(UserKeys.roles, user.getRoles());
    }

    if (user.getName() != null) {
      updateOperations.set(UserKeys.name, user.getName());
    } else {
      updateOperations.unset(UserKeys.name);
    }

    updateOperations.set(UserKeys.twoFactorAuthenticationEnabled, user.isTwoFactorAuthenticationEnabled());
    addTwoFactorAuthenticationOperation(user.getTwoFactorAuthenticationMechanism(), updateOperations);
    addTotpSecretKeyOperation(user.getTotpSecretKey(), updateOperations);

    if (user.getMarketoLeadId() > 0) {
      updateOperations.set(UserKeys.marketoLeadId, user.getMarketoLeadId());
    }

    if (isNotEmpty(user.getReportedMarketoCampaigns())) {
      updateOperations.set(UserKeys.reportedMarketoCampaigns, user.getReportedMarketoCampaigns());
    }

    if (isNotEmpty(user.getSegmentIdentity())) {
      updateOperations.set(UserKeys.segmentIdentity, user.getSegmentIdentity());
    }

    if (isNotEmpty(user.getReportedSegmentTracks())) {
      updateOperations.set(UserKeys.reportedSegmentTracks, user.getReportedSegmentTracks());
    }

    if (user.getLastLogin() > 0L) {
      updateOperations.set(UserKeys.lastLogin, user.getLastLogin());
    }

    return applyUpdateOperations(user, updateOperations);
  }

  @Override
  public User unlockUser(String email, String accountId) {
    User user = getUserByEmail(email);
    Preconditions.checkNotNull(user, "User cannot be null");
    Preconditions.checkNotNull(user.getDefaultAccountId(), "Default account can't be null for any user");
    if (!user.getDefaultAccountId().equals(accountId)) {
      throw new UnauthorizedException("User not authorized", USER);
    }
    UpdateOperations<User> operations = wingsPersistence.createUpdateOperations(User.class);
    setUnset(operations, UserKeys.userLocked, false);
    setUnset(operations, UserKeys.userLockoutInfo, new UserLockoutInfo());
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Type.UNLOCK);
    log.info("Auditing unlocking of user={} in account={}", user.getName(), accountId);
    return applyUpdateOperations(user, operations);
  }

  @Override
  public User applyUpdateOperations(User user, UpdateOperations<User> updateOperations) {
    wingsPersistence.update(user, updateOperations);
    evictUserFromCache(user.getUuid());
    return wingsPersistence.getWithAppId(User.class, user.getAppId(), user.getUuid());
  }

  private void auditUserAdditionAndRemovalFromUserGroups(
      String accountId, User user, List<UserGroup> userGroupMemberAdditions, List<UserGroup> userGroupMemberDeletions) {
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Type.UPDATE);
    userGroupMemberAdditions.forEach(userGroupAdded
        -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, userGroupAdded, Type.ADD));
    userGroupMemberDeletions.forEach(userGroupDeleted
        -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, userGroupDeleted, Type.REMOVE));
  }

  @Override
  public User updateUserGroupsOfUser(
      String userId, List<UserGroup> userGroups, String accountId, boolean sendNotification) {
    User userFromDB = get(accountId, userId);
    List<UserGroup> oldUserGroups = userFromDB.getUserGroups();

    Set<String> oldUserGroupIds = oldUserGroups.stream().map(UserGroup::getUuid).collect(Collectors.toSet());
    Set<String> newUserGroupIds = userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toSet());

    SetView<String> userGroupMemberDeletions = Sets.difference(oldUserGroupIds, newUserGroupIds);
    SetView<String> userGroupMemberAdditions = Sets.difference(newUserGroupIds, oldUserGroupIds);

    List<UserGroup> userGroupsAdded =
        addUserToUserGroups(accountId, userFromDB, userGroupMemberAdditions, sendNotification);
    List<UserGroup> userGroupsDeleted =
        removeUserFromUserGroups(accountId, userFromDB, userGroupMemberDeletions, false);

    if (!userGroupsAdded.isEmpty() || !userGroupsDeleted.isEmpty()) {
      auditUserAdditionAndRemovalFromUserGroups(accountId, userFromDB, userGroupsAdded, userGroupsDeleted);
    }

    authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, Collections.singletonList(userId));
    return get(accountId, userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<User> list(PageRequest<User> pageRequest, boolean loadUserGroups) {
    String accountId = null;
    SearchFilter searchFilter = pageRequest.getFilters()
                                    .stream()
                                    .filter(filter -> filter.getFieldName().equals(UserKeys.accounts))
                                    .findFirst()
                                    .orElse(null);

    if (searchFilter != null) {
      Account account = (Account) searchFilter.getFieldValues()[0];
      accountId = account.getUuid();
    }

    PageResponse<User> pageResponse = wingsPersistence.query(User.class, pageRequest);
    if (loadUserGroups) {
      loadUserGroupsForUsers(pageResponse.getResponse(), accountId);
    }
    return pageResponse;
  }

  @Override
  public void loadUserGroupsForUsers(List<User> users, String accountId) {
    PageRequest<UserGroup> req = aPageRequest().addFilter(UserGroupKeys.accountId, EQ, accountId).build();
    PageResponse<UserGroup> res = userGroupService.list(accountId, req, false);
    List<UserGroup> allUserGroupList = res.getResponse();
    if (isEmpty(allUserGroupList)) {
      return;
    }

    Multimap<String, UserGroup> userUserGroupMap = HashMultimap.create();

    allUserGroupList.forEach(userGroup -> {
      List<String> memberIds = userGroup.getMemberIds();
      if (isEmpty(memberIds)) {
        return;
      }
      memberIds.forEach(userId -> userUserGroupMap.put(userId, userGroup));
    });

    users.forEach(user -> {
      Collection<UserGroup> userGroups = userUserGroupMap.get(user.getUuid());
      if (isEmpty(userGroups)) {
        user.setUserGroups(new ArrayList<>());
      } else {
        user.setUserGroups(new ArrayList<>(userGroups));
      }
    });
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#delete(java.lang.String)
   */
  @Override
  public void delete(String accountId, String userId) {
    deleteInternal(accountId, userId, true);
  }

  private void deleteInternal(String accountId, String userId, boolean updateUsergroup) {
    User user = get(userId);
    if (user.getAccounts() == null && user.getPendingAccounts() == null) {
      return;
    }

    // HAR-7189: If user removed, the corresponding user invite using the same email address should be removed.
    removeRelatedUserInvite(accountId, user.getEmail());

    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(accountId, ActionType.CREATE_USER));

    LimitEnforcementUtils.withCounterDecrement(checker, () -> {
      if (isNotEmpty(user.getAccounts())) {
        for (Account account : user.getAccounts()) {
          if (account.getUuid().equals(accountId)) {
            user.getAccounts().remove(account);
            break;
          }
        }
      }

      if (isNotEmpty(user.getPendingAccounts())) {
        for (Account account : user.getPendingAccounts()) {
          if (account.getUuid().equals(accountId)) {
            user.getPendingAccounts().remove(account);
            break;
          }
        }
      }

      if (user.getAccounts().isEmpty() && user.getPendingAccounts().isEmpty()
          && wingsPersistence.delete(User.class, userId)) {
        evictUserFromCache(userId);
        return;
      }

      if (user.getDefaultAccountId() != null && user.getDefaultAccountId().equals(accountId)) {
        setNewDefaultAccountId(user);
      }

      List<Role> accountRoles = roleService.getAccountRoles(accountId);
      if (accountRoles != null) {
        for (Role role : accountRoles) {
          user.getRoles().remove(role);
        }
      }

      if (updateUsergroup) {
        PageResponse<UserGroup> pageResponse = userGroupService.list(
            accountId, aPageRequest().addFilter(UserGroupKeys.memberIds, HAS, user.getUuid()).build(), true);
        List<UserGroup> userGroupList = pageResponse.getResponse();
        removeUserFromUserGroups(user, userGroupList, false);
      }

      UpdateOperations<User> updateOp = wingsPersistence.createUpdateOperations(User.class)
                                            .set(UserKeys.roles, user.getRoles())
                                            .set(UserKeys.accounts, user.getAccounts())
                                            .set(UserKeys.pendingAccounts, user.getPendingAccounts());
      if (user.getDefaultAccountId() != null) {
        updateOp.set(UserKeys.defaultAccountId, user.getDefaultAccountId());
      }
      Query<User> updateQuery = wingsPersistence.createQuery(User.class).filter(ID_KEY, userId);
      wingsPersistence.update(updateQuery, updateOp);

      evictUserFromCache(userId);
    });
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, user);
    log.info("Auditing deletion of user={} in account={}", user.getName(), accountId);
  }

  public void setNewDefaultAccountId(User user) {
    String newDefaultAccountId = user.getDefaultAccountCandidate();
    user.setDefaultAccountId(newDefaultAccountId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#get(java.lang.String)
   */
  @Override
  public User get(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }

    loadSupportAccounts(user);

    List<Account> accounts = user.getAccounts();
    if (isNotEmpty(accounts)) {
      accounts.forEach(account -> LicenseUtils.decryptLicenseInfo(account, false));
    }

    return user;
  }

  @Override
  public boolean isUserPresent(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    return user != null;
  }

  @Override
  public List<User> getUsers(List<String> userIds) {
    return wingsPersistence.createQuery(User.class).field("uuid").in(userIds).asList();
  }

  private void loadSupportAccounts(User user) {
    if (user == null) {
      return;
    }

    if (harnessUserGroupService.isHarnessSupportUser(user.getUuid())) {
      Set<String> excludeAccounts = user.getAccounts().stream().map(Account::getUuid).collect(Collectors.toSet());
      List<Account> accountList = harnessUserGroupService.listAllowedSupportAccounts(excludeAccounts);
      user.setSupportAccounts(accountList);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#get(java.lang.String, java.lang.String)
   */
  @Override
  public User get(String accountId, String userId) {
    User user = wingsPersistence.get(User.class, userId);
    boolean userBelongsToAccount = true;
    if (user != null) {
      userBelongsToAccount = user.getAccounts().stream().anyMatch(acc -> acc.getUuid().equals(accountId));
    }
    if (user == null || !userBelongsToAccount) {
      throw new InvalidRequestException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }

    loadSupportAccounts(user);
    loadUserGroups(accountId, user);
    return user;
  }

  @Override
  public User getUserFromCacheOrDB(String userId) {
    Cache<String, User> userCache = getUserCache();
    User user = null;
    try {
      user = userCache.get(userId);
    } catch (Exception ex) {
      log.error("Exception occurred while loading User from DB", ex);
    }
    if (user == null) {
      log.info("User [{}] not found in Cache. Load it from DB", userId);
      user = get(userId);
      try {
        userCache.put(user.getUuid(), user);
      } catch (Exception ex) {
        log.error("Exception occurred while putting User from DB to cache", ex);
      }
    }

    return user;
  }

  @Override
  public void evictUserFromCache(String userId) {
    getUserCache().remove(userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#addRole(java.lang.String, java.lang.String)
   */
  @Override
  public User addRole(String userId, String roleId) {
    ensureUserExists(userId);
    Role role = ensureRolePresent(roleId);

    UpdateOperations<User> updateOp =
        wingsPersistence.createUpdateOperations(User.class).addToSet(UserKeys.roles, role);
    Query<User> updateQuery = wingsPersistence.createQuery(User.class).filter(ID_KEY, userId);
    wingsPersistence.update(updateQuery, updateOp);
    evictUserFromCache(userId);
    return wingsPersistence.get(User.class, userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#revokeRole(java.lang.String, java.lang.String)
   */
  @Override
  public User revokeRole(String userId, String roleId) {
    ensureUserExists(userId);
    Role role = ensureRolePresent(roleId);

    UpdateOperations<User> updateOp =
        wingsPersistence.createUpdateOperations(User.class).removeAll(UserKeys.roles, role);
    Query<User> updateQuery = wingsPersistence.createQuery(User.class).filter(ID_KEY, userId);
    wingsPersistence.update(updateQuery, updateOp);
    evictUserFromCache(userId);
    return wingsPersistence.get(User.class, userId);
  }

  @Override
  public AccountRole getUserAccountRole(String userId, String accountId) {
    Account account = accountService.get(accountId);
    if (account == null) {
      String message = "Account [" + accountId + "] does not exist";
      log.warn(message);
      throw new GeneralException(message);
    }
    User user = get(userId);

    if (user.isAccountAdmin(accountId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : ResourceType.values()) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anAccountRole()
          .withAccountId(accountId)
          .withAccountName(account.getAccountName())
          .withAllApps(true)
          .withResourceAccess(builder.build())
          .build();

    } else if (user.isAllAppAdmin(accountId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anAccountRole()
          .withAccountId(accountId)
          .withAccountName(account.getAccountName())
          .withAllApps(true)
          .withResourceAccess(builder.build())
          .build();
    }
    return null;
  }

  @Override
  public ApplicationRole getUserApplicationRole(String userId, String appId) {
    Application application = appService.get(appId);
    User user = get(userId);
    if (user.isAccountAdmin(application.getAccountId()) || user.isAppAdmin(application.getAccountId(), appId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anApplicationRole()
          .withAppId(appId)
          .withAppName(application.getName())
          .withAllEnvironments(true)
          .withResourceAccess(builder.build())
          .build();
    }
    // TODO - for Prod support and non prod support
    return null;
  }

  @Override
  public ZendeskSsoLoginResponse generateZendeskSsoJwt(String returnToUrl) {
    String jwtZendeskSecret = configuration.getPortal().getJwtZendeskSecret();
    if (jwtZendeskSecret == null) {
      throw new InvalidRequestException("Request can not be completed. No Zendesk SSO secret found.");
    }

    User user = UserThreadLocal.get();

    // Given a user instance
    JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                                 .issueTime(new Date())
                                 .jwtID(UUID.randomUUID().toString())
                                 .claim(UserKeys.name, user.getName())
                                 .claim(UserKeys.email, user.getEmail())
                                 .build();

    // Create JWS header with HS256 algorithm
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).contentType("text/plain").build();

    // Create JWS object
    JWSObject jwsObject = new JWSObject(header, new Payload(jwtClaims.toJSONObject()));

    try {
      // Create HMAC signer
      JWSSigner signer = new MACSigner(jwtZendeskSecret.getBytes(UTF_8));
      jwsObject.sign(signer);
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new InvalidRequestException("Error signing JWT: " + ExceptionUtils.getMessage(e));
    }

    // Serialise to JWT compact form
    String jwtString = jwsObject.serialize();

    String redirectUrl = "https://"
        + "harnesssupport.zendesk.com/access/jwt?jwt=" + jwtString;

    if (returnToUrl != null) {
      redirectUrl += "&return_to=" + returnToUrl;
    }
    return ZendeskSsoLoginResponse.builder().redirectUrl(redirectUrl).userId(user.getUuid()).build();
  }

  private Role ensureRolePresent(String roleId) {
    Role role = roleService.get(roleId);
    if (role == null) {
      throw new GeneralException("Roles does not exist");
    }
    return role;
  }

  private void ensureUserExists(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }
  }

  private void addAccountAdminRole(User existingUser, Account account) {
    addAccountRoles(existingUser, account, newArrayList(roleService.getAccountAdminRole(account.getUuid())));
  }

  private void addAccountRoles(User existingUser, Account account, List<Role> roles) {
    UpdateOperations updateOperations = wingsPersistence.createUpdateOperations(User.class);
    if (isNotEmpty(roles)) {
      updateOperations.addToSet("roles", roles);
    }
    if (account != null) {
      updateOperations.addToSet(UserKeys.accounts, account);
    }
    wingsPersistence.update(wingsPersistence.createQuery(User.class)
                                .filter(UserKeys.email, existingUser.getEmail())
                                .filter("appId", existingUser.getAppId()),
        updateOperations);
  }

  private Account setupTrialAccount(String accountName, String companyName) {
    Account.Builder accountBuilder = Account.Builder.anAccount()
                                         .withAccountName(accountName)
                                         .withCompanyName(companyName)
                                         .withLicenseInfo(LicenseInfo.builder().accountType(AccountType.TRIAL).build());
    return setupAccount(accountBuilder.build(), false);
  }

  private Account setupAccount(Account account) {
    return setupAccount(account, true);
  }

  private Account setupAccount(Account account, boolean shouldCreateSampleApp) {
    // HAR-8645: Always set default appId for account creation to pass validation
    account.setAppId(GLOBAL_APP_ID);
    Account savedAccount = accountService.save(account, false, shouldCreateSampleApp);
    log.info("New account created with accountId {} and licenseType {}", account.getUuid(),
        account.getLicenseInfo().getAccountType());
    return savedAccount;
  }

  private List<UserGroup> getAccountAdminGroup(String accountId) {
    PageRequest<UserGroup> pageRequest =
        aPageRequest()
            .addFilter(UserGroup.ACCOUNT_ID_KEY, EQ, accountId)
            .addFilter(UserGroupKeys.name, EQ, UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
            .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true);
    return pageResponse.getResponse();
  }

  @Override
  public String generateJWTToken(User user, Map<String, String> claims, JWT_CATEGORY category) {
    String jwtPasswordSecret = secretManager.getJWTSecret(category);
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTCreator.Builder jwtBuilder =
          JWT.create()
              .withIssuer(HARNESS_ISSUER)
              .withIssuedAt(new Date())
              .withExpiresAt(new Date(System.currentTimeMillis() + category.getValidityDuration()));
      // User Principal needed in token for environments without gateway as this token will be sent back to different
      // microservices
      addUserPrincipal(claims, user);
      if (claims != null && claims.size() > 0) {
        claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken could not be generated");
    }
  }

  private void addUserPrincipal(Map<String, String> claims, User user) {
    UserPrincipal userPrincipal = new UserPrincipal(user.getUuid(), user.getDefaultAccountId());
    Map<String, String> userClaims = userPrincipal.getJWTClaims();
    if (userClaims != null) {
      claims.putAll(userClaims);
    }
  }

  @Override
  public User verifyJWTToken(String jwtToken, JWT_CATEGORY category) {
    String jwtPasswordSecret = secretManager.getJWTSecret(category);
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(HARNESS_ISSUER).build();
      verifier.verify(jwtToken);
      return getUserByEmail(JWT.decode(jwtToken).getClaim("email").asString());
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken validation failed");
    } catch (JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidCredentialsException("Invalid JWTToken received, failed to decode the token", USER);
    }
  }

  private boolean isUserAdminOfAnyAccount(User user) {
    return user.getAccounts().stream().anyMatch(account -> {
      List<UserGroup> userGroupList = userGroupService.listByAccountId(account.getUuid(), user, true);
      if (isNotEmpty(userGroupList)) {
        return userGroupList.stream().anyMatch(userGroup -> {
          AccountPermissions accountPermissions = userGroup.getAccountPermissions();
          if (accountPermissions != null) {
            Set<PermissionType> permissions = accountPermissions.getPermissions();
            if (isNotEmpty(permissions)) {
              return permissions.contains(PermissionType.USER_PERMISSION_MANAGEMENT);
            }
          }
          return false;
        });
      }
      return false;
    });
  }

  @Override
  public boolean isAccountAdmin(String accountId) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }
    return isAccountAdmin(user, accountId);
  }

  @Override
  public boolean isAccountAdmin(User user, String accountId) {
    UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      return true;
    }

    if (!accountId.equals(userRequestContext.getAccountId())) {
      return false;
    }

    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    return isUserAccountAdmin(userPermissionInfo, accountId);
  }

  @Override
  public boolean isUserAccountAdmin(UserPermissionInfo userPermissionInfo, String accountId) {
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    if (accountPermissionSummary == null) {
      return false;
    }
    Set<PermissionType> permissions = accountPermissionSummary.getPermissions();
    if (isEmpty(permissions)) {
      return false;
    }
    return permissions.contains(PermissionType.ACCOUNT_MANAGEMENT);
  }

  @Override
  public boolean isUserAssignedToAccount(User user, String accountId) {
    return user != null && isNotEmpty(user.getAccounts())
        && user.getAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
  }

  @Override
  public boolean isUserInvitedToAccount(User user, String accountId) {
    return user != null && isNotEmpty(user.getPendingAccounts())
        && user.getPendingAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
  }

  @Override
  public boolean isUserVerified(User user) {
    if (isNotEmpty(user.getAccounts())) {
      AuthenticationMechanism authenticationMechanism = getPrimaryAccount(user).getAuthenticationMechanism();
      return (authenticationMechanism != null && authenticationMechanism.getType().equals("SSO"))
          || user.isEmailVerified();
    }
    log.warn("User {} has no accounts associated", user.getEmail());
    return false;
  }

  @Override
  public List<User> getUsersOfAccount(String accountId) {
    Account account = accountService.get(accountId);
    PageRequest<User> pageRequest = aPageRequest().addFilter(UserKeys.accounts, HAS, account).build();
    PageResponse<User> pageResponse = wingsPersistence.query(User.class, pageRequest);
    return pageResponse.getResponse();
  }

  @Override
  public List<User> getUsersWithThisAsPrimaryAccount(String accountId) {
    return getUsersOfAccount(accountId)
        .stream()
        .filter(u -> u.getAccounts().get(0).getUuid().equals(accountId))
        .collect(Collectors.toList());
  }

  @Override
  public AuthenticationMechanism getAuthenticationMechanism(User user) {
    return getPrimaryAccount(user).getAuthenticationMechanism();
  }

  @Override
  public boolean setDefaultAccount(User user, String accountId) {
    // First we have to make sure the user is assigned to the account
    boolean userAssignedToAccount = isUserAssignedToAccount(user, accountId);
    if (userAssignedToAccount) {
      user.setDefaultAccountId(accountId);
      wingsPersistence.save(user);
      return true;
    } else {
      throw new InvalidRequestException("Can't set default account if the user is not associated with the account");
    }
  }

  @Override
  public void deleteByAccountId(String accountId) {
    Query<User> query = wingsPersistence.createQuery(User.class);
    query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
        query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    List<User> users = query.asList();
    for (User user : users) {
      deleteInternal(accountId, user.getUuid(), false);
    }
  }

  @Override
  public boolean updateLead(String email, String accountId) {
    User user = getUserByEmail(email);
    executorService.submit(() -> {
      try {
        Thread.sleep(30000);
      } catch (InterruptedException e) {
      }
      eventPublishHelper.publishUserRegistrationCompletionEvent(accountId, user);
    });
    return true;
  }

  @Override
  public boolean deleteUsersByEmailAddress(String accountId, List<String> usersToRetain) {
    if (CollectionUtils.isEmpty(usersToRetain)) {
      throw new IllegalArgumentException("'usersToRetain' is empty");
    }

    Set<String> usersToDelete = getUsersOfAccount(accountId)
                                    .stream()
                                    .filter(user -> !usersToRetain.contains(user.getEmail()))
                                    .map(UuidAware::getUuid)
                                    .collect(toSet());

    for (String userToDelete : usersToDelete) {
      delete(accountId, userToDelete);
    }

    return true;
  }

  @Override
  public boolean enableUser(String accountId, String userId, boolean enabled) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    } else if (!isUserAssignedToAccount(user, accountId)) {
      throw new InvalidRequestException("User is not assigned to account", USER);
    }
    if (user.isDisabled() == enabled) {
      user.setDisabled(!enabled);
      wingsPersistence.save(user);
      evictUserFromCache(user.getUuid());
      log.info("User {} is enabled: {}", user.getEmail(), enabled);
    }
    return true;
  }

  @Override
  public void sendPasswordExpirationWarning(String email, Integer passExpirationDays) {
    User user = getUserByEmail(email);

    if (user == null) {
      throw new InvalidRequestException(
          String.format("Email [%s] exist while sending mail for password expiration.", email));
    }

    String jwtPasswordSecret = getJwtSecret();

    try {
      String token = signupService.createSignupTokeFromSecret(jwtPasswordSecret, email, 1);
      sendPasswordExpirationWarningMail(user, token, passExpirationDays);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
  }

  @Override
  public String createSignupSecretToken(String email, Integer passExpirationDays) {
    String jwtPasswordSecret = getJwtSecret();
    try {
      return signupService.createSignupTokeFromSecret(jwtPasswordSecret, email, passExpirationDays);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new SignupException("Signup secret token can't be generated");
    }
  }

  private void sendPasswordExpirationWarningMail(User user, String token, Integer passExpirationDays) {
    try {
      String resetPasswordUrl = getResetPasswordUrl(token, user);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", user.getName());
      templateModel.put("url", resetPasswordUrl);
      templateModel.put("passExpirationDays", passExpirationDays.toString());

      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName("password_expiration_warning")
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  @Override
  public void sendPasswordExpirationMail(String email) {
    User user = getUserByEmail(email);

    if (user == null) {
      throw new InvalidRequestException(
          String.format("Email [%s] exist while sending mail for password expiration.", email));
    }

    String jwtPasswordSecret = getJwtSecret();

    try {
      String token = signupService.createSignupTokeFromSecret(jwtPasswordSecret, email, 1);
      sendPasswordExpirationMail(user, token);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
  }

  private void sendPasswordExpirationMail(User user, String token) {
    try {
      String resetPasswordUrl = getResetPasswordUrl(token, user);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("name", user.getName());
      templateModel.put("url", resetPasswordUrl);

      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName("password_expiration")
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  private String getJwtSecret() {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }
    return jwtPasswordSecret;
  }

  @Override
  public boolean passwordExpired(String email) {
    User user = getUserByEmail(email);
    Preconditions.checkNotNull(user, "User is null");
    return user.isPasswordExpired();
  }

  @Override
  public PasswordStrengthViolations checkPasswordViolations(
      String token, PasswordSource passwordSource, String password) {
    Account account = null;
    try {
      if (PasswordSource.PASSWORD_RESET_FLOW == passwordSource) {
        account = getAccountFromResetPasswordToken(token);
      } else if (PasswordSource.SIGN_UP_FLOW == passwordSource) {
        account = getAccountFromInviteId(token);
      } else {
        throw new InvalidRequestException("Incorrect password source provided.", USER);
      }
      return loginSettingsService.getPasswordStrengthCheckViolations(
          account, EncodingUtils.decodeBase64ToString(password).toCharArray());
    } catch (Exception ex) {
      log.warn("Password violation polling failed for token: [{}]", token, ex);
      throw new InvalidRequestException("Password violation polling failed", USER);
    }
  }

  private Account getAccountFromInviteId(String inviteId) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class).filter("_id", inviteId).get();
    return accountService.get(userInvite.getAccountId());
  }

  private Account getAccountFromResetPasswordToken(String resetPasswordToken) {
    User user = verifyJWTToken(resetPasswordToken, JWT_CATEGORY.PASSWORD_SECRET);
    // Adding this check for infer checks, this condition won't happen, as if the user is null
    // verifyJWTToken will throw a exception
    if (user == null) {
      return null;
    }
    return accountService.get(user.getDefaultAccountId());
  }

  @Override
  public void sendAccountLockedNotificationMail(User user, int lockoutExpirationTime) {
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("name", user.getName());
    templateModel.put("lockoutExpirationTime", Integer.toString(lockoutExpirationTime));

    List<String> toList = new ArrayList<>();
    toList.add(user.getEmail());

    EmailData emailData = EmailData.builder()
                              .to(toList)
                              .templateName("user_account_locked_notification")
                              .templateModel(templateModel)
                              .accountId(getPrimaryAccount(user).getUuid())
                              .build();

    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);

    emailNotificationService.send(emailData);
  }

  @Override
  public Account getAccountByIdIfExistsElseGetDefaultAccount(User user, Optional<String> accountId) {
    if (accountId.isPresent()) {
      // First check if the user is associated with the account.
      if (!isUserAssignedToAccount(user, accountId.get())) {
        throw new InvalidRequestException("User is not assigned to account", USER);
      }
      return accountService.get(accountId.get());
    } else {
      Account defaultAccount = accountService.get(user.getDefaultAccountId());
      if (null == defaultAccount) {
        return getPrimaryAccount(user);
      } else {
        return defaultAccount;
      }
    }
  }

  /**
   * User can NOT be disabled/enabled in account status change only when:
   * 1. User belongs to multiple accounts
   * 2. User belongs to Harness user group
   */
  @Override
  public boolean canEnableOrDisable(User user) {
    String email = user.getEmail();
    boolean associatedWithMultipleAccounts = user.getAccounts().size() > 1;
    log.info("User {} is associated with {} accounts", email, user.getAccounts().size());
    boolean isHarnessUser = harnessUserGroupService.isHarnessSupportUser(user.getUuid());
    log.info("User {} is in harness user group: {}", email, isHarnessUser);
    boolean result = !(associatedWithMultipleAccounts || isHarnessUser);
    log.info("User {} can be set to new disabled status: {}", email, result);
    return result;
  }

  @Override
  public String saveUserInvite(UserInvite userInvite) {
    return wingsPersistence.save(userInvite);
  }

  public List<User> listUsers(PageRequest pageRequest, String accountId, String searchTerm, Integer offset,
      Integer pageSize, boolean loadUserGroups) {
    Query<User> query;
    if (isNotEmpty(searchTerm)) {
      query = getSearchUserQuery(accountId, searchTerm);
    } else {
      query = getListUserQuery(accountId, true);
    }
    query.criteria(UserKeys.disabled).notEqual(true);
    applySortFilter(pageRequest, query);
    FindOptions findOptions = new FindOptions().skip(offset).limit(pageSize);
    List<User> userList = query.asList(findOptions);
    if (loadUserGroups) {
      loadUserGroupsForUsers(userList, accountId);
    }
    return userList;
  }

  private void applySortFilter(PageRequest pageRequest, Query<User> query) {
    List<String> fieldToSort = pageRequest.getUriInfo().getQueryParameters(true).get("sort[0][field]");
    if (fieldToSort == null) {
      return;
    }
    if (pageRequest.getUriInfo().getQueryParameters(true).get("sort[0][direction]").get(0).equals("ASC")) {
      query.order(Sort.ascending(fieldToSort.get(0)));
    } else {
      query.order(Sort.descending(fieldToSort.get(0)));
    }
  }

  public long getTotalUserCount(String accountId, boolean listPendingUsers) {
    Query<User> query = getListUserQuery(accountId, listPendingUsers);
    query.criteria(UserKeys.disabled).notEqual(true);
    return query.count();
  }

  private Query<User> getListUserQuery(String accountId, boolean listPendingUsers) {
    Query<User> listUserQuery = wingsPersistence.createQuery(User.class, excludeAuthority);

    if (listPendingUsers) {
      listUserQuery.or(listUserQuery.criteria(UserKeys.accounts).hasThisOne(accountId),
          listUserQuery.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    } else {
      listUserQuery.criteria(UserKeys.accounts).hasThisOne(accountId);
    }
    listUserQuery.order(Sort.descending("lastUpdatedAt"));
    return listUserQuery;
  }

  @VisibleForTesting
  Query<User> getSearchUserQuery(String accountId, String searchTerm) {
    Query<User> query = wingsPersistence.createQuery(User.class, excludeAuthority);

    CriteriaContainer nameCriterion = query.and(
        getSearchCriterion(query, UserKeys.name, searchTerm), query.criteria(UserKeys.accounts).hasThisOne(accountId));

    CriteriaContainer emailCriterion = query.and(
        getSearchCriterion(query, UserKeys.email, searchTerm), query.criteria(UserKeys.accounts).hasThisOne(accountId));

    CriteriaContainer emailCriterionForPendingUsers = query.and(getSearchCriterion(query, UserKeys.email, searchTerm),
        query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));

    query.or(nameCriterion, emailCriterion, emailCriterionForPendingUsers);
    return query;
  }

  private CriteriaContainer getSearchCriterion(Query<?> query, String fieldName, String searchTerm) {
    return query.criteria(fieldName).startsWithIgnoreCase(quote(searchTerm));
  }

  @Override
  public InviteOperationResponse checkInviteStatus(UserInvite userInvite) {
    UserInvite existingInvite = getInvite(userInvite.getUuid());
    if (existingInvite == null) {
      throw new NoSuchElementException(ErrorCode.USER_INVITATION_DOES_NOT_EXIST.toString());
    }
    User user = getUserByEmail(existingInvite.getEmail());
    Account account = accountService.get(userInvite.getAccountId());

    if (account == null || user == null) {
      throw new NoSuchElementException("User or account does not exist");
    }

    if (existingInvite.isCompleted()) {
      log.warn("User invite {} is already completed", userInvite.getUuid());
      return FAIL;
    }

    eventPublishHelper.publishUserInviteVerifiedFromAccountEvent(account.getUuid(), user.getEmail());

    AuthenticationMechanism authMechanism = account.getAuthenticationMechanism();
    boolean isPasswordRequired =
        (authMechanism == null || authMechanism == USER_PASSWORD) && isEmpty(user.getPasswordHash());

    if (isPasswordRequired) {
      log.info("Redirecting invite id: {} to password signup page", existingInvite.getUuid());
      return ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
    } else {
      // Marking the user invite complete.
      moveAccountFromPendingToConfirmed(
          user, account, userGroupService.getUserGroupsFromUserInvite(existingInvite), true);
      markUserInviteComplete(existingInvite);
      return ACCOUNT_INVITE_ACCEPTED;
    }
  }

  private void moveAccountFromPendingToConfirmed(
      User existingUser, Account invitationAccount, List<UserGroup> userGroups, boolean markEmailVerified) {
    addUserToUserGroups(invitationAccount.getUuid(), existingUser, userGroups, false, false);

    List<Account> newAccountsList = new ArrayList<>(existingUser.getAccounts());
    newAccountsList.add(invitationAccount);

    List<Account> newPendingAccountsList = new ArrayList<>();
    if (existingUser.getPendingAccounts() != null) {
      for (Account pendingAccount : existingUser.getPendingAccounts()) {
        if (!pendingAccount.getUuid().equals(invitationAccount.getUuid())) {
          newPendingAccountsList.add(pendingAccount);
        }
      }
    }

    Map<String, Object> map = new HashMap<>();
    map.put(UserKeys.accounts, newAccountsList);
    map.put(UserKeys.pendingAccounts, newPendingAccountsList);
    map.put(UserKeys.emailVerified, markEmailVerified);
    wingsPersistence.updateFields(User.class, existingUser.getUuid(), map);
  }

  private void markUserInviteComplete(UserInvite userInvite) {
    Map<String, Object> map = new HashMap<>();
    map.put(UserInviteKeys.completed, Boolean.TRUE);
    map.put(UserInviteKeys.agreement, userInvite.isAgreement());
    wingsPersistence.updateFields(UserInvite.class, userInvite.getUuid(), map);
  }

  private void completeUserInfo(UserInvite userInvite, User existingUser) {
    Map<String, Object> map = new HashMap<>();
    map.put(UserKeys.name, userInvite.getName().trim());
    map.put(UserKeys.passwordHash, hashpw(new String(userInvite.getPassword()), BCrypt.gensalt()));
    wingsPersistence.updateFields(User.class, existingUser.getUuid(), map);
  }

  private String setupAccountBasedOnProduct(User user, UserInvite userInvite, MarketPlace marketPlace) {
    LicenseInfo licenseInfo = LicenseInfo.builder()
                                  .accountType(AccountType.PAID)
                                  .licenseUnits(marketPlace.getOrderQuantity())
                                  .expiryTime(marketPlace.getExpirationDate().getTime())
                                  .accountStatus(AccountStatus.ACTIVE)
                                  .build();
    if (marketPlace.getProductCode().equals(configuration.getMarketPlaceConfig().getAwsMarketPlaceProductCode())) {
      return setupAccountForUser(user, userInvite, licenseInfo);
    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceCeProductCode())) {
      licenseInfo.setAccountType(AccountType.TRIAL);
      licenseInfo.setLicenseUnits(MINIMAL_ORDER_QUANTITY);
      String accountId = setupAccountForUser(user, userInvite, licenseInfo);
      licenseService.updateCeLicense(accountId,
          CeLicenseInfo.builder()
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .licenseType(CeLicenseType.PAID)
              .build());
      return accountId;
    } else {
      throw new InvalidRequestException("Cannot resolve AWS marketplace order");
    }
  }
}
