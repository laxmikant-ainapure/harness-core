package software.wings.security;

public enum JWT_CATEGORY {
  MULTIFACTOR_AUTH(3 * 60 * 1000), // 3 mins
  SSO_REDIRECT(60 * 1000), // 1 min
  OAUTH_REDIRECT(3 * 60 * 1000), // 1 min
  PASSWORD_SECRET(4 * 60 * 60 * 1000), // 4 hrs
  ZENDESK_SECRET(4 * 60 * 60 * 1000), // 4 hrs
  EXTERNAL_SERVICE_SECRET(60 * 60 * 1000), // 1hr
  IDENTITY_SERVICE_SECRET(60 * 60 * 1000), // 1hr
  AUTH_SECRET(24 * 60 * 60 * 1000), // 24 hr
  JIRA_SERVICE_SECRET(7 * 24 * 60 * 60 * 1000), // 7 days
  MARKETPLACE_SIGNUP(24 * 60 * 60 * 1000), // 1 day
  API_KEY(10 * 60 * 1000), // 10 mins; API_KEY secret is not configured in config.yml!
  DATA_HANDLER_SECRET(60 * 60 * 1000),
  NEXT_GEN_MANAGER_SECRET(60 * 60 * 1000);
  private int validityDuration;

  JWT_CATEGORY(int validityDuration) {
    this.validityDuration = validityDuration;
  }

  public int getValidityDuration() {
    return validityDuration;
  }
}
