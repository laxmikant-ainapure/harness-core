package io.harness.ngtriggers.resource;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.utils.PageUtils;
import io.swagger.annotations.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.List;
import java.util.Optional;

import static io.harness.utils.PageUtils.getNGPageResponse;
import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

@Api("triggers")
@Path("triggers")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
public class NGTriggerResource {
  private final NGTriggerService ngTriggerService;

  @POST
  @ApiImplicitParams({
    @ApiImplicitParam(dataTypeClass = NGTriggerConfig.class,
        dataType = "io.harness.ngtriggers.beans.config.NGTriggerConfig", paramType = "body")
  })
  @ApiOperation(value = "Create Trigger", nickname = "createTrigger")
  public ResponseDTO<NGTriggerResponseDTO>
  create(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @ApiParam(hidden = true, type = "") String yaml) {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerElementMapper.toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, yaml);
    NGTriggerEntity createdEntity = ngTriggerService.create(ngTriggerEntity);
    return ResponseDTO.newResponse(
        createdEntity.getVersion().toString(), NGTriggerElementMapper.toResponseDTO(createdEntity));
  }

  @GET
  @Path("/{triggerIdentifier}")
  @ApiOperation(value = "Gets a trigger by identifier", nickname = "getTrigger")
  public ResponseDTO<NGTriggerResponseDTO> get(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("targetIdentifier") String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    return ResponseDTO.newResponse(ngTriggerEntity.get().getVersion().toString(),
        ngTriggerEntity.map(NGTriggerElementMapper::toResponseDTO).orElse(null));
  }

  @PUT
  @Path("/{triggerIdentifier}")
  @ApiImplicitParams({
    @ApiImplicitParam(dataTypeClass = NGTriggerConfig.class,
        dataType = "io.harness.ngtriggers.beans.config.NGTriggerConfig", paramType = "body")
  })
  @ApiOperation(value = "Update a trigger by identifier", nickname = "updateTrigger")
  public ResponseDTO<NGTriggerResponseDTO>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier,
      @NotNull @ApiParam(hidden = true, type = "") String yaml) {
    NGTriggerEntity ngTriggerEntity = NGTriggerElementMapper.toTriggerEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, triggerIdentifier, yaml);
    ngTriggerEntity.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);

    NGTriggerEntity updatedEntity = ngTriggerService.update(ngTriggerEntity);
    return ResponseDTO.newResponse(
        updatedEntity.getVersion().toString(), NGTriggerElementMapper.toResponseDTO(updatedEntity));
  }

  @DELETE
  @Path("{triggerIdentifier}")
  @ApiOperation(value = "Delete a trigger by identifier", nickname = "deleteTrigger")
  public ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("targetIdentifier") String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier) {
    return ResponseDTO.newResponse(ngTriggerService.delete(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @GET
  @ApiOperation(value = "Gets Triggers list for target", nickname = "getTriggerListForTarget")
  public ResponseDTO<PageResponse<NGTriggerResponseDTO>> getListForTarget(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("targetIdentifier") String targetIdentifier, @QueryParam("filter") String filterQuery,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("25") int size,
      @QueryParam("sort") List<String> sort, @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Criteria criteria = TriggerFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, null, searchTerm, false);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<NGTriggerResponseDTO> triggers =
        ngTriggerService.list(criteria, pageRequest).map(NGTriggerElementMapper::toResponseDTO);
    return ResponseDTO.newResponse(getNGPageResponse(triggers));
  }

  @GET
  @Path("/triggersList")
  @ApiOperation(value = "Gets Triggers list for Repo URL", nickname = "getTriggerListForRepoURL")
  public ResponseDTO<PageResponse<NGTriggerResponseDTO>> getListForRepoURL(
      @NotNull @QueryParam("repoURL") String repoURL, @QueryParam("filter") String filterQuery,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("25") int size,
      @QueryParam("sort") List<String> sort, @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Criteria criteria = TriggerFilterHelper.createCriteriaForWebhookTriggerGetList(null, repoURL, searchTerm, false);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest =
          PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGTriggerEntity.NGTriggerEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<NGTriggerResponseDTO> triggers =
        ngTriggerService.list(criteria, pageRequest).map(NGTriggerElementMapper::toResponseDTO);
    return ResponseDTO.newResponse(getNGPageResponse(triggers));
  }
}
