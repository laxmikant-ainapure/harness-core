package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapGroupConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class LdapGroupSettings implements LdapGroupConfig {
  @JsonProperty @NotBlank String baseDN;
  @JsonProperty @NotBlank String searchFilter = LdapConstants.DEFAULT_GROUP_SEARCH_FILTER;
  @JsonProperty @NotBlank String nameAttr = "cn";
  @JsonProperty @NotBlank String descriptionAttr = "description";
  @JsonProperty @NotBlank String userMembershipAttr = "member";
  @JsonProperty @NotBlank String referencedUserAttr = "dn";

  @Override
  @JsonIgnore
  public String getFilter(String additionalFilter) {
    String filterString;
    if (EmptyPredicate.isEmpty(additionalFilter)) {
      filterString = String.format("%s", searchFilter);
    } else {
      filterString = String.format("(&(cn=%s)%s)", additionalFilter, searchFilter);
    }
    log.info("LdapGroupSettings filter is {}", filterString);
    return filterString;
  }

  @JsonIgnore
  @Override
  public String[] getReturnAttrs() {
    return new String[] {nameAttr, descriptionAttr};
  }
}
