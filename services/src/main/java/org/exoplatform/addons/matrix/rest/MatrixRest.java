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
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/matrix")
@Tag(name = "/matrix", description = "Manages Matrix integration")
public class MatrixRest implements ResourceContainer {

  private static final Log      LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  private final SpaceService    spaceService;

  private final MatrixService   matrixService;

  public MatrixRest(SpaceService spaceService, MatrixService matrixService) {
    this.spaceService = spaceService;
    this.matrixService = matrixService;
  }

  @GET
  @RolesAllowed("users")
  @Operation(summary = "Get the matrix room bound to the current space", method = "GET", description = "Get the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "2rest00", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response getMatrixRoomBySpaceId(@Parameter(description = "The space Id")
  @QueryParam("spaceId")
  String spaceId) {
    if (StringUtils.isBlank(spaceId)) {
      LOG.error("Could not get the URL for the space, missing space ID");
      return Response.status(Response.Status.BAD_REQUEST).entity("the space Id parameter is required!").build();
    }
    Space space = this.spaceService.getSpaceById(spaceId);
    if (space == null) {
      LOG.error("Could not find a space with id {}", spaceId);
      return Response.status(Response.Status.NOT_FOUND).entity("Can not find a space with Id = " + spaceId).build();
    }
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    if (!spaceService.isMember(space, userName) && !spaceService.isManager(space, userName)
        && !spaceService.isSuperManager(userName)) {
      LOG.error("User is not allowed to get the team associated with the space {}", space.getDisplayName());
      return Response.status(Response.Status.FORBIDDEN)
                     .entity("User " + userName + " is not allowed to get information from space" + space.getPrettyName())
                     .build();
    }

    try {
      return Response.ok().entity(matrixService.getRomBySpace(space)).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND)
                     .entity("There are more than one metadata for the space" + space.getPrettyName())
                     .build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                     .entity("No metadata items for space " + space.getPrettyName())
                     .build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Operation(summary = "Set the matrix room bound to the current space", method = "POST", description = "Set the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response linkSpaceToRoom(@QueryParam("spaceGroupId")
  String spaceGroupId, @QueryParam("roomId")
  String roomId, @QueryParam("create")
  boolean create) {
    try {
      if (StringUtils.isBlank(spaceGroupId)) {
        LOG.error("Could not connect the space to a team, space name is missing");
        return Response.status(Response.Status.BAD_REQUEST).entity("space group Id is required").build();
      }

      Space space = spaceService.getSpaceByGroupId("/spaces/" + spaceGroupId);

      if (space == null) {
        return Response.status(Response.Status.NOT_FOUND).entity("space with group Id " + spaceGroupId + "was not found").build();
      }

      String existingRoomId = matrixService.getRomBySpace(space);
      if (StringUtils.isNotBlank(existingRoomId)) {
        return Response.status(Response.Status.CONFLICT)
                       .entity("space with group Id " + spaceGroupId + "has already a room with ID " + existingRoomId)
                       .build();
      }

      if (StringUtils.isBlank(roomId) && create) {
        roomId = matrixService.createMatrixRoomForSpace(space);
      }

      matrixService.addMatrixMetadata(space, roomId);
      return Response.ok().entity("A new Matrix room {id=" + roomId + " was created for space " + space.getDisplayName()).build();
    } catch (Exception e) {
      LOG.error("Could not link space {} to Matrix team {}", spaceGroupId, roomId);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                     .entity("Could not link space " + spaceGroupId + " to Matrix team " + roomId)
                     .build();
    }
  }
}
