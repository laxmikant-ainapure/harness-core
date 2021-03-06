package io.harness.ng.core.impl;

import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.api.ProducerShutdownException;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.invites.entities.Role;
import io.harness.ng.core.invites.entities.UserProjectMap;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.user.services.api.NgUserService;
import io.harness.repositories.core.spring.OrganizationRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.PrincipalType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class OrganizationServiceImpl implements OrganizationService {
  private final OrganizationRepository organizationRepository;
  private final Producer eventProducer;
  private final NgUserService ngUserService;
  private static final String ORGANIZATION_ADMIN_ROLE_NAME = "Organization Admin";

  @Inject
  public OrganizationServiceImpl(OrganizationRepository organizationRepository,
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, NgUserService ngUserService) {
    this.organizationRepository = organizationRepository;
    this.eventProducer = eventProducer;
    this.ngUserService = ngUserService;
  }

  @Override
  public Organization create(String accountIdentifier, OrganizationDTO organizationDTO) {
    validateCreateOrganizationRequest(accountIdentifier, organizationDTO);
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    try {
      validate(organization);
      Organization savedOrganization = organizationRepository.save(organization);
      performActionsPostOrganizationCreation(organization);
      return savedOrganization;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format(
              "An organization with identifier %s is already present or was deleted", organization.getIdentifier()),
          USER_SRE, ex);
    }
  }

  private void performActionsPostOrganizationCreation(Organization organization) {
    publishEvent(organization, EventsFrameworkMetadataConstants.CREATE_ACTION);
    createUserProjectMap(organization);
  }

  private void publishEvent(Organization organization, String action) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", organization.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getOrganizationPayload(organization))
              .build());
    } catch (ProducerShutdownException e) {
      log.error("Failed to send event to events framework orgIdentifier: " + organization.getIdentifier(), e);
    }
  }

  private ByteString getOrganizationPayload(Organization organization) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(organization.getIdentifier())
        .setAccountIdentifier(organization.getAccountIdentifier())
        .build()
        .toByteString();
  }

  private void createUserProjectMap(Organization organization) {
    if (SecurityContextBuilder.getPrincipal() != null
        && SecurityContextBuilder.getPrincipal().getType() == PrincipalType.USER) {
      String userId = SecurityContextBuilder.getPrincipal().getName();
      Role role = Role.builder()
                      .accountIdentifier(organization.getAccountIdentifier())
                      .orgIdentifier(organization.getIdentifier())
                      .name(ORGANIZATION_ADMIN_ROLE_NAME)
                      .build();
      UserProjectMap userProjectMap = UserProjectMap.builder()
                                          .userId(userId)
                                          .accountIdentifier(organization.getAccountIdentifier())
                                          .orgIdentifier(organization.getIdentifier())
                                          .roles(singletonList(role))
                                          .build();
      ngUserService.createUserProjectMap(userProjectMap);
    }
  }

  @Override
  public Optional<Organization> get(String accountIdentifier, String organizationIdentifier) {
    return organizationRepository.findByAccountIdentifierAndIdentifierAndDeletedNot(
        accountIdentifier, organizationIdentifier, true);
  }

  @Override
  public Organization update(String accountIdentifier, String identifier, OrganizationDTO organizationDTO) {
    validateUpdateOrganizationRequest(accountIdentifier, identifier, organizationDTO);
    Optional<Organization> optionalOrganization = get(accountIdentifier, identifier);

    if (optionalOrganization.isPresent()) {
      Organization existingOrganization = optionalOrganization.get();
      if (existingOrganization.getHarnessManaged()) {
        throw new InvalidRequestException(
            String.format("Update operation not supported for Default Organization (identifier: [%s])", identifier),
            USER);
      }
      Organization organization = toOrganization(organizationDTO);
      organization.setAccountIdentifier(accountIdentifier);
      organization.setId(existingOrganization.getId());
      if (organization.getVersion() == null) {
        organization.setVersion(existingOrganization.getVersion());
      }

      validate(organization);
      Organization updatedOrganization = organizationRepository.save(organization);
      publishEvent(existingOrganization, EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return updatedOrganization;
    }
    throw new InvalidRequestException(String.format("Organisation with identifier [%s] not found", identifier), USER);
  }

  @Override
  public Page<Organization> list(
      String accountIdentifier, Pageable pageable, OrganizationFilterDTO organizationFilterDTO) {
    Criteria criteria = createOrganizationFilterCriteria(Criteria.where(OrganizationKeys.accountIdentifier)
                                                             .is(accountIdentifier)
                                                             .and(OrganizationKeys.deleted)
                                                             .ne(Boolean.TRUE),
        organizationFilterDTO);
    return organizationRepository.findAll(criteria, pageable);
  }

  @Override
  public Page<Organization> list(Criteria criteria, Pageable pageable) {
    return organizationRepository.findAll(criteria, pageable);
  }

  @Override
  public List<Organization> list(Criteria criteria) {
    return organizationRepository.findAll(criteria);
  }

  private Criteria createOrganizationFilterCriteria(Criteria criteria, OrganizationFilterDTO organizationFilterDTO) {
    if (organizationFilterDTO == null) {
      return criteria;
    }
    if (isNotBlank(organizationFilterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(OrganizationKeys.name).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.identifier).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.tags + "." + NGTagKeys.key).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.tags + "." + NGTagKeys.value)
              .regex(organizationFilterDTO.getSearchTerm(), "i"));
    }
    if (Objects.nonNull(organizationFilterDTO.getIdentifiers()) && !organizationFilterDTO.getIdentifiers().isEmpty()) {
      Criteria.where(OrganizationKeys.identifier).in(organizationFilterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public boolean delete(String accountIdentifier, String organizationIdentifier, Long version) {
    boolean delete = organizationRepository.delete(accountIdentifier, organizationIdentifier, version);
    if (delete) {
      publishEvent(
          Organization.builder().accountIdentifier(accountIdentifier).identifier(organizationIdentifier).build(),
          EventsFrameworkMetadataConstants.DELETE_ACTION);
    }
    return delete;
  }

  @Override
  public boolean restore(String accountIdentifier, String identifier) {
    boolean success = organizationRepository.restore(accountIdentifier, identifier);
    if (success) {
      publishEvent(Organization.builder().accountIdentifier(accountIdentifier).identifier(identifier).build(),
          EventsFrameworkMetadataConstants.RESTORE_ACTION);
    }
    return success;
  }

  private void validateCreateOrganizationRequest(String accountIdentifier, OrganizationDTO organization) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, organization.getAccountIdentifier())), true);
  }

  private void validateUpdateOrganizationRequest(
      String accountIdentifier, String identifier, OrganizationDTO organization) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, organization.getAccountIdentifier())), true);
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(identifier, organization.getIdentifier())), false);
  }
}