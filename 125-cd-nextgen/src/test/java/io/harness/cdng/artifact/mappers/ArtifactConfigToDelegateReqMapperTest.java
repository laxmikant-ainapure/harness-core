package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactConfigToDelegateReqMapperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequest() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().imagePath(ParameterField.createValueField("IMAGE")).build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList);

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_HUB);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTag()).isEqualTo("");
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo("\\*");
  }
}
