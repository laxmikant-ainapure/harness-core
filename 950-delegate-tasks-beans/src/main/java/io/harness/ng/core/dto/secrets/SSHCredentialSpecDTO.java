package io.harness.ng.core.dto.secrets;

import io.harness.ng.core.models.SSHCredentialSpec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "credentialType",
    visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = SSHKeyPathCredentialDTO.class, name = "KeyPath")
      , @JsonSubTypes.Type(value = SSHPasswordCredentialDTO.class, name = "Password"),
          @JsonSubTypes.Type(value = SSHKeyReferenceCredentialDTO.class, name = "KeyReference"),
    })
public abstract class SSHCredentialSpecDTO {
  public abstract SSHCredentialSpec toEntity();
}
