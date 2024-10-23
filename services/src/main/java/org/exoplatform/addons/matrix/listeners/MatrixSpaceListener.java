package org.exoplatform.addons.matrix.listeners;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.model.MatrixRoomPermissions;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;

import java.util.*;

import static org.exoplatform.addons.matrix.services.MatrixConstants.*;

public class MatrixSpaceListener extends SpaceListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(MatrixSpaceListener.class);

  private MatrixService matrixService;

  public MatrixSpaceListener(MatrixService matrixService) {
    this.matrixService = matrixService;
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    try {
      String teamDisplayName = space.getDisplayName();
      String description = space.getDescription() != null ? space.getDescription() : "";
      String matrixRoomId = MatrixHttpClient.createRoom(teamDisplayName, description, matrixService.getMatrixAccessToken());

      if (StringUtils.isNotBlank(matrixRoomId)) {
        matrixService.addMatrixMetadata(space, matrixRoomId);
        List<String> members = new ArrayList<>(Arrays.asList(space.getMembers()));
        for(String manager : space.getManagers()) {
          String matrixIdOfUser = matrixService.getMatrixIdForUser(manager);
          if(StringUtils.isNotBlank(matrixRoomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
            MatrixHttpClient.joinUserToRoom(matrixRoomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
            MatrixHttpClient.makeUserAdminInRoom(matrixRoomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
            members.remove(manager);
          }
        }
        for(String member : members) {
          String matrixIdOfUser = matrixService.getMatrixIdForUser(member);
          if(StringUtils.isNotBlank(matrixRoomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
            MatrixHttpClient.joinUserToRoom(matrixRoomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
          }
        }

        // Disable inviting user but for Moderators
        MatrixRoomPermissions matrixRoomPermissions = MatrixHttpClient.getRoomSettings(matrixRoomId, matrixService.getMatrixAccessToken());
        matrixRoomPermissions.setInvite(ADMIN_ROLE);
        MatrixHttpClient.updateRoomSettings(matrixRoomId, matrixRoomPermissions, matrixService.getMatrixAccessToken());
      }
    } catch (Exception e) {
      LOG.error("Mattermost integration: Could not create a team for space {}", space.getDisplayName(), e);
    }
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String spaceDisplayName = space.getDisplayName();
    String roomId;
    try {
      roomId = matrixService.getRoomBySpace(space);
      MatrixHttpClient.renameRoom(roomId, spaceDisplayName, matrixService.getMatrixAccessToken());
    } catch (ObjectNotFoundException e) {
      LOG.warn("Could not find a room linked to the space {}", spaceDisplayName);
    }
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String userId = event.getTarget();
    try {
      String roomId = matrixService.getRoomBySpace(space);
      String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
      if(StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
        MatrixHttpClient.joinUserToRoom(roomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
      }
    } catch (ObjectNotFoundException e) {
      LOG.error("Could not find the room linked to the space {}", space.getDisplayName(), e);
    }
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String userId = event.getTarget();
    try {
      String roomId = matrixService.getRoomBySpace(space);
      String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
      if(StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
        MatrixHttpClient.kickUserFromRoom(roomId, matrixIdOfUser, MESSAGE_USER_KICKED_SPACE.formatted(space.getDisplayName()), matrixService.getMatrixAccessToken());
      }
    } catch (ObjectNotFoundException e) {
      LOG.error("Could not find the room linked to the space {}", space.getDisplayName(), e);
    }
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String roomId ="undefined";
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    try {
      roomId = matrixService.getRoomBySpace(space);
      MatrixHttpClient.makeUserAdminInRoom(roomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
    } catch (ObjectNotFoundException e) {
      LOG.error("Could not revoke administrator role from user {}, on Matrix room {}", matrixIdOfUser, roomId);
    }
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String roomId ="undefined";
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    try {
      roomId = matrixService.getRoomBySpace(space);
      // There is no API to revoke manager role from a user, then we kick out the user from the room then re-adds him as simple user
      //MatrixHttpClient.kickUserFromRoom(roomId, matrixIdOfUser, "Revoke manager role of user");
      MatrixHttpClient.joinUserToRoom(roomId, matrixIdOfUser, matrixService.getMatrixAccessToken());
    } catch (ObjectNotFoundException e) {
      LOG.error("Could not revoke administrator role from user {}, on Matrix room {}", matrixIdOfUser, roomId);
    }
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String roomId ="undefined";
    String mimeType = "image/jpg";
    try {
      roomId = matrixService.getRoomBySpace(space);
      if(space.getAvatarAttachment() != null && space.getAvatarAttachment().getImageBytes() != null) {
        byte[] imageBytes = space.getAvatarAttachment().getImageBytes();
        if(!"application/octet-stream".equals(space.getAvatarAttachment().getMimeType())) {
          mimeType = space.getAvatarAttachment().getMimeType();
        }
        String avatarURL = MatrixHttpClient.uploadFile(space.getAvatarAttachment().getFileName(), mimeType, imageBytes, matrixService.getMatrixAccessToken());
        MatrixHttpClient.updateRoomAvatar(roomId, avatarURL, matrixService.getMatrixAccessToken());
      }
    } catch (ObjectNotFoundException e) {
      LOG.error("Could not upload the avatar of the space {}, on Matrix room {}", space.getDisplayName(), roomId);
    }
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String spaceDisplayName = space.getDisplayName();
    String roomId;
    try {
      roomId = matrixService.getRoomBySpace(space);
      MatrixHttpClient.updateRoomDescription(roomId, space.getDescription(), matrixService.getMatrixAccessToken());
    } catch (ObjectNotFoundException e) {
      LOG.warn("Could not find a room linked to the space {}", spaceDisplayName);
    }
  }
}
