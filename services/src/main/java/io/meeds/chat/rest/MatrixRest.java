/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.rest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.ChatSearchResult;
import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.ChatNotificationService;
import io.meeds.chat.service.MatrixSynchronizationService;
import io.meeds.chat.service.utils.AsyncTaskUtils;
import io.meeds.chat.service.model.*;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@RestController
@RequestMapping("/matrix")
@Tag(name = "/matrix", description = "Manages Matrix integration")
public class MatrixRest implements ResourceContainer {

  private static final Log             LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  @Autowired
  private SpaceService                 spaceService;

  @Autowired
  private MatrixService                matrixService;

  @Autowired
  private MatrixSynchronizationService matrixSynchronizationService;

  @Autowired
  private IdentityManager              identityManager;

  @Autowired
  private NotificationService          notificationService;

  @Autowired
  private ResourceBundleService        resourceBundleService;

  @Autowired
  private ChatNotificationService      chatNotificationService;
  @GetMapping
  @Secured("users")
  @Operation(summary = "Get the matrix room bound to the current space", method = "GET", description = "Get the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity getMatrixRoomBySpaceId(HttpServletRequest request,
                                           @Parameter(description = "The space Id")
                                           @RequestParam(name = "spaceId")
                                           String spaceId) {
    if (StringUtils.isBlank(spaceId)) {
      LOG.error("Could not get the URL for the space, missing space ID");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the space Id parameter is required!");
    }
    Space space = this.spaceService.getSpaceById(spaceId);
    if (space == null) {
      LOG.error("Could not find a space with id {}", spaceId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can not find a space with Id = " + spaceId);
    }
    String userName = request.getRemoteUser();
    if (!spaceService.isMember(space, userName) && !spaceService.isManager(space, userName)
        && !spaceService.isSuperManager(userName)) {
      LOG.error("User is not allowed to get the team associated with the space {}", space.getDisplayName());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "User " + userName + " is not allowed to get information from space"
                                            + space.getPrettyName());
    }

    Room room = matrixService.getRoomBySpace(space);

    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no Matrix room for space " + space.getDisplayName());
    }
    return buildRoomEntityFromRoom(room, userName);
  }

  @GetMapping("dmRoom")
  @Secured("users")
  @Operation(summary = "Get the matrix room used for direct messaging between provided users", method = "GET", description = "Get the matrix room used for direct messaging between provided users")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity getDirectMessagingRoom(HttpServletRequest request,
                                           @Parameter(description = "The first participant")
                                           @RequestParam(name = "firstParticipant")
                                           String firstParticipant,
                                           @Parameter(description = "The second participant")
                                           @RequestParam(name = "secondParticipant")
                                           String secondParticipant) {
    String currentUser = request.getRemoteUser();
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    Room directMessagingRoom = matrixService.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (directMessagingRoom != null) {
      return buildRoomEntityFromRoom(directMessagingRoom, currentUser);
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        "Could not find a room for participants %s and %s".formatted(firstParticipant,
                                                                                                     secondParticipant));
    }
  }

  @PostMapping
  @Secured("users")
  @Operation(summary = "Gets or creates the Matrix room for the direct messaging", method = "POST", description = "Gets or creates the Matrix room for the direct messaging")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "403", description = "Private rooms are deactivated"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity createDirectMessagingRoom(HttpServletRequest request,
                                              @RequestBody(description = "Matrix object to create", required = true)
                                              @org.springframework.web.bind.annotation.RequestBody
                                              Room room) {
    if (StringUtils.isBlank(room.getFirstParticipant()) || StringUtils.isBlank(room.getSecondParticipant())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    if (!this.matrixService.loadChatSettings().isPrivateRoomsEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat private rooms are deactivated in the system");
    }
    try {
      String currentUserName = request.getRemoteUser();
      return buildRoomEntityFromRoom(matrixService.createDirectMessagingRoom(room), currentUserName);
    } catch (ObjectAlreadyExistsException objectAlreadyExists) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, objectAlreadyExists.getMessage());
    }
  }

  /**
   * This API is used by Matrix server to notify the Meeds server that a user has
   * a new notification This API is used as a Push Gateway for Matrix server
   * 
   * @param notification the content of the notification received from Matrix
   *          server
   * @return String representing the list of rejected pushKeys on Matrix
   */
  @PostMapping("notify")
  @Operation(summary = "Receives push notification from Matrix", method = "POST", description = "Receives push notification from Matrix")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String notify(@RequestBody(description = "Notification received from Matrix", required = true)
  @org.springframework.web.bind.annotation.RequestBody
  String notification) {

    JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
    try {
      JsonValue jsonValue = jsonGenerator.createJsonObjectFromString(notification);
      JsonValue notifJsonValue = jsonValue.getElement("notification");
      String pushKey;
      if (notifJsonValue.getElement("devices").getElements().hasNext()) {
        JsonValue device = notifJsonValue.getElement("devices").getElements().next();
        pushKey = device.getElement("pushkey").getStringValue();
        if (StringUtils.isNotBlank(pushKey)) {
          String userName;
          try {
            userName = checkAndParseUserFromToken(pushKey);
          } catch (ExpiredJwtException expiredException) {
            // if the push key is expired, then it should be unregistered from Matrix server
            return """
                {
                  "rejected": ["%s"]
                }
                """.formatted(pushKey);
          }
          if (StringUtils.isNotBlank(userName)) {
            String roomId = "";
            if (notifJsonValue.getElement("room_id") != null) {
              roomId = notifJsonValue.getElement("room_id").getStringValue();
            }
            String eventId = "";
            if (notifJsonValue.getElement("event_id") != null) {
              eventId = notifJsonValue.getElement("event_id").getStringValue();
            }
            if (StringUtils.isNotBlank(eventId) && StringUtils.isNotBlank(roomId)) {
              chatNotificationService.onMatrixPushReceived(eventId, roomId, userName, pushKey);
            }
          }
        }
      }
      return """
          {
            "rejected": []
          }
          """;
    } catch (Exception e) {
      LOG.error("Problem parsing notification received from Matrix", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  @GetMapping("linkRoom")
  @Secured("users")
  @Operation(summary = "Set the matrix room bound to the current space", method = "POST", description = "Set the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public boolean linkSpaceToRoom(@RequestParam("spaceGroupId")
  String spaceGroupId, @RequestParam(name = "roomId")
  String roomId, @RequestParam(name = "create", required = false)
  Boolean create) {
    if (StringUtils.isBlank(spaceGroupId)) {
      LOG.error("Could not connect the space to a team, space name is missing");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space group Id is required");
    }

    Space space = spaceService.getSpaceByGroupId("/spaces/" + spaceGroupId);

    if (space == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "space with group Id " + spaceGroupId + " was not found");
    }

    Room room = matrixService.getRoomBySpace(space);
    if (room != null && StringUtils.isNotBlank(room.getRoomId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "space with group Id " + spaceGroupId + "has already a room with ID " + room.getRoomId());
    }

    if (StringUtils.isBlank(roomId) && create) {
      try {
        roomId = matrixService.createRoomForSpaceOnMatrix(space);
      } catch (Exception e) {
        LOG.error("Could not link space {} to Matrix room {}", spaceGroupId, roomId, e);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                          "Could not link space " + spaceGroupId + " to Matrix room " + roomId + " : "
                                              + e.getMessage());
      }
    }

    room = matrixService.linkSpaceToMatrixRoom(space, roomId);
    return room != null;
  }

  @GetMapping("dmRooms")
  @Secured("users")
  @Operation(summary = "Get all the matrix rooms used for direct messaging of a defined user", method = "GET", description = "Get all the matrix rooms used for direct messaging of a defined user")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Map<String, String[]> getUserDirectMessagingRooms(@Parameter(description = "The user")
  @RequestParam(name = "user")
  String user) {

    Map<String, String[]> userDMRooms = new HashMap<>();
    List<Room> rooms = matrixService.getMatrixDMRoomsOfUser(user);
    for (Room dmRoom : rooms) {
      Identity userIdentity;
      if (dmRoom.getFirstParticipant().equals(user)) {
        userIdentity = identityManager.getOrCreateUserIdentity(dmRoom.getSecondParticipant());
      } else {
        userIdentity = identityManager.getOrCreateUserIdentity(dmRoom.getFirstParticipant());
      }
      if (userIdentity != null) {
        String userMatrixId = "@" + userIdentity.getProfile().getProperty(USER_MATRIX_ID) + ":"
            + PropertyManager.getProperty(MATRIX_SERVER_NAME);
        userDMRooms.put(userMatrixId, new String[] { dmRoom.getRoomId() });
      }
    }
    return userDMRooms;
  }

  @GetMapping("byRoom")
  @Secured("users")
  @Operation(summary = "Get the space linked to the specified matrix room", method = "GET", description = "Get the space linked to the specified matrix room")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "Space not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String getByRoomId(HttpServletRequest request,
                            @Parameter(description = "The room Id")
                            @RequestParam(name = "roomId")
                            String roomId) {
    if (StringUtils.isNotBlank(roomId) && roomId.contains(PropertyManager.getProperty(MATRIX_SERVER_NAME))) {
      roomId = roomId.substring(0, roomId.indexOf(":"));
    }
    Room room = matrixService.getById(roomId);
    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no room with ID " + roomId);
    } else {
      if (room.getSpaceId() != null) {
        Space space = spaceService.getSpaceById(room.getSpaceId());
        if (space != null) {
          return identityManager.getOrCreateSpaceIdentity(space.getPrettyName()).getId();
        } else {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no space with ID " + room.getSpaceId());
        }
      } else {
        String currentUserName = request.getRemoteUser();
        if (room.getFirstParticipant().equals(currentUserName)) {
          return identityManager.getOrCreateUserIdentity(room.getSecondParticipant()).getId();
        } else {
          return identityManager.getOrCreateUserIdentity(room.getFirstParticipant()).getId();
        }
      }
    }
  }

  @GetMapping("search")
  @Secured("users")
  @Operation(summary = "Search chat messages", method = "GET",
             description = "Full-text search the current user's chat messages, optionally scoped to one conversation")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query parameter"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<List<ChatSearchResult>> searchMessages(HttpServletRequest request,
                                                               @Parameter(description = "Free-text search term")
                                                               @RequestParam(name = "query")
                                                               String query,
                                                               @Parameter(description = "Optional room id to scope the search")
                                                               @RequestParam(name = "conversationId", required = false)
                                                               String conversationId,
                                                               @Parameter(description = "Maximum number of hits")
                                                               @RequestParam(name = "limit", required = false, defaultValue = "20")
                                                               int limit) {
    if (StringUtils.isBlank(query)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query parameter is required");
    }
    String userName = request.getRemoteUser();
    List<ChatSearchResult> results = matrixService.searchChatMessages(userName, query, conversationId, limit);
    // Enrich each hit with the conversation avatar for the UI, reusing the same
    // resolution as the room list (buildRoomEntityFromRoom -> updateRoomEntity).
    Map<String, RoomEntity> roomEntities = new HashMap<>();
    for (ChatSearchResult result : results) {
      RoomEntity roomEntity = roomEntities.computeIfAbsent(result.getConversationId(), id -> {
        Room room = matrixService.getById(id, true);
        return room == null ? null : buildRoomEntityFromRoom(room, userName);
      });
      if (roomEntity != null) {
        result.setAvatarUrl(roomEntity.getAvatarUrl());
        result.setDirectChat(roomEntity.isDirectChat());
        if (StringUtils.isBlank(result.getConversationTitle()) && StringUtils.isNotBlank(roomEntity.getName())) {
          // The service resolves titles from the user's conversation list, which misses rooms it
          // does not track (a direct message opened from another Matrix client, a room not synced
          // yet…). The room entity carries the same display name the room list shows — the space
          // name, or the other member's full name for a direct chat — so use it rather than
          // letting the UI fall back to the raw Matrix room id.
          result.setConversationTitle(roomEntity.getName());
        }
      }
    }
    return ResponseEntity.ok().body(results);
  }

  @GetMapping("byRoomId")
  @Secured("users")
  @Operation(summary = "Get the room by Id", method = "GET", description = "Get the room by Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomEntity> getRoomById(HttpServletRequest request,
                                                @Parameter(description = "The room Id")
                                                @RequestParam(name = "roomId")
                                                String roomId) {
    if (StringUtils.isBlank(roomId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId parameter is required");
    }
    String userName = request.getRemoteUser();
    Room room = matrixService.getById(roomId);
    if (room == null || !matrixService.canAccess(room, userName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    RoomEntity roomEntity = buildRoomEntityFromRoom(room, userName);
    return ResponseEntity.ok().body(roomEntity);
  }

  @GetMapping("spaceRoom")
  @Secured("users")
  @Operation(summary = "Get the room by Id", method = "GET", description = "Get the room by Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomEntity> getRoomBySpaceId(HttpServletRequest request,
                                                     @Parameter(description = "The room Id")
                                                     @RequestParam(name = "spaceId")
                                                     long spaceId) {
    String userName = request.getRemoteUser();
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Room room;
    if (spaceService.canManageSpace(space, userName)) {
      room = matrixService.getRoomBySpace(space, true);
    } else {
      room = matrixService.getRoomBySpace(space);
    }

    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (!matrixService.canAccess(room, userName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    RoomEntity roomEntity = buildRoomEntityFromRoom(room, userName);
    return ResponseEntity.ok().body(roomEntity);
  }

  @PostMapping("processRooms")
  @Secured("users")
  @Operation(summary = "Process the list of rooms and add needed information", method = "POST", description = "Process the list of rooms and add needed information")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomList> processRooms(HttpServletRequest request,
                                               @RequestBody(description = "Rooms received from Matrix", required = true)
                                               @org.springframework.web.bind.annotation.RequestBody
                                               RoomList rooms) {
    String userName = request.getRemoteUser();
    rooms = processRooms(rooms, userName);
    return ResponseEntity.ok().body(rooms);
  }

  @PutMapping("setStatus")
  @Secured("users")
  @Operation(summary = "Set the presence status of the user", method = "PUT", description = "Set the presence status of the user")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> updatePresenceStatus(@RequestBody(description = "Rooms received from Matrix", required = true)
  @org.springframework.web.bind.annotation.RequestBody
  Presence presence) {
    String presenceStatus = matrixService.updateUserPresence(presence.getUserIdOnMatrix(),
                                                             presence.getPresence(),
                                                             presence.getStatusMessage());
    return ResponseEntity.ok().body(presenceStatus);
  }

  @GetMapping("sync")
  @Secured("administrators")
  @Operation(summary = "Get the user Identity for the provided matrix user Id", method = "GET", description = "Get the user Identity for the provided matrix user Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> syncUsersAndSpaces() {
    try {
      matrixSynchronizationService.synchronizeUsers();
      matrixSynchronizationService.synchronizeSpaces();
      return ResponseEntity.ok().body("Synchronization finished for users and spaces with Matrix server");
    } catch (Exception e) {
      LOG.error("Could not synchronise users and spaces", e);
      return ResponseEntity.internalServerError()
                           .body("An error occurred when synchronizing users and spaces with Matrix server");
    }
  }

  @GetMapping("participant/{userId}")
  @Secured("users")
  @Operation(summary = "Get participant info including its created matrix id using its user Id", method = "GET", description = "Get participant info including its created matrix id using its user Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<?> getParticipantInfo(@PathVariable("userId")
  String userId) {
    try {
      if (userId == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("user id is mandatory");
      }
      return ResponseEntity.ok().body(buildRoomMember(userId));
    } catch (Exception e) {
      LOG.error("Could not get participant info of userId: {}", userId, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  @PutMapping("enable/{spaceId}")
  @Secured("users")
  @Operation(summary = "Enable the chat for a given space", method = "GET", description = "Enable the chat for a space ")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> enableChat(HttpServletRequest request, @PathVariable("spaceId")
  String spaceId) {
    String currentUserName = request.getRemoteUser();

    if (StringUtils.isBlank(spaceId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Space id is mandatory");
    }
    Space space = spaceService.getSpaceById(spaceId);
    if (!spaceService.canManageSpace(space, currentUserName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Current user does not have the needed privileges to enable the chat of the space");
    }
    Room roomToEnable = matrixService.getRoomBySpace(space, true);
    try {
      if (roomToEnable == null) {
        String roomId = matrixService.createRoom(space);
        roomToEnable = matrixService.getById(roomId);
      }
      class EnableChatRunnable implements Runnable {
        @Override
        public void run() {
          ExoContainerContext.setCurrentContainer(PortalContainer.getInstance());
          RequestLifeCycle.begin(PortalContainer.getInstance());
          try {
            matrixService.enableSpaceChat(space, true);
          } catch (ObjectNotFoundException e) {
            LOG.error("Could not enable the room of the space {}", space.getDisplayName(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              "Could not enable the chat for the space with id " + spaceId);
          } finally {
            RequestLifeCycle.end();
          }
        }
      }
      AsyncTaskUtils.runAsync("Enable members in space Thread", new EnableChatRunnable());
      roomToEnable.setStatus(RoomStatus.ENABLE_IN_PROGRESS.name());
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Could not enable the chat for the space with id " + spaceId);
    }
  }

  @PutMapping("disable/{spaceId}")
  @Secured("users")
  @Operation(summary = "Enable the chat for a given space", method = "GET", description = "Enable the chat for a space ")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> disableChat(HttpServletRequest request, @PathVariable("spaceId")
  String spaceId) {
    String currentUserName = request.getRemoteUser();

    if (StringUtils.isBlank(spaceId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Space id is mandatory");
    }
    Space space = spaceService.getSpaceById(spaceId);
    if (!spaceService.canManageSpace(space, currentUserName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Current user does not have the needed privileges to enable the chat of the space");
    }
    Room roomToDisable = matrixService.getRoomBySpace(space, true);
    try {
      if (roomToDisable == null) {
        String roomId = matrixService.createRoom(space);
        roomToDisable = matrixService.getById(roomId, true);
      }
      class EnableChatRunnable implements Runnable {
        @Override
        public void run() {
          ExoContainerContext.setCurrentContainer(PortalContainer.getInstance());
          RequestLifeCycle.begin(PortalContainer.getInstance());
          try {
            matrixService.enableSpaceChat(space, false);
          } catch (ObjectNotFoundException e) {
            LOG.error("Could not disable the room of the space {}", space.getDisplayName(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              "Could not disable the chat for the space with id " + spaceId);
          } finally {
            RequestLifeCycle.end();
          }
        }
      }
      AsyncTaskUtils.runAsync("Disable members in space Thread", new EnableChatRunnable());
      roomToDisable.setStatus(RoomStatus.DISABLED_IN_PROGRESS.name());
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      LOG.error("Could not disable the room of the space {}", space.getDisplayName(), e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Could not disable the chat for the space with id " + spaceId);
    }
  }

  @PutMapping("notification/{roomId}/{eventId}/{ts}")
  @Secured("users")
  @Operation(summary = "Get the details of a notification based on the event details", method = "GET", description = "Get the details of a notification based on the event details")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public PwaNotificationMessage getNotification(HttpServletRequest request, @PathVariable("roomId")
  String roomId, @PathVariable("eventId")
  String eventId, @PathVariable("ts")
  String timeStamp,
                                                @RequestBody(description = "Access token of the user", required = false)
                                                @org.springframework.web.bind.annotation.RequestBody(required = false)
                                                String accessToken) {
    String currentUserName = request.getRemoteUser();
    if (eventId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event id is mandatory");
    }
    long ts = 0L;
    try {
      ts = Long.parseLong(timeStamp);
    } catch (NumberFormatException nfe) {
      // Do nothing, we consider Timestamp as 0 //NOSONAR
    }
    PwaNotificationMessage pwaMessage = chatNotificationService.createNotification(eventId,
                                                                                   roomId,
                                                                                   currentUserName,
                                                                                   ts,
                                                                                   accessToken);
    if (pwaMessage != null) {
      return pwaMessage;
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found or it is not a chat message");
    }
  }

  @PostMapping("/muteRoom")
  @Secured("users")
  @Operation(summary = "Mute a private room for the current user", description = "Adds a private room to the user's muted list")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Room muted successfully"),
      @ApiResponse(responseCode = "400", description = "Missing or invalid parameters"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> muteRoom(HttpServletRequest request,
                                         @Parameter(description = "ID of the room to mute")
                                         @RequestParam(name = "roomId")
                                         String roomId) {

    String userName = request.getRemoteUser();
    if (StringUtils.isBlank(roomId)) {
      return ResponseEntity.badRequest().body("roomId parameter is required");
    }
    try {
      chatNotificationService.toggleMutePrivateRoom(userName, roomId);
      return ResponseEntity.ok("Room muted successfully");
    } catch (Exception e) {
      LOG.error("Error muting room {} for user {}", roomId, userName, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to mute room");
    }
  }

  @PostMapping("rooms/{roomId}/read")
  @Secured("users")
  @Operation(summary = "Marks a room as read up to an event for the current user", method = "POST",
             description = "Server-side read anchor: posts the Matrix read receipt with the user's identity, records the read watermark and cancels the pending push popups")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Room marked as read"),
      @ApiResponse(responseCode = "400", description = "Invalid parameters"),
      @ApiResponse(responseCode = "403", description = "Not a member of the room"),
      @ApiResponse(responseCode = "404", description = "Room not found") })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRoomAsRead(HttpServletRequest request,
                             @PathVariable("roomId")
                             String roomId,
                             @RequestParam("eventId")
                             String eventId,
                             @RequestParam(value = "ts", required = false)
                             Long readTimestamp) {
    try {
      chatNotificationService.markRoomAsRead(request.getRemoteUser(), roomId, eventId, readTimestamp);
    } catch (ObjectNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalAccessException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping("findId/{userId}")
  @Secured("users")
  @Operation(summary = "Get the matrix ID of a user", method = "GET", description = "Get the matrix ID of a user")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> getMatrixId(@PathVariable("userId")
  String userId) {
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user id is mandatory");
    }
    String matrixId = matrixService.getMatrixIdForUser(userId);
    if (StringUtils.isNotBlank(matrixId)) {
      return ResponseEntity.ok().body(matrixId);
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no Matrix account for the user " + userId);
    }
  }

  @GetMapping(value = "spaceChatSetting/{spaceId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("users")
  @Operation(summary = "Get the status of the Chat on this space", method = "GET", description = "Get the status of the Chat on this space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "No space found or the space Id is missing"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String getChatAuthorizationStatus(HttpServletRequest request, @PathVariable("spaceId")
  long spaceId) {
    if (spaceId == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space id is mandatory");
    }
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space was not found");
    }

    Room spaceRoom = matrixService.getRoomBySpace(space);

    boolean chatAuthorizedForSpace = matrixService.isChatAuthorizedByAdministration(space)
        && (spaceRoom != null || matrixService.isChatAuthorizedForSpaceTemplate(space));

    return """
        {
          "chatAuthorizedForSpace": %s
        }
        """.formatted(chatAuthorizedForSpace);
  }

  private String checkAndParseUserFromToken(String token) {
    byte[] secret = PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes();
    Jws<Claims> jws = Jwts.parser().setSigningKey(Keys.hmacShaKeyFor(secret)).build().parseClaimsJws(token);
    String usernameOnMatrix = String.valueOf(jws.getBody().getSubject());
    if (StringUtils.isNotBlank(usernameOnMatrix)) {
      String fullUserName = matrixService.getUserFullMatrixID(usernameOnMatrix);
      String userName = matrixService.findUserByMatrixId(fullUserName);
      if (identityManager.identityExisted(OrganizationIdentityProvider.NAME, userName)) {
        return userName;
      }
    }
    return null;
  }

  private RoomEntity buildRoomEntityFromRoom(Room room, String currentUserName) {
    RoomEntity roomEntity = new RoomEntity();
    String roomId = room.getRoomId();
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (!roomId.contains(serverName)) {
      roomId = roomId + ":" + serverName;
    }
    roomEntity.setId(roomId);
    roomEntity.setStatus(room.getStatus());
    if (room.getSpaceId() != null) {
      roomEntity.setSpaceId(room.getSpaceId());
      roomEntity.setDirectChat(false);
      roomEntity.setSpaceId(room.getSpaceId());
    } else if (StringUtils.isNotBlank(room.getFirstParticipant()) && StringUtils.isNotBlank(room.getSecondParticipant())) {
      roomEntity.setDirectChat(true);
      if (room.getFirstParticipant().equals(currentUserName)) {
        roomEntity.setDmMemberId(room.getSecondParticipant());
      } else {
        roomEntity.setDmMemberId(room.getFirstParticipant());
      }
    }
    // Update room entity with data from Meeds server
    return updateRoomEntity(roomEntity, currentUserName);
  }

  /**
   * Process the Matrix rooms and adds the missing information of users and spaces
   *
   * @param roomList the room list received from Matrix par sync API
   * @param currentUserName the current user
   * @return the roo List after processing
   */
  public RoomList processRooms(RoomList roomList, String currentUserName) {
    if (roomList == null || roomList.getRooms() == null) {
      throw new IllegalArgumentException("The room list Object is empty");
    }
    if (StringUtils.isBlank(currentUserName)) {
      throw new IllegalArgumentException("The username of the current user is mandatory");
    }

    List<String> spaceIds = spaceService.getMemberSpacesIds(currentUserName, 0, -1);

    List<RoomEntity> processedRooms = new ArrayList<>();
    for (int index = 0; index < roomList.getRooms().size(); index++) {
      RoomEntity room = roomList.getRooms().get(index);
      room = updateRoomEntity(room, currentUserName);
      // check missing rooms
      if (room == null) {
        continue;
      }
      if (room.isMuted()) {
        roomList.setTotalUnreadMessages(Math.max(0, roomList.getTotalUnreadMessages() - room.getUnreadMessages()));
      }
      if (!room.isDirectChat()) {
        spaceIds.remove(String.valueOf(room.getSpaceId()));
      }
      ChatSettingsEntity chatSettings = matrixService.loadChatSettings();
      if (RoomStatus.ENABLED.name().equals(room.getStatus())
          && (chatSettings == null || (room.isDirectChat() && chatSettings.isPrivateRoomsEnabled()) || (!room.isDirectChat()
              && chatSettings.isSpaceRoomsEnabled() && matrixService.isChatAuthorizedForSpace(room.getSpaceId())))) {
        processedRooms.add(room);
      } else {
        // Substract the unread messages from disabled chat rooms
        roomList.setTotalUnreadMessages(Math.max(0, roomList.getTotalUnreadMessages() - room.getUnreadMessages()));
      }

    }
    if (!spaceIds.isEmpty()) {
      for (String spaceId : spaceIds) {
        Room missingRoom = matrixService.getRoomBySpaceId(Long.valueOf(spaceId));
        if (missingRoom != null) {
          RoomEntity missingRoomEntity = buildRoomEntityFromRoom(missingRoom, currentUserName);
          if (missingRoomEntity != null) {
            processedRooms.addFirst(missingRoomEntity);
          }
        }
      }
    }

    roomList.setRooms(processedRooms);
    return roomList;
  }

  private RoomEntity updateRoomEntity(RoomEntity room, String currentUserName) {
    String roomId = room.getId();
    // Update room information
    if (room.getId().contains(":")) {
      roomId = room.getId().substring(0, room.getId().indexOf(":"));// remove server part
    }
    Room matrixRoom = matrixService.getById(roomId, true);
    if (matrixRoom != null) {
      if (matrixRoom.getSpaceId() != null) {
        Space space = spaceService.getSpaceById(matrixRoom.getSpaceId());
        UserSettingService userSettingService = CommonsUtils.getService(UserSettingService.class);
        UserSetting userSetting = userSettingService.get(currentUserName);
        if (space != null) {
          room.setName(space.getDisplayName());
          room.setAvatarUrl(space.getAvatarUrl());
          room.setSpaceId(matrixRoom.getSpaceId());
          room.setPrettyName(space.getPrettyName());
          room.setMuted(userSetting != null && userSetting.isSpaceMuted(Long.parseLong(space.getId())));
          room.setDirectChat(false);
          ArrayList<Member> members = new ArrayList<>();
          Arrays.stream(space.getMembers()).forEach(member -> members.add(buildRoomMember(member)));
          room.setMembers(members);
        }
      } else if (StringUtils.isNotBlank(matrixRoom.getFirstParticipant())
          && StringUtils.isNotBlank(matrixRoom.getSecondParticipant())) {
        Identity identity = null;
        if (matrixRoom.getFirstParticipant().equals(currentUserName)) {
          identity = identityManager.getOrCreateUserIdentity(matrixRoom.getSecondParticipant());
        } else if (matrixRoom.getSecondParticipant().equals(currentUserName)) {
          identity = identityManager.getOrCreateUserIdentity(matrixRoom.getFirstParticipant());
        }
        if (identity != null) {
          room.setName(identity.getProfile().getFullName());
          room.setAvatarUrl(identity.getProfile().getAvatarUrl());
          room.setUserId(identity.getRemoteId());
          room.setIdentityId(identity.getId());
          room.setDmMemberId(identity.getRemoteId());
          room.setMuted(chatNotificationService.isPrivateRoomMutedForUser(currentUserName, room.getId()));
          room.setExternal(identity.isExternal());
          room.setEnabledUser(identity.isEnable());
          room.setDeletedUser(identity.isDeleted());
          room.setMatrixId((String) identity.getProfile().getProperty(USER_MATRIX_ID));
        }
      }
      room.setStatus(matrixRoom.getStatus());
      // Add update Date
      if (room.getUpdated() == 0) {
        room.setUpdated(System.currentTimeMillis());
      }
      return room;
    } else {
      return null;
    }
  }

  private Member buildRoomMember(String userName) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity == null || identity.getProfile() == null) {
      return null;
    }
    Member member = new Member();
    member.setId(identity.getId());
    member.setUserId(userName);
    member.setMatrixId((String) identity.getProfile().getProperty(USER_MATRIX_ID));
    return member;
  }

}
