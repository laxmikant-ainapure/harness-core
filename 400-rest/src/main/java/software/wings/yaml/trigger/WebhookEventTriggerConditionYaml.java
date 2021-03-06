package software.wings.yaml.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("WEBHOOK")
@JsonPropertyOrder({"harnessApiVersion"})
public class WebhookEventTriggerConditionYaml extends TriggerConditionYaml {
  private String repositoryType;
  private List<String> eventType = new ArrayList<>();
  private List<String> action = new ArrayList<>();
  private List<String> releaseActions = new ArrayList<>();
  private String branchRegex;
  private String gitConnectorId;
  private String repoName;
  private String branchName;
  private List<String> filePaths;
  private Boolean checkFileContentChanged;

  public WebhookEventTriggerConditionYaml() {
    super.setType("WEBHOOK");
  }

  @lombok.Builder
  WebhookEventTriggerConditionYaml(String repositoryType, String branchRegex, List<String> eventType,
      List<String> action, List<String> releaseActions, String gitConnectorId, String repoName, String branchName,
      List<String> filePaths, Boolean checkFileContentChanged) {
    super.setType("WEBHOOK");
    this.eventType = eventType;
    this.action = action;
    this.releaseActions = releaseActions;
    this.repositoryType = repositoryType;
    this.branchRegex = branchRegex;
    this.gitConnectorId = gitConnectorId;
    this.repoName = repoName;
    this.branchName = branchName;
    this.filePaths = filePaths;
    this.checkFileContentChanged = checkFileContentChanged;
  }
}
