package io.harness.ngtriggers.beans.entity.metadata;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthToken {
  String type;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = EXTERNAL_PROPERTY, property = "type", visible = true)
  AuthTokenSpec spec;

  @Builder
  public AuthToken(String type, AuthTokenSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
