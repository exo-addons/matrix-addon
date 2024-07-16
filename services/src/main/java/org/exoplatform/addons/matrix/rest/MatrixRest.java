package org.exoplatform.addons.matrix.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.exoplatform.addons.matrix.services.MatrixConstants.*;

@RestController
@RequestMapping("matrix")
@Tag(name = "/matrix/rest/matrix", description = "Manages Matrix integration")
public class MatrixRest {

  private static final Log LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  private final SpaceService    spaceService;

  private final MetadataService metadataService;

  private final MatrixService   matrixService;

  public MatrixRest(SpaceService spaceService, MetadataService metadataService, MatrixService matrixService) {
    this.spaceService = spaceService;
    this.metadataService = metadataService;
    this.matrixService = matrixService;
  }

  @GetMapping
  @Secured("users")
  @Operation(
          summary = "Get the matrix room bound to the current space",
          method = "GET",
          description = "Get the id of the matrix room bound to the current space")
  @ApiResponses(value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String getMatrixRoomBySpaceId(
          @Parameter(description = "The space Id")
          @RequestParam(name = "spaceId", required = false)
          String spaceId) {
    if(StringUtils.isBlank(spaceId)) {
      LOG.error("Could not get the URL for the space, missing space ID");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the space Id parameter is required!");
    }
    Space space = this.spaceService.getSpaceById(spaceId);
    if(space == null) {
      LOG.error("Could not find a space with id {}", spaceId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can not find a space with Id = " + spaceId);
    }
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    if(!spaceService.isMember(space, userName) && !spaceService.isManager(space, userName) && !spaceService.isSuperManager(userName)) {
      LOG.error("User is not allowed to get the team associated with the space {}", space.getDisplayName());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + userName + " is not allowed to get information from space" + space.getPrettyName());
    }

    try {
      return matrixService.getRomBySpace(space);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There are more than one metadata for the space" + space.getPrettyName());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No metadata items for space " + space.getPrettyName());
    } 
  }

  @PostMapping
  @Secured("users")
  @Operation(summary = "Set the matrix room bound to the current space", method = "POST", description = "Set the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String linkSpaceToRoom(@RequestParam(name = "spaceGroupId")
  String spaceGroupId, @RequestParam(name = "roomId")
  String roomId) {
    try {
      if (StringUtils.isBlank(spaceGroupId)) {
        LOG.error("Could not connect the space to a team, space name is missing");
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space group Id is required");
      }

      Space space = spaceService.getSpaceByGroupId("/spaces/" + spaceGroupId);
      matrixService.addMatrixMetadata(space, roomId);
      return roomId;
    } catch (Exception e) {
      LOG.error("Could not link space {} to Matrix team {}", spaceGroupId, roomId);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
              "Could not link space " + spaceGroupId + " to Matrix team " + roomId);
    }
  }
}
