package io.harness.ngtriggers.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ngtriggers.Constants.BITBUCKET_CLOUD_HEADER_KEY;
import static io.harness.ngtriggers.Constants.BITBUCKET_SERVER_HEADER_KEY;
import static io.harness.ngtriggers.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.ngtriggers.Constants.X_GIT_HUB_EVENT;
import static io.harness.ngtriggers.Constants.X_GIT_LAB_EVENT;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.HeaderConfig;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData;
import io.harness.ngtriggers.beans.dto.WebhookEventHeaderData.WebhookEventHeaderDataBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.BranchWebhookEvent;
import io.harness.ngtriggers.beans.scm.CommitDetails;
import io.harness.ngtriggers.beans.scm.IssueCommentWebhookEvent;
import io.harness.ngtriggers.beans.scm.PRWebhookEvent;
import io.harness.ngtriggers.beans.scm.Repository;
import io.harness.ngtriggers.beans.scm.WebhookBaseAttributes;
import io.harness.ngtriggers.beans.scm.WebhookGitUser;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData.WebhookPayloadDataBuilder;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.Header;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookRequest;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class WebhookEventPayloadParser {
  @Inject private SCMGrpc.SCMBlockingStub scmBlockingStub;

  public WebhookPayloadData parseEvent(TriggerWebhookEvent triggerWebhookEvent) {
    ParseWebhookResponse parseWebhookResponse = invokeScmService(triggerWebhookEvent);

    return convertWebhookResponse(parseWebhookResponse, triggerWebhookEvent);
  }

  public ParseWebhookResponse invokeScmService(TriggerWebhookEvent triggerWebhookEvent) {
    Set<String> headerKeys = triggerWebhookEvent.getHeaders().stream().map(HeaderConfig::getKey).collect(toSet());

    GitProvider gitProvider = obtainWebhookSource(headerKeys);
    Header.Builder header = Header.newBuilder();
    triggerWebhookEvent.getHeaders().forEach(headerConfig
        -> header.addFields(
            Header.Pair.newBuilder().setKey(headerConfig.getKey()).addAllValues(headerConfig.getValues()).build()));

    ParseWebhookResponse parseWebhookResponse;
    try {
      parseWebhookResponse = scmBlockingStub.parseWebhook(ParseWebhookRequest.newBuilder()
                                                              .setBody(triggerWebhookEvent.getPayload())
                                                              .setProvider(gitProvider)
                                                              .setHeader(header.build())
                                                              .build());
      log.info("Finished parsing webhook payload");
    } catch (StatusRuntimeException e) {
      log.error("Failed to parse webhook payload");
      throw e;
    }
    return parseWebhookResponse;
  }

  public WebhookPayloadData convertWebhookResponse(
      ParseWebhookResponse parseWebhookResponse, TriggerWebhookEvent triggerWebhookEvent) {
    WebhookPayloadDataBuilder webhookPayloadDataBuilder;
    if (parseWebhookResponse.hasPr()) {
      PullRequestHook prHook = parseWebhookResponse.getPr();
      webhookPayloadDataBuilder = convertPullRequestHook(prHook);
    } else if (parseWebhookResponse.hasPush()) {
      PushHook pushHook = parseWebhookResponse.getPush();
      if (pushHook.getRef().startsWith("refs/tags/")) {
        throw new InvalidRequestException("Tag event not supported", USER);
      }
      webhookPayloadDataBuilder = convertPushHook(pushHook);
    } else if (parseWebhookResponse.hasComment() && parseWebhookResponse.getComment().getIssue() != null
        && parseWebhookResponse.getComment().getIssue().getPr() != null) {
      IssueCommentHook commentHook = parseWebhookResponse.getComment();
      webhookPayloadDataBuilder = convertComment(commentHook);
    } else {
      log.error("Unsupported webhook event");
      throw new InvalidRequestException("Unsupported webhook event", USER);
    }

    return webhookPayloadDataBuilder.originalEvent(triggerWebhookEvent)
        .parseWebhookResponse(parseWebhookResponse)
        .build();
  }

  private WebhookPayloadDataBuilder convertPullRequestHook(PullRequestHook prHook) {
    WebhookGitUser webhookGitUser = convertUser(prHook.getSender());
    PRWebhookEvent prWebhookEvent = convertPRWebhookEvent(prHook);

    return WebhookPayloadData.builder()
        .webhookGitUser(webhookGitUser)
        .repository(prWebhookEvent.getRepository())
        .webhookEvent(prWebhookEvent);
  }

  WebhookPayloadDataBuilder convertPushHook(PushHook pushHook) {
    WebhookGitUser webhookGitUser = convertUser(pushHook.getSender());
    BranchWebhookEvent webhookEvent = convertPushWebhookEvent(pushHook);

    return WebhookPayloadData.builder()
        .webhookGitUser(webhookGitUser)
        .repository(webhookEvent.getRepository())
        .webhookEvent(webhookEvent);
  }

  WebhookPayloadDataBuilder convertComment(IssueCommentHook commentHook) {
    Repository repository = convertRepository(commentHook.getRepo());
    WebhookGitUser webhookGitUser = convertUser(commentHook.getSender());

    IssueCommentWebhookEvent webhookEvent =
        IssueCommentWebhookEvent.builder()
            .commentBody(commentHook.getComment().getBody())
            .pullRequestNum(Integer.toString(commentHook.getIssue().getNumber()))
            .repository(repository)
            .baseAttributes(WebhookBaseAttributes.builder().action(commentHook.getAction().name()).build())
            .build();

    return WebhookPayloadData.builder()
        .webhookGitUser(webhookGitUser)
        .repository(repository)
        .webhookEvent(webhookEvent);
  }

  private BranchWebhookEvent convertPushWebhookEvent(PushHook pushHook) {
    // TODO Add required push event details here with commit
    List<CommitDetails> commitDetailsList = new ArrayList<>();
    pushHook.getCommitsList().forEach(commit -> commitDetailsList.add(convertCommit(commit)));

    return BranchWebhookEvent.builder()
        .branchName(pushHook.getRepo().getBranch())
        .link(pushHook.getRepo().getLink())
        .commitDetailsList(commitDetailsList)
        .repository(convertRepository(pushHook.getRepo()))
        .baseAttributes(convertPushHookBaseAttributes(pushHook))
        .build();
  }

  private WebhookGitUser convertUser(User user) {
    return WebhookGitUser.builder()
        .avatar(user.getAvatar())
        .email(user.getEmail())
        .gitId(user.getLogin())
        .name(user.getName())
        .build();
  }

  private CommitDetails convertCommit(Commit commit) {
    return CommitDetails.builder()
        .commitId(commit.getSha())
        .message(commit.getMessage())
        .link(commit.getLink())
        .timeStamp(commit.getCommitter().getDate().getSeconds() * 1000)
        .ownerEmail(commit.getAuthor().getEmail())
        .ownerId(commit.getAuthor().getLogin())
        .ownerName(commit.getAuthor().getName())
        .build();
  }

  private Repository convertRepository(io.harness.product.ci.scm.proto.Repository repo) {
    return Repository.builder()
        .name(repo.getName())
        .namespace(repo.getNamespace())
        .slug(repo.getNamespace() + "/" + repo.getName())
        .link(repo.getLink())
        .branch(repo.getBranch())
        .isPrivate(repo.getPrivate())
        .httpURL(repo.getClone())
        .sshURL(repo.getCloneSsh())
        .build();
  }

  public PRWebhookEvent convertPRWebhookEvent(PullRequestHook prHook) {
    // TODO Add commit details
    PullRequest pr = prHook.getPr();
    return PRWebhookEvent.builder()
        .sourceBranch(pr.getSource())
        .targetBranch(pr.getTarget())
        .pullRequestLink(pr.getLink())
        .pullRequestBody(pr.getBody())
        .pullRequestId(pr.getNumber())
        .title(pr.getTitle())
        .closed(pr.getClosed())
        .merged(pr.getMerged())
        .repository(convertRepository(prHook.getRepo()))
        .baseAttributes(convertPrHookBaseAttributes(prHook))
        .build();
  }

  private WebhookBaseAttributes convertPrHookBaseAttributes(PullRequestHook prHook) {
    PullRequest pr = prHook.getPr();
    User author = prHook.getPr().getAuthor();
    String message = pr.getBody();
    if (message.equals("")) {
      message = pr.getTitle();
    }
    return WebhookBaseAttributes.builder()
        .link(pr.getLink())
        .message(message)
        .before(pr.getBase().getSha())
        .after(pr.getSha())
        .ref(pr.getRef())
        .source(pr.getSource())
        .target(pr.getTarget())
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(prHook.getSender().getLogin())
        .action(prHook.getAction().toString().toLowerCase())
        .build();
  }

  private WebhookBaseAttributes convertPushHookBaseAttributes(PushHook pushHook) {
    String trimmedRef = pushHook.getRef().replaceFirst("^refs/heads/", "");
    Signature author = pushHook.getCommit().getAuthor();
    return WebhookBaseAttributes.builder()
        .link(pushHook.getCommit().getLink())
        .message(pushHook.getCommit().getMessage())
        .before(pushHook.getBefore())
        .after(pushHook.getAfter())
        .ref(pushHook.getRef())
        .source(EMPTY)
        .target(trimmedRef)
        .authorLogin(author.getLogin())
        .authorName(author.getName())
        .authorEmail(author.getEmail())
        .authorAvatar(author.getAvatar())
        .sender(pushHook.getSender().getLogin())
        .build();
  }

  public GitProvider obtainWebhookSource(Set<String> headerKeys) {
    if (isEmpty(headerKeys)) {
      throw new InvalidRequestException("Failed to resolve Webhook Source. Reason: HttpHeaders are empty.");
    }

    if (containsHeaderKey(headerKeys, X_GIT_HUB_EVENT)) {
      return GitProvider.GITHUB;
    } else if (containsHeaderKey(headerKeys, X_GIT_LAB_EVENT)) {
      return GitProvider.GITLAB;
    } else if (containsHeaderKey(headerKeys, X_BIT_BUCKET_EVENT)) {
      return getBitbucketProvider(headerKeys);
    }

    throw new InvalidRequestException("Unable to resolve the Webhook Source. "
            + "One of " + X_GIT_HUB_EVENT + ", " + X_BIT_BUCKET_EVENT + ", " + X_GIT_LAB_EVENT
            + " must be present in Headers",
        USER);
  }

  @VisibleForTesting
  GitProvider getBitbucketProvider(Set<String> headerKeys) {
    if (isBitbucketServer(headerKeys)) {
      return GitProvider.STASH;
    } else if (containsHeaderKey(headerKeys, BITBUCKET_CLOUD_HEADER_KEY)) {
      return GitProvider.BITBUCKET;
    } else {
      StringBuilder stringBuilder = new StringBuilder(
          "TRIGGER: Could not determine if source is Bitbucket Cloud or Server, defaulting to Cloud. Please verify header again. ");
      headerKeys.forEach(key -> stringBuilder.append(key).append(','));
      log.warn(stringBuilder.toString());
      return GitProvider.BITBUCKET;
    }
  }

  @VisibleForTesting
  boolean isBitbucketServer(Set<String> headerKeys) {
    return containsHeaderKey(headerKeys, BITBUCKET_SERVER_HEADER_KEY);
  }

  public boolean containsHeaderKey(Set<String> headerKeys, String key) {
    if (isEmpty(headerKeys) || isBlank(key)) {
      return false;
    }

    return headerKeys.contains(key) || headerKeys.contains(key.toLowerCase())
        || headerKeys.stream().anyMatch(key::equalsIgnoreCase);
  }

  public WebhookEventHeaderData obtainWebhookSourceKeyData(List<HeaderConfig> headerConfigs) {
    HeaderConfig headerConfig = headerConfigs.stream()
                                    .filter(config
                                        -> config.getKey().equalsIgnoreCase(X_GIT_HUB_EVENT)
                                            || config.getKey().equalsIgnoreCase(X_GIT_LAB_EVENT)
                                            || config.getKey().equalsIgnoreCase(X_BIT_BUCKET_EVENT))
                                    .findFirst()
                                    .orElse(null);

    WebhookEventHeaderDataBuilder builder = WebhookEventHeaderData.builder().dataFound(false);
    if (headerConfig != null) {
      return WebhookEventHeaderData.builder()
          .sourceKey(headerConfig.getKey())
          .sourceKeyVal(headerConfig.getValues())
          .dataFound(true)
          .build();
    }

    return builder.build();
  }
}
