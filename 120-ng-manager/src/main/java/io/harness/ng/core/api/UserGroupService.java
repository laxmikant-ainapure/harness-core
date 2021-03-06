package io.harness.ng.core.api;

import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.entities.UserGroup;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserGroupService {
  UserGroup create(String accountIdentifier, String orgIdentifier, String projectIdentifier, UserGroupDTO userGroup);

  Page<UserGroup> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm, Pageable pageable);

  List<UserGroup> list(List<String> userGroupIds);
}
