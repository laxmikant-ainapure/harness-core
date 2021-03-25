package io.harness.ng.core.auditevent;

import static io.harness.audit.ResourceTypeConstants.ORGANIZATION;

import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.dto.OrganizationDTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrganizationRestoreEvent implements Event {
  private OrganizationDTO organization;
  private String accountIdentifier;

  public OrganizationRestoreEvent(String accountIdentifier, OrganizationDTO organization) {
    this.organization = organization;
    this.accountIdentifier = accountIdentifier;
  }

  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  public Resource getResource() {
    return Resource.builder().identifier(organization.getIdentifier()).type(ORGANIZATION).build();
  }

  public String getEventType() {
    return "OrganizationRestored";
  }
}
