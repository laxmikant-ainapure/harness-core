package io.harness.delegate.beans.connector.scm.bitbucket;

import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("BitbucketSshCredentials")
public class BitbucketSshCredentialsDTO implements BitbucketCredentialsDTO, DecryptableEntity {
  @NotNull @SecretReference @ApiModelProperty(dataType = "string") SecretRefData sshKeyRef;
}
