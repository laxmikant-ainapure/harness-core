package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "WebhookTokenKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "webhookTokens")
@HarnessEntity(exportable = true)
public class WebhookToken implements PersistentEntity, CreatedAtAware, UpdatedAtAware, UuidAware {
  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;

  private String token;
  private String projectIdentifier;
  private String orgIdentifier;
}
