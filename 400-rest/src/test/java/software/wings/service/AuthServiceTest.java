package software.wings.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.cache.HarnessCacheManager;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTokenException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.TokenGenerator;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.AuthToken;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.Workflow;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.GenericDbCache;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mongodb.morphia.AdvancedDatastore;

/**
 * Created by anubhaw on 8/31/16.
 */
public class AuthServiceTest extends WingsBaseTest {
  private final String VALID_TOKEN = "VALID_TOKEN";
  private final String INVALID_TOKEN = "INVALID_TOKEN";
  private final String EXPIRED_TOKEN = "EXPIRED_TOKEN";
  private final String NOT_AVAILABLE_TOKEN = "NOT_AVAILABLE_TOKEN";
  private final String AUTH_SECRET = "AUTH_SECRET";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Mock private GenericDbCache cache;
  @Mock private Cache<String, User> userCache;
  @Mock private Cache<String, AuthToken> authTokenCache;
  @Mock private HPersistence persistence;
  @Mock private AdvancedDatastore advancedDatastore;
  @Mock private AccountService accountService;
  @Mock private SegmentHandler segmentHandler;
  @Mock FeatureFlagService featureFlagService;
  @Mock private ConfigurationController configurationController;
  @Mock private HarnessCacheManager harnessCacheManager;
  @Mock PortalConfig portalConfig;
  @Inject MainConfiguration mainConfiguration;
  @Inject @InjectMocks private UserService userService;
  @Inject @InjectMocks private AuthService authService;

