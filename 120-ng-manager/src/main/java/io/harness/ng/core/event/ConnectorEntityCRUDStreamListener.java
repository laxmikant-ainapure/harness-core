package io.harness.ng.core.event;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.exception.WingsException.USER;

import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ConnectorEntityCRUDStreamListener implements MessageListener {
  private final HarnessSMManager harnessSMManager;
  private final CIDefaultEntityManager ciDefaultEntityManager;

  @Inject
  public ConnectorEntityCRUDStreamListener(
      HarnessSMManager harnessSMManager, CIDefaultEntityManager ciDefaultEntityManager) {
    this.harnessSMManager = harnessSMManager;
    this.ciDefaultEntityManager = ciDefaultEntityManager;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        switch (entityType) {
          case ACCOUNT_ENTITY:
            return processAccountChangeEvent(message);
          case ORGANIZATION_ENTITY:
            return processOrganizationChangeEvent(message);
          case PROJECT_ENTITY:
            return processProjectChangeEvent(message);
          default:
        }
      }
    }
    return true;
  }

  private boolean processAccountChangeEvent(Message message) {
    AccountEntityChangeDTO accountEntityChangeDTO;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking AccountEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processAccountCreateEvent(accountEntityChangeDTO);
        case DELETE_ACTION:
          return processAccountDeleteEvent(accountEntityChangeDTO);
        case RESTORE_ACTION:
          return processAccountRestoreEvent(accountEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processAccountCreateEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    try {
      harnessSMManager.createHarnessSecretManager(accountEntityChangeDTO.getAccountId(), null, null);
    } catch (DuplicateFieldException ex) {
      log.error(String.format("Harness Secret Manager for accountIdentifier %s already exists",
                    accountEntityChangeDTO.getAccountId()),
          ex, USER);
    }
    return true;
  }

  private boolean processAccountDeleteEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    return true;
  }

  private boolean processAccountRestoreEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    return true;
  }

  private boolean processOrganizationChangeEvent(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking OrganizationEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processOrganizationCreateEvent(organizationEntityChangeDTO);
        case DELETE_ACTION:
          return processOrganizationDeleteEvent(organizationEntityChangeDTO);
        case RESTORE_ACTION:
          return processOrganizationRestoreEvent(organizationEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processOrganizationCreateEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    try {
      harnessSMManager.createHarnessSecretManager(
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
    } catch (DuplicateFieldException ex) {
      log.error(String.format("Harness Secret Manager for accountIdentifier %s and orgIdentifier %s already exists",
                    organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier()),
          ex, USER);
    }
    return true;
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return true;
  }

  private boolean processOrganizationRestoreEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return true;
  }

  private boolean processProjectChangeEvent(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processProjectCreateEvent(projectEntityChangeDTO);
        case DELETE_ACTION:
          return processProjectDeleteEvent(projectEntityChangeDTO);
        case RESTORE_ACTION:
          return processProjectRestoreEvent(projectEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processProjectCreateEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    try {
      harnessSMManager.createHarnessSecretManager(projectEntityChangeDTO.getAccountIdentifier(),
          projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
    } catch (DuplicateFieldException ex) {
      log.error(
          String.format(
              "Harness Secret Manager for accountIdentifier %s, orgIdentifier %s and projectIdentifier %s already exists",
              projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getOrgIdentifier(),
              projectEntityChangeDTO.getIdentifier()),
          ex, USER);
    }

    ciDefaultEntityManager.createCIDefaultEntities(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
    return true;
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return true;
  }

  private boolean processProjectRestoreEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return true;
  }
}
