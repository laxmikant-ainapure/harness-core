package io.harness.ng.core.invites.remote;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.invites.remote.InviteMapper.toInviteList;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.DuplicateFieldException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.invites.InviteOperationResponse;
import io.harness.ng.core.invites.api.InvitesService;
import io.harness.ng.core.invites.dto.CreateInviteListDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.user.services.api.NgUserService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;

@Api("/invites")
@Path("/invites")
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json", "text/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class InviteResource {
  private static final String PROJECT_URL_FORMAT = "account/%s/projects/%s/orgs/%s/details";
  private static final String ORG_URL_FORMAT = "account/%s/admin/organizations/%s";
  private static final String SIGN_UP_URL = "#/login";
  private static final String INVALID_TOKEN_REDIRECT_URL = "account/%s/error?code=INVITE_EXPIRED";
  private final InvitesService invitesService;
  private final NgUserService ngUserService;
  private final String uiBaseUrl;
  private final String ngUiBaseUrl;

  @Inject
  InviteResource(InvitesService invitesService, NgUserService ngUserService, @Named("uiBaseUrl") String uiBaseUrl,
      @Named("ngUiBaseUrl") String ngUiBaseUrl) {
    this.invitesService = invitesService;
    this.ngUserService = ngUserService;
    this.uiBaseUrl = uiBaseUrl;
    this.ngUiBaseUrl = ngUiBaseUrl;
  }

  @GET
  @ApiOperation(value = "Get all invites for the queried project/organization", nickname = "getInvites")
  @NextGenManagerAuth
  public ResponseDTO<PageResponse<InviteDTO>> getInvites(
      @QueryParam("accountIdentifier") @NotNull String accountIdentifier,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") String projectIdentifier, @BeanParam PageRequest pageRequest) {
    projectIdentifier = stripToNull(projectIdentifier);
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(InviteKeys.createdAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }

    Criteria criteria = Criteria.where(InviteKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(InviteKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(InviteKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(InviteKeys.approved)
                            .is(Boolean.FALSE)
                            .and(InviteKeys.deleted)
                            .is(Boolean.FALSE);
    Page<InviteDTO> invites =
        invitesService.list(criteria, PageUtils.getPageRequest(pageRequest)).map(InviteMapper::writeDTO);
    return ResponseDTO.newResponse(getNGPageResponse(invites));
  }

  @POST
  @ApiOperation(value = "Add a new invite for the specified project/organization", nickname = "sendInvite")
  @NextGenManagerAuth
  public ResponseDTO<List<InviteOperationResponse>> createInvitations(
      @QueryParam("accountIdentifier") @NotNull String accountIdentifier,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") String projectIdentifier,
      @NotNull @Valid CreateInviteListDTO createInviteListDTO) {
    projectIdentifier = stripToNull(projectIdentifier);
    orgIdentifier = stripToNull(orgIdentifier);
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    List<InviteOperationResponse> inviteOperationResponses = new ArrayList<>();
    List<String> usernames = ngUserService.getUsernameFromEmail(accountIdentifier, createInviteListDTO.getUsers());
    List<String> userMails = createInviteListDTO.getUsers();
    for (int i = 0; i < usernames.size(); i++) {
      if (usernames.get(i) == null) {
        String defaultName = userMails.get(i).split("@", 2)[0];
        usernames.set(i, defaultName);
      }
    }
    List<Invite> invites = toInviteList(createInviteListDTO, usernames, ngAccess);

    for (Invite invite : invites) {
      try {
        InviteOperationResponse response = invitesService.create(invite);
        inviteOperationResponses.add(response);
      } catch (DuplicateFieldException ex) {
        log.error("error: ", ex);
      }
    }
    return ResponseDTO.newResponse(inviteOperationResponses);
  }

  @GET
  @Path("/verify")
  @PublicApi
  @ApiOperation(value = "Verify user invite", nickname = "verifyInvite")
  public Response verify(
      @QueryParam("token") @NotNull String jwtToken, @QueryParam("accountIdentifier") String accountIdentifier) {
    Optional<Invite> inviteOpt = invitesService.verify(jwtToken);
    URI redirectURI;
    if (inviteOpt.isPresent()) {
      Invite invite = inviteOpt.get();

      // TODO @Ankush when user signup, check if he has any pending approved invites
      redirectURI = getRedirectURI(invite);
    } else {
      redirectURI = URI.create(ngUiBaseUrl + String.format(INVALID_TOKEN_REDIRECT_URL, accountIdentifier));
    }
    return Response.seeOther(redirectURI).build();
  }

  private URI getRedirectURI(Invite invite) {
    URI redirectURI;
    if (Boolean.TRUE.equals(invite.getDeleted())) {
      if (invite.getProjectIdentifier() == null) {
        redirectURI = URI.create(
            String.format(ngUiBaseUrl + ORG_URL_FORMAT, invite.getAccountIdentifier(), invite.getOrgIdentifier()));
      } else {
        redirectURI = URI.create(String.format(ngUiBaseUrl + PROJECT_URL_FORMAT, invite.getAccountIdentifier(),
            invite.getProjectIdentifier(), invite.getOrgIdentifier()));
      }
    } else {
      redirectURI = URI.create(uiBaseUrl + SIGN_UP_URL);
    }
    return redirectURI;
  }

  @PUT
  @Path("/{inviteId}")
  @ApiOperation(value = "Resend invite mail", nickname = "updateInvite")
  @NextGenManagerAuth
  public ResponseDTO<Optional<InviteDTO>> updateInvite(@PathParam("inviteId") @NotNull String inviteId,
      @NotNull @Valid InviteDTO inviteDTO, @QueryParam("accountIdentifier") String accountIdentifier) {
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountIdentifier).build();
    Invite invite = InviteMapper.toInvite(inviteDTO, ngAccess);
    invite.setId(inviteId);
    Optional<Invite> inviteOptional = invitesService.updateInvite(invite);
    return ResponseDTO.newResponse(inviteOptional.map(InviteMapper::writeDTO));
  }

  @DELETE
  @Path("/{inviteId}")
  @Consumes()
  @ApiOperation(value = "Delete a invite for the specified project/organization", nickname = "deleteInvite")
  @NextGenManagerAuth
  public ResponseDTO<Optional<InviteDTO>> delete(
      @PathParam("inviteId") @NotNull String inviteId, @QueryParam("accountIdentifier") String accountIdentifier) {
    Optional<Invite> inviteOptional = invitesService.deleteInvite(inviteId);
    return ResponseDTO.newResponse(inviteOptional.map(InviteMapper::writeDTO));
  }
}
