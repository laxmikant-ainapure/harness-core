package io.harness.resourcegroupclient;

import io.harness.resourcegroup.remote.dto.ResourceGroupDTO;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResourceGroupResponse {
  @NotNull private ResourceGroupDTO resourceGroup;
  private Long createdAt;
  private Long lastModifiedAt;

  @Builder
  public ResourceGroupResponse(ResourceGroupDTO resourceGroup, Long createdAt, Long lastModifiedAt) {
    this.resourceGroup = resourceGroup;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
  }
}