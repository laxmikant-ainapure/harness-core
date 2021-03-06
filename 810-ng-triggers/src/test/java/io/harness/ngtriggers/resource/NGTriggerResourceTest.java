package io.harness.ngtriggers.resource;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfig;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;
import io.harness.yaml.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class NGTriggerResourceTest extends CategoryTest {
  @Mock NGTriggerService ngTriggerService;
  @InjectMocks NGTriggerResource ngTriggerResource;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;

  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private String ngTriggerYaml;

  private NGTriggerDetailsResponseDTO ngTriggerDetailsResponseDTO;
  private NGTriggerResponseDTO ngTriggerResponseDTO;
  private NGTriggerEntity ngTriggerEntity;
  private NGTriggerConfig ngTriggerConfig;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger.yaml";
    ngTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfig.class);
    WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) ngTriggerConfig.getSource().getSpec();
    WebhookMetadata metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType()).build();
    NGTriggerMetadata ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    ngTriggerResponseDTO = NGTriggerResponseDTO.builder()
                               .accountIdentifier(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .targetIdentifier(PIPELINE_IDENTIFIER)
                               .identifier(IDENTIFIER)
                               .name(NAME)
                               .yaml(ngTriggerYaml)
                               .type(NGTriggerType.WEBHOOK)
                               .version(0L)
                               .build();

    ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(NAME)
            .identifier(IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .lastTriggerExecutionDetails(LastTriggerExecutionDetails.builder()
                                             .lastExecutionTime(1607306091861L)
                                             .lastExecutionStatus("SUCCESS")
                                             .lastExecutionSuccessful(false)
                                             .planExecutionId("PYV86FtaSfes7uPrGYJhBg")
                                             .message("Pipeline execution was requested successfully")
                                             .build())
            .webhookDetails(WebhookDetails.builder().webhookSourceRepo("Github").build())
            .enabled(true)
            .build();

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .targetIdentifier(PIPELINE_IDENTIFIER)
                          .identifier(IDENTIFIER)
                          .name(NAME)
                          .targetType(TargetType.PIPELINE)
                          .type(NGTriggerType.WEBHOOK)
                          .metadata(ngTriggerMetadata)
                          .yaml(ngTriggerYaml)
                          .version(0L)
                          .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreate() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).create(any());

    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml).getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);
    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER).getData();
    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdate() throws Exception {
    doReturn(ngTriggerEntity).when(ngTriggerService).update(any());
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
    doReturn(triggerDetails)
        .when(ngTriggerElementMapper)
        .toTriggerDetails(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ngTriggerYaml);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);

    NGTriggerResponseDTO responseDTO =
        ngTriggerResource.update("0", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, ngTriggerYaml).getData();

    assertThat(responseDTO).isEqualTo(ngTriggerResponseDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true)
        .when(ngTriggerService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    when(ngTriggerElementMapper.toResponseDTO(ngTriggerEntity)).thenReturn(ngTriggerResponseDTO);
    Boolean response =
        ngTriggerResource.delete(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER)
            .getData();

    assertThat(response).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testListServicesWithDESCSort() {
    Criteria criteria = TriggerFilterHelper.createCriteriaForGetList("", "", "", "", null, "", false);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    final Page<NGTriggerEntity> serviceList = new PageImpl<>(Collections.singletonList(ngTriggerEntity), pageable, 1);
    doReturn(serviceList).when(ngTriggerService).list(criteria, pageable);

    when(ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false))
        .thenReturn(ngTriggerDetailsResponseDTO);

    List<NGTriggerDetailsResponseDTO> content =
        ngTriggerResource.getListForTarget("", "", "", "", "", 0, 10, null, "").getData().getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getName()).isEqualTo(ngTriggerDetailsResponseDTO.getName());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGitConnectorTrigger() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-git-connector.yaml";
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(triggerYaml, NGTriggerConfig.class);
    WebhookTriggerConfig webhookTriggerConfig = (WebhookTriggerConfig) ngTriggerConfig.getSource().getSpec();
    assertThat(webhookTriggerConfig.getSpec().getRepoSpec().getIdentifier()).isEqualTo("account.gitAccount");
  }
}