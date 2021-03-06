package io.harness.gitsync.core.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "gitWebhookRequestAttributes", noClassnameStored = true)
@Document("gitWebhookRequestAttributes")
@TypeAlias("io.harness.gitsync.core.beans.gitWebhookRequestAttributes")
public class GitWebhookRequestAttributes {
  private String webhookBody;
  private String webhookHeaders;
  @NotEmpty private String branchName;
  @NotEmpty private String gitConnectorId;
  @NotEmpty private String repo;
  String headCommitId;
}
