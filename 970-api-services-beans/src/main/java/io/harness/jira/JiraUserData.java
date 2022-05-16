package io.harness.jira;

import lombok.Data;
import net.sf.json.JSONObject;

@Data
public class JiraUserData {
  private String accountId;
  private String displayName;
  private boolean active;

  public JiraUserData(JSONObject jsonObject) {
    this.active = jsonObject.getBoolean("active");
    this.accountId = jsonObject.getString("accountId");
    this.displayName = jsonObject.getString("displayName");
  }
}
