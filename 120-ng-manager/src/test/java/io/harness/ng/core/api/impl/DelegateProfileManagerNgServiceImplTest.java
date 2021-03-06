package io.harness.ng.core.api.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.tasks.Cd1SetupFields.ENV_ID_FIELD;

import static software.wings.beans.Variable.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.beans.ScopingRuleDetailsNg;
import io.harness.delegateprofile.DelegateProfileGrpc;
import io.harness.delegateprofile.DelegateProfilePageResponseGrpc;
import io.harness.delegateprofile.ProfileId;
import io.harness.delegateprofile.ProfileScopingRule;
import io.harness.delegateprofile.ScopingValues;
import io.harness.exception.InvalidArgumentsException;
import io.harness.grpc.DelegateProfileServiceGrpcClient;
import io.harness.paging.PageRequestGrpc;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.tasks.Cd1SetupFields;

import software.wings.beans.Environment;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateProfileManagerNgServiceImplTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = generateUuid();
  private static final String TEST_DELEGATE_PROFILE_ID = generateUuid();

  @Mock private DelegateProfileServiceGrpcClient delegateProfileServiceGrpcClient;
  @Mock private HPersistence hPersistence;
  @InjectMocks @Inject private DelegateProfileManagerNgServiceImpl delegateProfileManagerNgService;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldList() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfiles(any(AccountId.class), any(PageRequestGrpc.class)))
        .thenReturn(null)
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        delegateProfileManagerNgService.list(TEST_ACCOUNT_ID, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNull();

    delegateProfileDetailsPageResponse = delegateProfileManagerNgService.list(TEST_ACCOUNT_ID, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldGet() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    when(delegateProfileServiceGrpcClient.getProfile(any(AccountId.class), any(ProfileId.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails =
        delegateProfileManagerNgService.get(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails =
        delegateProfileManagerNgService.get(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                                 .build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .build();

    when(delegateProfileServiceGrpcClient.updateProfile(any(DelegateProfileGrpc.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails = delegateProfileManagerNgService.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerNgService.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isEqualToIgnoringGivenFields(profileDetail, "uuid");
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(updatedDelegateProfileDetails.getDescription()).isEqualTo("description");
    assertThat(updatedDelegateProfileDetails.getScopingRules().get(0).getDescription()).isEqualTo("test");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingProfile() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    assertThatThrownBy(() -> delegateProfileManagerNgService.update(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldAdd() {
    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                                 .build();

    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .build();

    when(delegateProfileServiceGrpcClient.addProfile(any(DelegateProfileGrpc.class))).thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg result = delegateProfileManagerNgService.add(profileDetail);
    assertThat(result).isNotNull().isEqualToIgnoringGivenFields(profileDetail, "uuid");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenAddingProfile() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(() -> delegateProfileManagerNgService.add(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdateScopingRules() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet<>(Collections.singletonList("PROD")))
                                                 .build();

    when(delegateProfileServiceGrpcClient.updateProfileScopingRules(
             any(AccountId.class), any(ProfileId.class), anyList()))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails = delegateProfileManagerNgService.updateScopingRules(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerNgService.updateScopingRules(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingScopingRule() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(()
                           -> delegateProfileManagerNgService.updateScopingRules(
                               TEST_ACCOUNT_ID, generateUuid(), Collections.singletonList(scopingRuleDetail)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldDelete() {
    delegateProfileManagerNgService.delete(TEST_ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    AccountId accountId = AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build();
    ProfileId profileId = ProfileId.newBuilder().setId(TEST_DELEGATE_PROFILE_ID).build();
    verify(delegateProfileServiceGrpcClient, times(1)).deleteProfile(eq(accountId), eq(profileId));
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdateSelectors() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    List<String> selectors = Collections.singletonList("selectors");

    when(delegateProfileServiceGrpcClient.updateProfileSelectors(any(AccountId.class), any(ProfileId.class), anyList()))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails = delegateProfileManagerNgService.updateSelectors(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerNgService.updateSelectors(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);

    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldGenerateScopingRuleDescription() {
    List<String> serviceNames = Arrays.asList("service1, service2");

    ScopingValues scopingValuesEnvTypeId = ScopingValues.newBuilder().addAllValue(serviceNames).build();

    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(ENV_ID, scopingValuesEnvTypeId);

    String description = delegateProfileManagerNgService.generateScopingRuleDescription(scopingEntities);

    assertThat(description).isNotNull().isEqualTo("Environment: service1, service2; ");
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveScopingRuleEnvEntityName() {
    List<String> scopingEntitiesIds = new ArrayList<>();

    Environment environment = Environment.Builder.anEnvironment().uuid(ENV_ID).name("qa").build();
    when(hPersistence.get(Environment.class, ENV_ID)).thenReturn(environment);

    Environment retrievedEnvironment = hPersistence.get(Environment.class, environment.getUuid());

    scopingEntitiesIds.add(retrievedEnvironment.getName());

    List<String> retrieveScopingRuleEntitiesNames =
        delegateProfileManagerNgService.retrieveScopingRuleEntitiesNames(ENV_ID_FIELD, scopingEntitiesIds);

    assertThat(retrieveScopingRuleEntitiesNames).isNotNull().containsExactly("qa");
  }
}
