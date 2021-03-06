package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.beans.response.WebhookEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_REPO;
import static io.harness.utils.IdentifierRefHelper.getFullyQualifiedIdentifierRefString;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.Repository;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.TriggerGitConnectorWrapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.WebhookEventResponseHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton

public class GitWebhookTriggerRepoFilter implements TriggerFilter {
  private NGTriggerService ngTriggerService;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = WebhookEventMappingResponse.builder();

    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    TriggerWebhookEvent originalEvent = webhookPayloadData.getOriginalEvent();
    Repository repository = webhookPayloadData.getRepository();
    Set<String> urls = new HashSet<>(Arrays.asList(repository.getLink().toLowerCase(),
        repository.getHttpURL().toLowerCase(), repository.getSshURL().toLowerCase()));

    // {connectorFQN, connectorConfig, List<Trigger>}
    List<TriggerGitConnectorWrapper> triggerGitConnectorWrappers =
        prepareTriggerConnectorWrapperList(originalEvent.getAccountId(), filterRequestData.getDetails());

    List<TriggerDetails> eligibleTriggers = new ArrayList<>();
    for (TriggerGitConnectorWrapper wrapper : triggerGitConnectorWrappers) {
      // update GitConnectionType and repoUrl values in wrapper.
      updateConnectionTypeAndUrlInWrapper(wrapper);

      if (wrapper.getGitConnectionType() == GitConnectionType.REPO) {
        evaluateWrapperForRepoLevelGitConnector(urls, eligibleTriggers, wrapper);
      } else {
        evaluateWrapperForAccountLevelGitConnector(urls, eligibleTriggers, wrapper);
      }
    }

    if (isEmpty(eligibleTriggers)) {
      String msg = String.format("No trigger found for repoUrl: {} for Project {}",
          webhookPayloadData.getRepository().getLink(), filterRequestData.getProjectFqn());
      log.info(msg);
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(
              WebhookEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_REPO, originalEvent, null, null, msg, null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, eligibleTriggers);
    }

    return mappingResponseBuilder.build();
  }

  private void evaluateWrapperForAccountLevelGitConnector(
      Set<String> urls, List<TriggerDetails> eligibleTriggers, TriggerGitConnectorWrapper wrapper) {
    String accUrl = wrapper.getUrl();
    for (TriggerDetails details : wrapper.getTriggers()) {
      final String repoUrl = new StringBuilder(128)
                                 .append(accUrl)
                                 .append(accUrl.endsWith("/") ? EMPTY : '/')
                                 .append(details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName())
                                 .toString();

      String finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(repoUrl)).findAny().orElse(null);

      if (!isBlank(finalUrl)) {
        eligibleTriggers.add(details);
      }
    }
  }

  @VisibleForTesting
  void evaluateWrapperForRepoLevelGitConnector(
      Set<String> urls, List<TriggerDetails> eligibleTriggers, TriggerGitConnectorWrapper wrapper) {
    String url = wrapper.getUrl();
    // accomadate the '/' at the end of the provided repo URL
    final String modifiedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

    String finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(modifiedUrl)).findAny().orElse(null);

    if (!isBlank(finalUrl)) {
      eligibleTriggers.addAll(wrapper.getTriggers());
    }
  }

  @VisibleForTesting
  void updateConnectionTypeAndUrlInWrapper(TriggerGitConnectorWrapper wrapper) {
    ConnectorConfigDTO connectorConfigDTO = wrapper.getConnectorConfigDTO();

    if (connectorConfigDTO.getClass().isAssignableFrom(GithubConnectorDTO.class)) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorConfigDTO;
      wrapper.setUrl(githubConnectorDTO.getUrl());
      wrapper.setGitConnectionType(githubConnectorDTO.getConnectionType());
    } else if (connectorConfigDTO.getClass().isAssignableFrom(GitlabConnectorDTO.class)) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorConfigDTO;
      wrapper.setUrl(gitlabConnectorDTO.getUrl());
      wrapper.setGitConnectionType(gitlabConnectorDTO.getConnectionType());
    } else if (connectorConfigDTO.getClass().isAssignableFrom(BitbucketConnectorDTO.class)) {
      BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorConfigDTO;
      wrapper.setUrl(bitbucketConnectorDTO.getUrl());
      wrapper.setGitConnectionType(bitbucketConnectorDTO.getConnectionType());
    }
  }

  @VisibleForTesting
  List<TriggerGitConnectorWrapper> prepareTriggerConnectorWrapperList(
      String accountId, List<TriggerDetails> triggerDetails) {
    // Map 1
    Map<String, List<TriggerDetails>> triggerToConnectorMap = new HashMap<>();
    triggerDetails.forEach(
        triggerDetail -> generateConnectorFQNFromTriggerConfig(triggerDetail, triggerToConnectorMap));

    // Map 2
    Map<String, ConnectorConfigDTO> connectorMap = new HashMap<>();
    List<ConnectorResponseDTO> connectors =
        ngTriggerService.fetchConnectorsByFQN(accountId, new ArrayList<>(triggerToConnectorMap.keySet()));
    connectors.forEach(connector
        -> connectorMap.put(
            FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountId,
                connector.getConnector().getOrgIdentifier(), connector.getConnector().getProjectIdentifier(),
                connector.getConnector().getIdentifier()),
            connector.getConnector().getConnectorConfig()));

    return connectorMap.keySet()
        .stream()
        .map(fqn
            -> TriggerGitConnectorWrapper.builder()
                   .connectorFQN(fqn)
                   .connectorConfigDTO(connectorMap.get(fqn))
                   .triggers(triggerToConnectorMap.get(fqn))
                   .build())
        .collect(toList());
  }

  @VisibleForTesting
  void generateConnectorFQNFromTriggerConfig(
      TriggerDetails triggerDetail, Map<String, List<TriggerDetails>> triggerToConnectorMap) {
    NGTriggerEntity ngTriggerEntity = triggerDetail.getNgTriggerEntity();
    WebhookMetadata webhook = ngTriggerEntity.getMetadata().getWebhook();
    if (webhook == null) {
      return;
    }

    String fullyQualifiedIdentifier = getFullyQualifiedIdentifierRefString(
        IdentifierRefHelper.getIdentifierRef(webhook.getGit().getConnectorIdentifier(), ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier()));

    List<TriggerDetails> triggerDetailList = triggerToConnectorMap.get(fullyQualifiedIdentifier);
    if (triggerDetailList == null) {
      triggerDetailList = new ArrayList<>();
      triggerToConnectorMap.put(fullyQualifiedIdentifier, triggerDetailList);
    }

    triggerDetailList.add(triggerDetail);
  }
}
