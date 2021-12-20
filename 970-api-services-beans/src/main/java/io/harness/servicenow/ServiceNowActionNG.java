package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDC)
public enum ServiceNowActionNG {
  VALIDATE_CREDENTIALS("Validate Credentials"),
  GET_TICKET_CREATE_METADATA("Get Ticket Create Metadata"),
  GET_TICKET("Get ticket");

  private final String displayName;

  ServiceNowActionNG(String s) {
    displayName = s;
  }

  public String getDisplayName() {
    return displayName;
  }
}