package software.wings.service.impl.trigger;

import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.IGOR;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.trigger.WebhookSource.BITBUCKET;
import static software.wings.beans.trigger.WebhookSource.GITHUB;
import static software.wings.beans.trigger.WebhookSource.GITLAB;
import static software.wings.service.impl.trigger.WebhookEventUtils.X_BIT_BUCKET_EVENT;
import static software.wings.service.impl.trigger.WebhookEventUtils.X_GIT_HUB_EVENT;
import static software.wings.service.impl.trigger.WebhookEventUtils.X_GIT_LAB_EVENT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.shell.AuthenticationScheme;

import software.wings.WingsBaseTest;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WebhookSource.BitBucketEventType;
import software.wings.beans.trigger.WebhookSource.GitHubEventType;
import software.wings.beans.trigger.WebhookSource.GitLabEventType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class WebhookEventUtilsTest extends WingsBaseTest {
  @Inject @InjectMocks private WebhookEventUtils webhookEventUtils;
  static final String GH_PUSH_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/github_push_request.json";
  static final String GH_PULL_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/github_pull_request.json";
  static final String GITLAB_PUSH_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/gitlab_push_request.json";
  static final String GITLAB_PULL_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/gitlab_pullrequest.json";
  static final String BITBUCKET_PUSH_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_push_request.json";
  static final String BITBUCKET_PULL_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_pull_request_created.json";
  static final String BITBUCKET_REF_CHANGES_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_ref_changes_request.json";
  static final String BITBUCKET_SERVER_PR_OPENED_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/bitbucket_server_pr_opened.json";

  @Mock HttpHeaders httpHeaders;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainWebhookSource() {
    when(httpHeaders.getHeaderString(X_GIT_HUB_EVENT))
        .thenReturn(GitHubEventType.PUSH.name())
        .thenReturn(null)
        .thenReturn(null);
    assertThat(webhookEventUtils.obtainWebhookSource(httpHeaders)).isEqualTo(GITHUB);
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT)).thenReturn(BitBucketEventType.PUSH.name()).thenReturn(null);
    assertThat(webhookEventUtils.obtainWebhookSource(httpHeaders)).isEqualTo(BITBUCKET);
    when(httpHeaders.getHeaderString(X_GIT_LAB_EVENT)).thenReturn(GitLabEventType.PUSH.name());
    assertThat(webhookEventUtils.obtainWebhookSource(httpHeaders)).isEqualTo(GITLAB);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainGHPushBranchAndCommitId() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_HUB_EVENT)).thenReturn(GitHubEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(GH_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(GITHUB, httpHeaders, payload)).isEqualTo("master");
    assertThat(webhookEventUtils.obtainCommitId(GITHUB, httpHeaders, payload))
        .isEqualTo("4ebc6e9e489979a29ca17b8da0c29d9f6803a5b9");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldObtainGHPullRequestBranchName() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_HUB_EVENT)).thenReturn(GitHubEventType.PULL_REQUEST.getValue());
    Map<String, Object> payload = obtainPayload(GH_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(GITHUB, httpHeaders, payload)).isEqualTo("puneet/istio-rollback");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainBitBucketPushBranchAndCommitId() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT)).thenReturn(BitBucketEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(BITBUCKET, httpHeaders, payload)).isEqualTo("master");
    assertThat(webhookEventUtils.obtainCommitId(BITBUCKET, httpHeaders, payload))
        .isEqualTo("b603ef9afef725673e908cef5c377f79aa532de0");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldObtainBitBucketPullBranchName() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT))
        .thenReturn(BitBucketEventType.PULL_REQUEST_CREATED.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(BITBUCKET, httpHeaders, payload)).isEqualTo("harshBranch");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldObtainGitLabBranchAndCommitId() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_LAB_EVENT)).thenReturn(GitLabEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(GITLAB_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(GITLAB, httpHeaders, payload)).isEqualTo("master");
    assertThat(webhookEventUtils.obtainCommitId(GITLAB, httpHeaders, payload))
        .isEqualTo("da1560886d4f094c3e6c9ef40349f7d38b5d27d7");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldObtainGitLabBranchNameForPullRequest() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_LAB_EVENT)).thenReturn(GitLabEventType.PULL_REQUEST.getValue());
    Map<String, Object> payload = obtainPayload(GITLAB_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainBranchName(GITLAB, httpHeaders, payload)).isEqualTo("harshBranch");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainGitHubPushRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_HUB_EVENT)).thenReturn(GitHubEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(GH_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(GITHUB, httpHeaders, payload).get()).isEqualTo("portal");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainGitHubPullRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_HUB_EVENT)).thenReturn(GitHubEventType.PULL_REQUEST.getValue());
    Map<String, Object> payload = obtainPayload(GH_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(GITHUB, httpHeaders, payload).get()).isEqualTo("portal");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainGitLabPushRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_LAB_EVENT)).thenReturn(GitLabEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(GITLAB_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(GITLAB, httpHeaders, payload).get()).isEqualTo("diaspora");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainGitLabPullRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_GIT_LAB_EVENT)).thenReturn(GitLabEventType.PULL_REQUEST.getValue());
    Map<String, Object> payload = obtainPayload(GITLAB_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(GITLAB, httpHeaders, payload).get()).isEqualTo("harshproject");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainBitbucketPushRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT)).thenReturn(BitBucketEventType.PUSH.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_PUSH_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(BITBUCKET, httpHeaders, payload).get()).isEqualTo("anshul-test");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainBitbucketRefChangesRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT)).thenReturn(BitBucketEventType.REFS_CHANGED.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_REF_CHANGES_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(BITBUCKET, httpHeaders, payload).get()).isEqualTo("anshul-test");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainBitbucketPullRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT))
        .thenReturn(BitBucketEventType.PULL_REQUEST_CREATED.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_PULL_REQ_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(BITBUCKET, httpHeaders, payload).get()).isEqualTo("HarshRepo");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainBitbucketServerPrOpenedRepositoryName() throws IOException {
    when(httpHeaders.getHeaderString(X_BIT_BUCKET_EVENT))
        .thenReturn(BitBucketEventType.PULL_REQUEST_CREATED.getValue());
    Map<String, Object> payload = obtainPayload(BITBUCKET_SERVER_PR_OPENED_FILE);
    assertThat(webhookEventUtils.obtainRepositoryName(BITBUCKET, httpHeaders, payload).get())
        .isEqualTo("git-sync-igor-bb-server");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void testFullNameFromCloneUrls() {
    assertThat(webhookEventUtils.fullNameFromCloneUrls(
                   "https://github.com/wings-software/portal.git", "git@github.com:wings-software/portal.git"))
        .isEqualTo("wings-software/portal");
    assertThat(webhookEventUtils.fullNameFromCloneUrls(
                   "http://example.com/mike/diaspora.git", "git@example.com:mike/diaspora.git"))
        .isEqualTo("mike/diaspora");
    assertThat(webhookEventUtils.fullNameFromCloneUrls(
                   "https://gitlab.com/harshjain123/harshproject.git", "git@gitlab.com:harshjain123/harshproject.git"))
        .isEqualTo("harshjain123/harshproject");
    assertThat(webhookEventUtils.fullNameFromCloneUrls(
                   "https://bitbucket.dev.harness.io/scm/~harnessadmin/git-sync-igor-bb-server.git",
                   "ssh://git@bitbucket.dev.harness.io:7999/~harnessadmin/git-sync-igor-bb-server.git"))
        .isEqualTo("~harnessadmin/git-sync-igor-bb-server");
    assertThat(webhookEventUtils.fullNameFromCloneUrls("http://34.237.124.155:7990/scm/har/anshul-test.git",
                   "ssh://git@34.237.124.155:7999/har/anshul-test.git"))
        .isEqualTo("har/anshul-test");
    assertThat(webhookEventUtils.fullNameFromCloneUrls("https://bitbucket.dev.harness.io/scm/har/git-sync-1.git",
                   "ssh://git@bitbucket.dev.harness.io:7999/har/git-sync-1.git"))
        .isEqualTo("har/git-sync-1");
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainFullName() throws IOException {
    assertThat(internalShouldObtainFullName(GITHUB, X_GIT_HUB_EVENT, GitHubEventType.PUSH.getValue(), GH_PUSH_REQ_FILE))
        .isEqualTo("wings-software/portal");
    assertThat(internalShouldObtainFullName(
                   GITHUB, X_GIT_HUB_EVENT, GitHubEventType.PULL_REQUEST.getValue(), GH_PULL_REQ_FILE))
        .isEqualTo("wings-software/portal");

    assertThat(
        internalShouldObtainFullName(GITLAB, X_GIT_LAB_EVENT, GitLabEventType.PUSH.getValue(), GITLAB_PUSH_REQ_FILE))
        .isEqualTo("mike/diaspora");
    assertThat(internalShouldObtainFullName(
                   GITLAB, X_GIT_LAB_EVENT, GitLabEventType.PULL_REQUEST.getValue(), GITLAB_PULL_REQ_FILE))
        .isEqualTo("harshjain123/harshproject");

    assertThat(internalShouldObtainFullName(BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_REF_CHANGES_REQ_FILE))
        .isEqualTo("har/anshul-test");
    assertThat(internalShouldObtainFullName(BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_REF_CHANGES_REQ_FILE))
        .isEqualTo("har/anshul-test");

    assertThat(internalShouldObtainFullName(BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_SERVER_PR_OPENED_FILE))
        .isEqualTo("~harnessadmin/git-sync-igor-bb-server");
    assertThat(internalShouldObtainFullName(BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_SERVER_PR_OPENED_FILE))
        .isEqualTo("~harnessadmin/git-sync-igor-bb-server");
  }

  private String internalShouldObtainFullName(
      WebhookSource webhookSource, String headerKey, String headerValue, String payloadFile) throws IOException {
    when(httpHeaders.getHeaderString(headerKey)).thenReturn(headerValue);

    return webhookEventUtils.obtainRepositoryFullName(webhookSource, httpHeaders, obtainPayload(payloadFile)).get();
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void shouldObtainCloneUrl() throws IOException {
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, GITHUB, X_GIT_HUB_EVENT,
                   GitHubEventType.PUSH.getValue(), GH_PUSH_REQ_FILE))
        .isEqualTo("https://github.com/wings-software/portal.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, GITHUB, X_GIT_HUB_EVENT,
                   GitHubEventType.PUSH.getValue(), GH_PUSH_REQ_FILE))
        .isEqualTo("git@github.com:wings-software/portal.git");

    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, GITHUB, X_GIT_HUB_EVENT,
                   GitHubEventType.PULL_REQUEST.getValue(), GH_PULL_REQ_FILE))
        .isEqualTo("https://github.com/wings-software/portal.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, GITHUB, X_GIT_HUB_EVENT,
                   GitHubEventType.PULL_REQUEST.getValue(), GH_PULL_REQ_FILE))
        .isEqualTo("git@github.com:wings-software/portal.git");

    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, GITLAB, X_GIT_LAB_EVENT,
                   GitLabEventType.PUSH.getValue(), GITLAB_PUSH_REQ_FILE))
        .isEqualTo("http://example.com/mike/diaspora.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, GITLAB, X_GIT_LAB_EVENT,
                   GitLabEventType.PUSH.getValue(), GITLAB_PUSH_REQ_FILE))
        .isEqualTo("git@example.com:mike/diaspora.git");

    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, GITLAB, X_GIT_LAB_EVENT,
                   GitLabEventType.PULL_REQUEST.getValue(), GITLAB_PULL_REQ_FILE))
        .isEqualTo("https://gitlab.com/harshjain123/harshproject.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, GITLAB, X_GIT_LAB_EVENT,
                   GitLabEventType.PULL_REQUEST.getValue(), GITLAB_PULL_REQ_FILE))
        .isEqualTo("git@gitlab.com:harshjain123/harshproject.git");

    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_REF_CHANGES_REQ_FILE))
        .isEqualTo("http://34.237.124.155:7990/scm/har/anshul-test.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_REF_CHANGES_REQ_FILE))
        .isEqualTo("ssh://git@34.237.124.155:7999/har/anshul-test.git");

    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.HTTP_PASSWORD, BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_SERVER_PR_OPENED_FILE))
        .isEqualTo("https://bitbucket.dev.harness.io/scm/~harnessadmin/git-sync-igor-bb-server.git");
    assertThat(internalShouldObtainCloneUrl(AuthenticationScheme.SSH_KEY, BITBUCKET, X_BIT_BUCKET_EVENT,
                   BitBucketEventType.PULL_REQUEST_CREATED.getValue(), BITBUCKET_SERVER_PR_OPENED_FILE))
        .isEqualTo("ssh://git@bitbucket.dev.harness.io:7999/~harnessadmin/git-sync-igor-bb-server.git");
  }

  private String internalShouldObtainCloneUrl(AuthenticationScheme authenticationScheme, WebhookSource webhookSource,
      String headerKey, String headerValue, String payloadFile) throws IOException {
    when(httpHeaders.getHeaderString(headerKey)).thenReturn(headerValue);

    return webhookEventUtils
        .obtainCloneUrl(authenticationScheme, webhookSource, httpHeaders, obtainPayload(payloadFile))
        .get();
  }

  private Map<String, Object> obtainPayload(String filePath) throws IOException {
    File file = new File(filePath);
    return JsonUtils.asObject(
        FileUtils.readFileToString(file, Charset.defaultCharset()), new TypeReference<Map<String, Object>>() {});
  }
}
