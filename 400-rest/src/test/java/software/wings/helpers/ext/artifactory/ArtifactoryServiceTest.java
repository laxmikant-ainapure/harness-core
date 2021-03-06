package software.wings.helpers.ext.artifactory;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.ArtifactType.RPM;
import static software.wings.utils.ArtifactType.WAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.artifact.Artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryServiceImpl.ArtifactoryErrorResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.utils.ArtifactType;
import software.wings.utils.JsonUtils;
import software.wings.utils.RepositoryType;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.impl.ArtifactoryResponseImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArtifactoryServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks ArtifactoryService artifactoryService = new ArtifactoryServiceImpl();

  /**
   * The Wire mock rule.
   */
  @Rule public WireMockRule wireMockRule = new WireMockRule(9881);

  String url = "http://localhost:9881/artifactory/";

  private ArtifactoryConfig artifactoryConfig =
      ArtifactoryConfig.builder().artifactoryUrl(url).username("admin").password("dummy123!".toCharArray()).build();

  private ArtifactoryConfig artifactoryConfigAnonymous = ArtifactoryConfig.builder().artifactoryUrl(url).build();

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(
        artifactoryService, "encryptionService", new EncryptionServiceImpl(null, null, null, null, null), true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetMavenRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, WAR);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetIvyRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, WAR);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-ivy");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetDockerRepositoriesWithArtifactType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, ArtifactType.DOCKER);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetDockerRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void testGetRepositoriesForMavenWithPackageType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, "maven");
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).containsKeys("harness-maven-snapshots");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetRpmRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, RPM);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetDockerImages() {
    List<String> repositories = artifactoryService.getRepoPaths(artifactoryConfig, null, "docker");
    assertThat(repositories).isNotNull();
    assertThat(repositories).contains("wingsplugins/todolist");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetDockerTags() {
    List<BuildDetails> builds = artifactoryService.getBuilds(artifactoryConfig, null,
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
            .metadataOnly(true)
            .jobName("docker")
            .imageName("wingsplugins/todolist")
            .artifactoryDockerRepositoryServer("harness.jfrog.com")
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build(),
        50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("latest");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetRpmFilePaths() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfig, null, "harness-rpm", "todolist*/", "generic", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0-2.x86_64.rpm");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetCorrectBuildNoWithAnyWildcardMatch() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, null, "harness-maven", "io/harness/todolist/todolist/*/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetCorrectBuildNoForAtLeastOneWildcardPattern() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, null, "harness-maven", "io/harness/todolist/todolist/[0-9]+/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, null, "harness-maven", "/io/harness/todolist/todolist/1.0/todolist-1.0.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("io/harness/todolist/todolist/1.0/todolist-1.0.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter1() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, null, "harness-maven", "io/harness/todolist/todolist/1.0/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0.war");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldDownloadRpmArtifacts() {
    ListNotifyResponseData listNotifyResponseData =
        artifactoryService.downloadArtifacts(artifactoryConfig, null, "harness-rpm",
            ImmutableMap.of(ArtifactMetadataKeys.artifactPath, "harness-rpm/todolist-1.0-2.x86_64.rpm",
                ArtifactMetadataKeys.artifactFileName, "todolist-1.0-2.x86_64.rpm"),
            "delegateId", "taskId", "ACCOUNT_ID");
    assertThat(listNotifyResponseData).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldValidateArtifactPath() {
    assertThat(artifactoryService.validateArtifactPath(artifactoryConfig, null, "harness-rpm", "todolist*", "generic"))
        .isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldValidateArtifactPathAnonymous() {
    assertThat(artifactoryService.validateArtifactPath(
                   artifactoryConfigAnonymous, null, "harness-rpm", "todolist*", "generic"))
        .isTrue();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldValidateArtifactPathPasswordEmpty() {
    ArtifactoryConfig artifactoryConfigNoPassword =
        ArtifactoryConfig.builder().artifactoryUrl("some url").username("some username").build();
    artifactoryService.validateArtifactPath(artifactoryConfigNoPassword, null, "harness-rpm", "todolist*", "generic");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldValidateArtifactPathEmpty() {
    artifactoryService.validateArtifactPath(artifactoryConfig, null, "harness-rpm", "", "generic");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldValidateArtifactPathMaven() {
    artifactoryService.validateArtifactPath(
        artifactoryConfig, null, "harness-rpm", "io/harness/todolist/*/todolist", "maven");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldDownloadRpmArtifact() {
    Pair<String, InputStream> pair = artifactoryService.downloadArtifact(artifactoryConfig, null, "harness-rpm",
        ImmutableMap.of(ArtifactMetadataKeys.artifactPath, "harness-rpm/todolist-1.0-2.x86_64.rpm",
            ArtifactMetadataKeys.artifactFileName, "todolist-1.0-2.x86_64.rpm"));
    assertThat(pair).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetFileSize() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.artifactPath, "harness-maven/io/harness/todolist/todolist/1.1/todolist-1.1.war");
    Long size = artifactoryService.getFileSize(artifactoryConfig, null, metadata);
    assertThat(size).isEqualTo(1776799L);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldTestArtifactoryRunning() {
    assertThat(artifactoryService.isRunning(artifactoryConfig, null)).isTrue();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldThrowExceptionOnArtifactoryResponseWith407StatusCode() throws IOException, IllegalAccessException {
    ArtifactoryServiceImpl service = Mockito.spy(ArtifactoryServiceImpl.class);
    Artifactory client = Mockito.mock(Artifactory.class);
    ArtifactoryResponse artifactoryResponse = Mockito.mock(ArtifactoryResponse.class);
    FieldUtils.writeField(service, "encryptionService", new EncryptionServiceImpl(null, null, null, null, null), true);

    when(artifactoryResponse.getStatusLine())
        .thenReturn(new BasicStatusLine(new ProtocolVersion("", 1, 1), 407, "407 Related Exception Phrase"));
    when(service.getArtifactoryClient(artifactoryConfig, null)).thenReturn(client);
    when(client.restCall(any())).thenReturn(artifactoryResponse);

    assertThatThrownBy(() -> service.isRunning(artifactoryConfig, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("407 Related Exception Phrase");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldThrowExceptionOnArtifactoryResponseWith400StatusCode() throws IOException, IllegalAccessException {
    ArtifactoryServiceImpl service = Mockito.spy(ArtifactoryServiceImpl.class);
    Artifactory client = Mockito.mock(Artifactory.class);
    ArtifactoryResponse artifactoryResponse = Mockito.mock(ArtifactoryResponseImpl.class);
    FieldUtils.writeField(service, "encryptionService", new EncryptionServiceImpl(null, null, null, null, null), true);

    when(artifactoryResponse.getStatusLine())
        .thenReturn(new BasicStatusLine(new ProtocolVersion("", 1, 1), 500, "Internal Server Error"));
    when(artifactoryResponse.parseBody(ArtifactoryErrorResponse.class))
        .thenReturn(JsonUtils.convertStringToObj("{\n"
                + "  \"errors\" : [ {\n"
                + "    \"status\" : 500,\n"
                + "    \"message\" : \"Artifactory failed to initialize: check Artifactory logs for errors.\"\n"
                + "  } ]\n"
                + "}",
            ArtifactoryErrorResponse.class));
    when(service.getArtifactoryClient(artifactoryConfig, null)).thenReturn(client);
    when(client.restCall(any())).thenReturn(artifactoryResponse);

    assertThatThrownBy(() -> service.isRunning(artifactoryConfig, null))
        .isInstanceOf(ArtifactoryServerException.class)
        .hasMessageContaining(
            "Request to server failed with status code: 500 with message - Artifactory failed to initialize: check Artifactory logs for errors.");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void testGetRepositoriesWithRepositoryType() {
    Map<String, String> repositories =
        artifactoryService.getRepositories(artifactoryConfig, null, RepositoryType.docker);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void testGetMavenRepositoriesWithRepositoryType() {
    Map<String, String> repositories =
        artifactoryService.getRepositories(artifactoryConfig, null, RepositoryType.maven);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void testGetAnyRepositoriesWithRepositoryType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, null, RepositoryType.any);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void testGetDefaultRepositoriesWithRepositoryType() {
    Map<String, String> repositories =
        artifactoryService.getRepositories(artifactoryConfig, null, RepositoryType.nuget);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetFilePathsWithWildCardForAnonymousUser() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfigAnonymous, null, "harness-maven", "tdlist/*/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("tdlist/1.1/tdlist-1.1.war", "tdlist/1.2/tdlist-1.2.war");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetFilePathsWithWildCardForAnonymousUser1() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfigAnonymous, null, "harness-maven", "tdlist/1.1/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("tdlist-1.1.war");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldThrowExceptionWhenEmptyArtifactPath() {
    assertThatThrownBy(
        () -> artifactoryService.getFilePaths(artifactoryConfigAnonymous, null, "harness-maven", "    ", "any", 50))
        .isInstanceOf(ArtifactoryServerException.class)
        .extracting("message")
        .isEqualTo("Artifact path can not be empty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldGetFilePathsForAnonymousUser() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfigAnonymous, null, "harness-maven", "//myartifact/", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("myartifact2");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldAppendProxyConfig() {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("proxyScheme", "http");
    System.setProperty("http.proxyPort", "123");
    ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNotNull();
    assertThat(artifactoryClientBuilder.getProxy().getHost()).isEqualTo("proxyHost");
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldNotAppendProxyConfigIfArtifactoryUrlIsInNonProxyList() {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("http.proxyPort", "123");
    System.setProperty("http.nonProxyHosts", "url");
    ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNull();

    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  @Ignore("TODO: This test is failing in bazel. Changes are required from the owner to make it work in bazel")
  public void shouldNotAppendProxyConfigWhenProxyIsNotEnabled() {
    ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNull();
  }
}