  private Builder userBuilder = anUser().appId(APP_ID).email(USER_EMAIL).name(USER_NAME).password(PASSWORD);
  private String accountKey = "2f6b0988b6fb3370073c3d0505baee59";

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    initMocks(this);
    on(mainConfiguration).set("portal", portalConfig);
    when(configurationController.isPrimary()).thenReturn(true);
    when(harnessCacheManager.getCache(anyString(), eq(String.class), eq(User.class), any())).thenReturn(userCache);
    when(userCache.get(USER_ID)).thenReturn(User.Builder.anUser().uuid(USER_ID).build());
    when(authTokenCache.get(VALID_TOKEN)).thenReturn(new AuthToken(ACCOUNT_ID, USER_ID, 86400000L));
    when(authTokenCache.get(EXPIRED_TOKEN)).thenReturn(new AuthToken(ACCOUNT_ID, USER_ID, 0L));
    when(cache.get(Application.class, APP_ID)).thenReturn(anApplication().uuid(APP_ID).appId(APP_ID).build());
    when(cache.get(Environment.class, ENV_ID))
        .thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).environmentType(EnvironmentType.NON_PROD).build());
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withUuid(ACCOUNT_ID).withAccountKey(accountKey).build());
    when(cache.get(Account.class, ACCOUNT_ID))
        .thenReturn(anAccount().withUuid(ACCOUNT_ID).withAccountKey(accountKey).build());
    when(portalConfig.getJwtAuthSecret()).thenReturn(AUTH_SECRET);

    when(persistence.getDatastore(AuthToken.class)).thenReturn(advancedDatastore);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    workflowExecutePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), null);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    workflowExecutePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    pipelineExecutePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), null);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    pipelineExecutePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  /**
   * Test whether auth token is fetched from db if its not available in cache
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testAuthTokenNotAvailableInCache() {
    AuthToken authTokenInDB = new AuthToken(ACCOUNT_ID, USER_ID, 86400000L);
    when(advancedDatastore.get(AuthToken.class, NOT_AVAILABLE_TOKEN)).thenReturn(authTokenInDB);
    AuthToken authToken = authService.validateToken(NOT_AVAILABLE_TOKEN);
    assertThat(authToken).isNotNull().isInstanceOf(AuthToken.class);
    assertThat(authToken).isEqualTo(authTokenInDB);
    verify(advancedDatastore, times(1)).get(AuthToken.class, NOT_AVAILABLE_TOKEN);
  }

  /**
   * Should validate valid token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldValidateValidToken() {
    AuthToken authToken = authService.validateToken(VALID_TOKEN);
    assertThat(authToken).isNotNull().isInstanceOf(AuthToken.class);
  }

  /**
   * Should throw invalid token exception for invalid token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowInvalidTokenExceptionForInvalidToken() {
    assertThatThrownBy(() -> authService.validateToken(INVALID_TOKEN))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.INVALID_TOKEN.name());
  }

  /**
   * Should throw expired token exception for expired token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExpiredTokenExceptionForExpiredToken() {
    assertThatThrownBy(() -> authService.validateToken(EXPIRED_TOKEN))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.EXPIRED_TOKEN.name());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldAuthorizeWithAccountAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.ACCOUNT_ADMIN).build();
    User user = userBuilder.but().roles(asList(role)).build();
    String appId = null;
    try {
      authService.authorize(
          ACCOUNT_ID, appId, null, user, asList(new PermissionAttribute(ResourceType.USER, Action.READ)), null);
    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWithoutAccountAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    String appId = null;
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, appId, null, user,
                               asList(new PermissionAttribute(ResourceType.USER, Action.READ)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAuthorizeWithAppAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    authService.authorize(
        ACCOUNT_ID, APP_ID, null, user, asList(new PermissionAttribute(ResourceType.ARTIFACT, Action.UPDATE)), null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAuthorizeReadWithEnvAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.NON_PROD_SUPPORT).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    authService.authorize(
        ACCOUNT_ID, APP_ID, ENV_ID, user, asList(new PermissionAttribute(ResourceType.APPLICATION, Action.READ)), null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWithDiffAppAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).withAppId("APP_ID2").build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, APP_ID, null, user,
                               asList(new PermissionAttribute(ResourceType.APPLICATION, Action.READ)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWriteWithEnvAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.NON_PROD_SUPPORT).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, APP_ID, ENV_ID, user,
                               asList(new PermissionAttribute(ResourceType.APPLICATION, Action.UPDATE)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldValidateDelegateToken() {
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    authService.validateDelegateToken(ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname"));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldNotValidateDelegateToken() {
    TokenGenerator tokenGenerator = new TokenGenerator(GLOBAL_ACCOUNT_ID, accountKey);
    assertThatThrownBy(()
                           -> authService.validateDelegateToken(
                               GLOBAL_ACCOUNT_ID, tokenGenerator.getToken("https", "localhost", 9090, "hostname")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotValidateExpiredDelegateToken() {
    String expiredToken =
        "eyJlbmMiOiJBMTI4R0NNIiwiYWxnIjoiZGlyIn0..SFvYSml0znPxoa7K.JcsFw5GiYevubqqzjy-nQyDMzjtA64YhxZjnQz6VH7lRCAGP5JML9Ov86rSRV1V7Kb-a12UvTNzqEqdJ4PCLv4R7GA5SzCwxLEYrlTLtUWX40r0GKuRGoiJVJqax2bBy3gOqDftETZCm_90lD3NxDeJ__RICl4osp9IxCKmlfGyoqriAswoEvkVtu0wjRlvBS-FtY42AeyCf9XIH5rppw-AsXoHH40M6_8FN-mFkilfqv3QKPaGL6Zph.1ipAjbMS834AKSotvHy4sg";
    assertThatThrownBy(() -> authService.validateDelegateToken(ACCOUNT_ID, expiredToken))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unauthorized");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldThrowDenyAccessWhenAccountIdNullForDelegate() {
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    assertThatThrownBy(
        () -> authService.validateDelegateToken(null, tokenGenerator.getToken("https", "localhost", 9090, "hostname")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldThrowDenyAccessWhenAccountIdNotFoundForDelegate() {
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, accountKey);
    assertThatThrownBy(()
                           -> authService.validateDelegateToken(
                               ACCOUNT_ID + "1", tokenGenerator.getToken("https", "localhost", 9090, "hostname")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Access denied");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowThrowInavlidTokenForDelegate() {
    assertThatThrownBy(() -> authService.validateDelegateToken(ACCOUNT_ID, "Dummy"))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenUnableToDecryptToken() {
    assertThatThrownBy(() -> authService.validateDelegateToken(ACCOUNT_ID, getDelegateToken()))
        .isInstanceOf(InvalidTokenException.class);
  }

  private String getDelegateToken() {
    KeyGenerator keyGen = null;
    try {
      keyGen = KeyGenerator.getInstance("AES");
    } catch (NoSuchAlgorithmException e) {
      throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE);
    }
    keyGen.init(128);
    SecretKey secretKey = keyGen.generateKey();
    byte[] encoded = secretKey.getEncoded();
    TokenGenerator tokenGenerator = new TokenGenerator(ACCOUNT_ID, Hex.encodeHexString(encoded));
    return tokenGenerator.getToken("https", "localhost", 9090, "hostname");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGenerateBearerTokenWithJWTToken() throws UnsupportedEncodingException {
    when(featureFlagService.isEnabled(Matchers.any(FeatureName.class), anyString())).thenReturn(true);
    Account mockAccount = Account.Builder.anAccount().withAccountKey("TestAccount").build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    mockUser.setUuid("kmpySmUISimoRrJL6NL73w");
    when(userCache.get(USER_ID)).thenReturn(mockUser);
    User user = authService.generateBearerTokenForUser(mockUser);
    assertThat(user.getToken().length()).isGreaterThan(32);

    Algorithm algorithm = Algorithm.HMAC256(AUTH_SECRET);
    JWTVerifier verifier = JWT.require(algorithm).withIssuer("Harness Inc").build();
    String authTokenId = JWT.decode(user.getToken()).getClaim("authToken").asString();

    String tokenString = user.getToken();
    AuthToken authToken = new AuthToken(ACCOUNT_ID, USER_ID, 8640000L);
    authToken.setJwtToken(user.getToken());
    when(authTokenCache.get(authTokenId)).thenReturn(authToken);
    assertThat(authService.validateToken(tokenString)).isEqualTo(authToken);

    try {
      authService.validateToken(tokenString + "FakeToken");
      fail("WingsException should have been thrown");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIAL.name());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGenerateBearerTokenWithoutJWTToken() {
    when(featureFlagService.isEnabled(Matchers.any(FeatureName.class), anyString())).thenReturn(false);
    Account mockAccount = Account.Builder.anAccount().withAccountKey("TestAccount").build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    mockUser.setUuid("kmpySmUISimoRrJL6NL73w");
    when(userCache.get(USER_ID)).thenReturn(mockUser);
    User user = authService.generateBearerTokenForUser(mockUser);
    AuthToken authToken = new AuthToken(ACCOUNT_ID, USER_ID, 8640000L);
    JWT jwt = JWT.decode(user.getToken());
    String authTokenUuid = jwt.getClaim("authToken").asString();
    when(cache.get(Matchers.any(), Matchers.matches(authTokenUuid))).thenReturn(authToken);
    when(authTokenCache.get(authTokenUuid)).thenReturn(authToken);
    assertThat(user.getToken().length()).isGreaterThan(32);
    authService.validateToken(user.getToken());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSendSegmentTrackEvent() throws IllegalAccessException {
    when(featureFlagService.isEnabled(Matchers.any(FeatureName.class), anyString())).thenReturn(false);
    Account mockAccount = Account.Builder.anAccount().withAccountKey("TestAccount").withUuid(ACCOUNT_ID).build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setLastAccountId(ACCOUNT_ID);
    when(userCache.get(USER_ID)).thenReturn(mockUser);

    FieldUtils.writeField(authService, "segmentHandler", segmentHandler, true);
    authService.generateBearerTokenForUser(mockUser);
    try {
      Thread.sleep(10000);
      verify(segmentHandler, times(1))
          .reportTrackEvent(any(Account.class), anyString(), any(User.class), anyMap(), anyMap());
    } catch (InterruptedException | URISyntaxException e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  private User getMockUser(Account mockAccount) {
    return Builder.anUser()
        .uuid(USER_ID)
        .name("TestUser")
        .email("admin@abcd.io")
        .appId("TestApp")
        .accounts(Arrays.asList(mockAccount))
        .build();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void denyWorkflowCreationWhenEnvironmentAccessNotPresent() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    // Workflow with a different envId than what is allowed
    Workflow workflow = aWorkflow()
                            .appId(application.getUuid())
                            .envId(generateUuid())
                            .uuid(generateUuid())
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .build();

    Set<String> workflowCreatePermissionsForEnvs = new HashSet<>();
    workflowCreatePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowCreatePermissionsForEnvs(workflowCreatePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();
    UserThreadLocal.set(user);

    assertThatThrownBy(() -> authService.checkWorkflowPermissionsForEnv(application.getUuid(), workflow, Action.CREATE))
        .isInstanceOf(WingsException.class)
        .hasMessageContaining("Access Denied");

    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createPipelineWhenEnvironmentAccessPresent() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    // empty pipeline should not throw error for invalid environment access
    Pipeline pipeline = Pipeline.builder().build();

    Set<String> pipelineCreatePermissionsForEnvs = new HashSet<>();
    pipelineCreatePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineCreatePermissionsForEnvs(pipelineCreatePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkPipelinePermissionsForEnv(application.getUuid(), pipeline, Action.CREATE);
    } catch (WingsException ex) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    // no error since the environment is not required in the pipeline
    assertThat(exceptionThrown).isFalse();
  }
}
