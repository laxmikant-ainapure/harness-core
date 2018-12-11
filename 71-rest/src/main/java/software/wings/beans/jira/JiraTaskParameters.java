package software.wings.beans.jira;

import lombok.Builder;
import lombok.Data;
import net.rcarz.jiraclient.Priority;
import software.wings.beans.JiraConfig;
import software.wings.delegatetasks.jira.JiraAction;
import software.wings.security.encryption.EncryptedDataDetail;

import java.util.List;

@Data
@Builder
public class JiraTaskParameters {
  private JiraConfig jiraConfig;
  private JiraAction jiraAction;
  private String project;
  private String summary;
  private String description;
  private String issueType;
  private Priority priority;
  private String labels;
  private String assignee;

  private String issueId;
  private String status;
  private String comment;
  List<EncryptedDataDetail> encryptionDetails;

  private String accountId;
  private String appId;
  private String activityId;
  private String approvalId;

  private String webhookUrl;
  private String jiraToken;
}
