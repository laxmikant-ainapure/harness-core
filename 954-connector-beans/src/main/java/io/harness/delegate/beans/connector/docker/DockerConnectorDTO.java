package io.harness.delegate.beans.connector.docker;

import static io.harness.delegate.beans.connector.docker.DockerAuthType.ANONYMOUS;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DockerConnectorDTO extends ConnectorConfigDTO {
  @NotNull @NotBlank String dockerRegistryUrl;
  @NotNull DockerRegistryProviderType providerType;
  @Valid DockerAuthenticationDTO auth;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (auth.getAuthType() == ANONYMOUS) {
      return null;
    }
    return Collections.singletonList(auth.getCredentials());
  }
}
