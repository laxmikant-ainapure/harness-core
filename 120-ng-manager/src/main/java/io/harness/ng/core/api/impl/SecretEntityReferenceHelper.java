package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.entitysetupusageclient.EntitySetupUsageHelper;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.secretmanagerclient.dto.EncryptedDataDTO;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretEntityReferenceHelper {
  EntitySetupUsageHelper entityReferenceHelper;
  EntitySetupUsageClient entitySetupUsageClient;
  Producer eventProducer;
  IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Inject
  public SecretEntityReferenceHelper(EntitySetupUsageHelper entityReferenceHelper,
      EntitySetupUsageClient entitySetupUsageClient,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer eventProducer,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper) {
    this.entityReferenceHelper = entityReferenceHelper;
    this.entitySetupUsageClient = entitySetupUsageClient;
    this.eventProducer = eventProducer;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
  }

  public void createSetupUsageForSecretManager(EncryptedDataDTO encryptedDataDTO) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(encryptedDataDTO.getAccount(),
        encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getSecretManager());
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(encryptedDataDTO.getAccount(),
        encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getIdentifier());
    IdentifierRefProtoDTO secretReference =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(encryptedDataDTO.getAccount(),
            encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getIdentifier());

    IdentifierRefProtoDTO secretManagerReference =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(encryptedDataDTO.getAccount(),
            encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getSecretManager());

    EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                             .setIdentifierRef(secretReference)
                                             .setType(EntityTypeProtoEnum.SECRETS)
                                             .setName(emptyIfNull(encryptedDataDTO.getName()))
                                             .build();

    EntityDetailProtoDTO secretManagerDetails = EntityDetailProtoDTO.newBuilder()
                                                    .setIdentifierRef(secretManagerReference)
                                                    .setType(EntityTypeProtoEnum.CONNECTORS)
                                                    .setName(emptyIfNull(encryptedDataDTO.getSecretManagerName()))
                                                    .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(encryptedDataDTO.getAccount())
                                                         .setReferredByEntity(secretDetails)
                                                         .addReferredEntities(secretManagerDetails)
                                                         .setDeleteOldReferredByRecords(false)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", encryptedDataDTO.getAccount(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not created when the secret [{}] was created from the secret manager [{}]",
          secretFQN, secretMangerFQN);
    }
  }

  public void deleteSecretEntityReferenceWhenSecretGetsDeleted(EncryptedDataDTO encryptedDataDTO) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(encryptedDataDTO.getAccount(),
        encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getSecretManager());
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(encryptedDataDTO.getAccount(),
        encryptedDataDTO.getOrg(), encryptedDataDTO.getProject(), encryptedDataDTO.getIdentifier());
    boolean entityReferenceDeleted = false;
    try {
      DeleteSetupUsageDTO deleteSetupUsageDTO = DeleteSetupUsageDTO.newBuilder()
                                                    .setAccountIdentifier(encryptedDataDTO.getAccount())
                                                    .setReferredByEntityFQN(secretFQN)
                                                    .setReferredByEntityType(EntityTypeProtoEnum.SECRETS)
                                                    .setReferredEntityFQN(secretMangerFQN)
                                                    .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
                                                    .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", encryptedDataDTO.getAccount(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(deleteSetupUsageDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the secret [{}] was deleted from the secret manager [{}] with the exception [{}]",
          secretFQN, secretMangerFQN, ex.getMessage());
    }
    if (entityReferenceDeleted) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the secret [{}] was deleted from the secret manager [{}]",
          secretFQN, secretMangerFQN);
    }
  }
}
