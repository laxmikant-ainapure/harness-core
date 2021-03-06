package io.harness.ngtriggers.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.ngtriggers.Constants.X_GIT_HUB_EVENT;
import static io.harness.ngtriggers.Constants.X_GIT_LAB_EVENT;
import static io.harness.ngtriggers.Constants.X_HARNESS_TRIGGER_ID;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.BITBUCKET;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.CUSTOM;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.GITLAB;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ngtriggers.beans.config.HeaderConfig;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO.NGTriggerDetailsResponseDTOBuilder;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails.WebhookDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.beans.entity.metadata.CustomMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CustomWebhookInlineAuthToken;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata.WebhookMetadataBuilder;
import io.harness.ngtriggers.beans.source.NGTriggerSource;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.CustomWebhookTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.GitRepoSpec;
import io.harness.ngtriggers.beans.source.webhook.RepoSpec;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfig;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.repositories.ng.core.spring.TriggerEventHistoryRepository;
import io.harness.yaml.utils.YamlPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGTriggerElementMapper {
  public static final int DAYS_BEFORE_CURRENT_DATE = 6;
  private TriggerEventHistoryRepository triggerEventHistoryRepository;
  private WebhookEventPayloadParser webhookEventPayloadParser;

  public NGTriggerConfig toTriggerConfig(String yaml) {
    try {
      return YamlPipelineUtils.read(yaml, NGTriggerConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException(e.getMessage()); // update this message
    }
  }

  public TriggerDetails toTriggerDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String yaml) {
    NGTriggerConfig config = toTriggerConfig(yaml);
    NGTriggerEntity ngTriggerEntity =
        toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, config, yaml);
    return TriggerDetails.builder().ngTriggerConfig(config).ngTriggerEntity(ngTriggerEntity).build();
  }

  public NGTriggerEntity toTriggerEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String yaml) {
    NGTriggerConfig config = toTriggerConfig(yaml);
    if (!identifier.equals(config.getIdentifier())) {
      throw new InvalidRequestException("Identifier in url and yaml do not match");
    }
    return toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, config, yaml);
  }

  public NGTriggerEntity toTriggerEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, NGTriggerConfig config, String yaml) {
    return NGTriggerEntity.builder()
        .name(config.getName())
        .identifier(config.getIdentifier())
        .description(config.getDescription())
        .yaml(yaml)
        .type(config.getSource().getType())
        .accountId(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .targetIdentifier(config.getTarget().getTargetIdentifier())
        .targetType(config.getTarget().getType())
        .metadata(toMetadata(config.getSource()))
        .enabled(config.getEnabled())
        .tags(TagMapper.convertToList(config.getTags()))
        .build();
  }

  NGTriggerMetadata toMetadata(NGTriggerSource triggerSource) {
    NGTriggerType type = triggerSource.getType();
    if (type == NGTriggerType.WEBHOOK) {
      WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) triggerSource.getSpec();

      WebhookMetadataBuilder metadata = WebhookMetadata.builder();
      if (webhookTriggerConfig.getSpec().getType() == CUSTOM) {
        metadata.custom(prepareCustomMetadata(webhookTriggerConfig));
      } else if (isGitSpec(webhookTriggerConfig)) {
        metadata.git(prepareGitMetadata(webhookTriggerConfig));
      }

      metadata.type(webhookTriggerConfig.getType());
      return NGTriggerMetadata.builder().webhook(metadata.build()).build();
    }
    throw new InvalidRequestException("Type " + type.toString() + " is invalid");
  }

  @VisibleForTesting
  boolean isGitSpec(WebhookTriggerConfig webhookTriggerConfig) {
    return webhookTriggerConfig.getSpec().getType() == GITHUB || webhookTriggerConfig.getSpec().getType() == GITLAB
        || webhookTriggerConfig.getSpec().getType() == BITBUCKET;
  }

  @VisibleForTesting
  GitMetadata prepareGitMetadata(WebhookTriggerConfig webhookTriggerConfig) {
    RepoSpec repoSpec = webhookTriggerConfig.getSpec().getRepoSpec();
    if (repoSpec != null && GitRepoSpec.class.isAssignableFrom(repoSpec.getClass())) {
      GitRepoSpec gitRepoSpec = (GitRepoSpec) repoSpec;

      return GitMetadata.builder()
          .connectorIdentifier(gitRepoSpec.getIdentifier())
          .repoName(gitRepoSpec.getRepoName())
          .build();
    }

    return null;
  }

  @VisibleForTesting
  CustomMetadata prepareCustomMetadata(WebhookTriggerConfig webhookTriggerConfig) {
    CustomWebhookTriggerSpec customWebhookTriggerSpec = (CustomWebhookTriggerSpec) webhookTriggerConfig.getSpec();
    CustomWebhookInlineAuthToken customWebhookInlineAuthToken;

    if ("inline".equals(customWebhookTriggerSpec.getAuthToken().getType())) {
      customWebhookInlineAuthToken = (CustomWebhookInlineAuthToken) customWebhookTriggerSpec.getAuthToken().getSpec();

      String encryptedToken = Base64.encodeBase64String(customWebhookInlineAuthToken.getValue().getBytes());

      return CustomMetadata.builder()
          .customAuthTokenType(customWebhookTriggerSpec.getAuthToken().getType())
          .customAuthTokenValue(encryptedToken)
          .build();
    }
    return null;
  }

  public NGTriggerResponseDTO toResponseDTO(NGTriggerEntity ngTriggerEntity) {
    return NGTriggerResponseDTO.builder()
        .name(ngTriggerEntity.getName())
        .identifier(ngTriggerEntity.getIdentifier())
        .description(ngTriggerEntity.getDescription())
        .type(ngTriggerEntity.getType())
        .accountIdentifier(ngTriggerEntity.getAccountId())
        .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
        .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
        .targetIdentifier(ngTriggerEntity.getTargetIdentifier())
        .version(ngTriggerEntity.getVersion())
        .yaml(ngTriggerEntity.getYaml())
        .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
        .build();
  }

  public TriggerWebhookEvent toNGTriggerWebhookEvent(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String payload, List<HeaderConfig> headerConfigs) {
    WebhookSourceRepo webhookSourceRepo;
    Set<String> headerKeys =
        headerConfigs.stream().map(headerConfig -> headerConfig.getKey()).collect(Collectors.toSet());
    if (webhookEventPayloadParser.containsHeaderKey(headerKeys, X_GIT_HUB_EVENT)) {
      webhookSourceRepo = GITHUB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headerKeys, X_GIT_LAB_EVENT)) {
      webhookSourceRepo = GITLAB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headerKeys, X_BIT_BUCKET_EVENT)) {
      webhookSourceRepo = BITBUCKET;
    } else {
      webhookSourceRepo = CUSTOM;
    }

    TriggerWebhookEventBuilder triggerWebhookEventBuilder = TriggerWebhookEvent.builder()
                                                                .accountId(accountIdentifier)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .sourceRepoType(webhookSourceRepo.name())
                                                                .headers(headerConfigs)
                                                                .payload(payload);

    HeaderConfig customTriggerIdentifier = headerConfigs.stream()
                                               .filter(header -> header.getKey().equalsIgnoreCase(X_HARNESS_TRIGGER_ID))
                                               .findAny()
                                               .orElse(null);

    if (customTriggerIdentifier != null && isNotBlank(customTriggerIdentifier.getValues().get(0))) {
      triggerWebhookEventBuilder.triggerIdentifier(customTriggerIdentifier.getValues().get(0));
    }

    return triggerWebhookEventBuilder.build();
  }

  public NGTriggerDetailsResponseDTO toNGTriggerDetailsResponseDTO(
      NGTriggerEntity ngTriggerEntity, boolean includeYaml) {
    NGTriggerDetailsResponseDTOBuilder ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(ngTriggerEntity.getName())
            .identifier(ngTriggerEntity.getIdentifier())
            .description(ngTriggerEntity.getDescription())
            .type(ngTriggerEntity.getType())
            .yaml(includeYaml ? ngTriggerEntity.getYaml() : StringUtils.EMPTY)
            .tags(TagMapper.convertToMap(ngTriggerEntity.getTags()))
            .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled());

    // Webhook Details
    if (ngTriggerEntity.getType() == NGTriggerType.WEBHOOK) {
      WebhookDetailsBuilder webhookDetails = WebhookDetails.builder();

      webhookDetails.webhookSourceRepo(ngTriggerEntity.getMetadata().getWebhook().getType()).build();

      if (ngTriggerEntity.getMetadata().getWebhook().getType().equalsIgnoreCase("CUSTOM")) {
        CustomMetadata customMedata = ngTriggerEntity.getMetadata().getWebhook().getCustom();
        if (customMedata != null
            && "inline".equals(ngTriggerEntity.getMetadata().getWebhook().getCustom().getCustomAuthTokenType())) {
          webhookDetails.webhookSecret(new String(
              Base64.decodeBase64(ngTriggerEntity.getMetadata().getWebhook().getCustom().getCustomAuthTokenValue()),
              StandardCharsets.UTF_8));
        }

        ngTriggerDetailsResponseDTO.webhookDetails(webhookDetails.build());
      }
    }

    Optional<TriggerEventHistory> triggerEventHistory = fetchLatestExecutionForTrigger(ngTriggerEntity);

    List<Integer> executions = generateLastWeekActivityData(ngTriggerEntity);
    if (isNotEmpty(executions)) {
      ngTriggerDetailsResponseDTO.executions(executions);
    }

    if (triggerEventHistory.isPresent()) {
      LastTriggerExecutionDetails lastTriggerExecutionDetails =
          LastTriggerExecutionDetails.builder()
              .lastExecutionStatus(triggerEventHistory.get().getFinalStatus())
              .lastExecutionSuccessful(!triggerEventHistory.get().isExceptionOccurred())
              .message(triggerEventHistory.get().getMessage())
              .planExecutionId(triggerEventHistory.get().getPlanExecutionId())
              .lastExecutionTime(triggerEventHistory.get().getCreatedAt())
              .build();
      ngTriggerDetailsResponseDTO.lastTriggerExecutionDetails(lastTriggerExecutionDetails);
    }

    return ngTriggerDetailsResponseDTO.build();
  }

  private List<Integer> generateLastWeekActivityData(NGTriggerEntity ngTriggerEntity) {
    long startTime = System.currentTimeMillis() - Duration.ofDays(DAYS_BEFORE_CURRENT_DATE).toMillis();
    Criteria criteria = TriggerFilterHelper.createCriteriaForTriggerEventCountLastNDays(ngTriggerEntity.getAccountId(),
        ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getIdentifier(),
        ngTriggerEntity.getTargetIdentifier(), startTime);

    List<TriggerEventHistory> triggerActivityList =
        triggerEventHistoryRepository.findAllActivationTimestampsInRange(criteria);

    Integer[] executions = prepareExecutionDataArray(startTime, triggerActivityList);
    return Arrays.asList(executions);
  }

  @VisibleForTesting
  Integer[] prepareExecutionDataArray(long startTime, List<TriggerEventHistory> triggerActivityList) {
    Integer[] executions = new Integer[] {0, 0, 0, 0, 0, 0, 0};
    if (isNotEmpty(triggerActivityList)) {
      List<Long> timeStamps =
          triggerActivityList.stream().map(event -> event.getCreatedAt()).sorted().collect(Collectors.toList());
      timeStamps.forEach(timeStamp -> {
        long diff = DAYS.between(Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDate(),
            Instant.ofEpochMilli(timeStamp).atZone(ZoneId.systemDefault()).toLocalDate());
        int index = (int) Math.abs(diff);
        if (index >= 0 && index < 7) {
          executions[index]++;
        }
      });
    }
    return executions;
  }

  public Optional<TriggerEventHistory> fetchLatestExecutionForTrigger(NGTriggerEntity ngTriggerEntity) {
    List<TriggerEventHistory> triggerEventHistoryList =
        triggerEventHistoryRepository.findFirst1ByAccountIdAndOrgIdentifierAndProjectIdentifierAndTriggerIdentifier(
            ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
            ngTriggerEntity.getIdentifier(), Sort.by(TriggerEventHistoryKeys.createdAt).descending());
    if (!isEmpty(triggerEventHistoryList)) {
      return Optional.of(triggerEventHistoryList.get(0));
    }
    return Optional.empty();
  }
}
