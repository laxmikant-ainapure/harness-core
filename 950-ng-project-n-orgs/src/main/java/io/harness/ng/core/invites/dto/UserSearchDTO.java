package io.harness.ng.core.invites.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(PL)
public class UserSearchDTO {
  @ApiModelProperty(required = true) @NotEmpty String name;
  @ApiModelProperty(required = true) @NotEmpty String email;
  @ApiModelProperty(required = true) @NotEmpty String uuid;

  @Builder
  public UserSearchDTO(String name, String email, String uuid) {
    this.name = name;
    this.email = email;
    this.uuid = uuid;
  }
}
