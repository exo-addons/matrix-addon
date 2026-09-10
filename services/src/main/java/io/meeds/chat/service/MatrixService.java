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
package io.meeds.chat.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.ChatConversation;
import io.meeds.chat.model.ChatMessage;
import io.meeds.chat.model.ChatSearchResult;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.service.model.ChatSettings;
import io.meeds.chat.service.model.MediaInfo;
import io.meeds.chat.service.model.SpaceTemplateSetting;
import io.meeds.chat.service.utils.AsyncTaskUtils;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.utils.MatrixUnauthorizedException;
import io.meeds.chat.storage.MatrixRoomStorage;
import io.meeds.portal.navigation.model.TopbarApplication;
import io.meeds.portal.navigation.service.NavigationConfigurationService;
import io.meeds.social.space.template.model.SpaceTemplate;
import io.meeds.social.space.template.model.SpaceTemplateFilter;
import io.meeds.social.space.template.service.SpaceTemplateService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.notification.channel.MailChannel;
import org.exoplatform.commons.notification.channel.WebChannel;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.Query;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.apache.commons.lang3.StringUtils.isNumeric;

@Service
public class MatrixService {

  private static final Log               LOG                       = ExoLogger.getLogger(MatrixService.class);

  public static final String             CHAT_APPLICATION_ID       = "chat";

  @Autowired
  private MatrixRoomStorage              matrixRoomStorage;

  @Autowired
  private IdentityManager                identityManager;

  @Autowired
  private IdentityStorage                identityStorage;

  @Autowired
  private OrganizationService            organizationService;

  @Autowired
  private SpaceService                   spaceService;

  @Autowired
  private MatrixHttpClient               matrixHttpClient;

  @Autowired
  private SpaceTemplateService           spaceTemplateService;

  /**
   * -- GETTER -- Checks if the Matrix service is available
   */
  @Getter
  private volatile boolean               serviceAvailable;

  private volatile String                matrixAccessToken;

  private SettingService                 settingService;

  private final ExoCache<String, String> userMatrixIdsCache;

  private final ExoCache<String, String> userAccessTokensCache;

  public static final String             USER_MATRIX_ID_CACHE_NAME = "chat.UserMatrixId";

  @Getter
  @Value("#{'${meeds.chat.authorized.space.templates:project,circle}'.split(',')}")
  public List<String>                    defaultAuthorizedSpaceTemplates;
  public static final String             USER_ACCESS_TOKEN_CACHE_NAME = "chat.UserAccessToken";

  public MatrixService(MatrixRoomStorage matrixRoomStorage,
                       IdentityManager identityManager,
                       IdentityStorage identityStorage,
                       OrganizationService organizationService,
                       MatrixHttpClient matrixHttpClient,
                       CacheService cacheService,
                       SettingService settingService,
                       SpaceTemplateService spaceTemplateService) {
    this.matrixRoomStorage = matrixRoomStorage;
    this.identityManager = identityManager;
    this.identityStorage = identityStorage;
    this.organizationService = organizationService;
    this.matrixHttpClient = matrixHttpClient;
    this.userMatrixIdsCache = cacheService.getCacheInstance(USER_MATRIX_ID_CACHE_NAME);
    this.settingService = settingService;
    this.spaceTemplateService = spaceTemplateService;
    this.userAccessTokensCache = cacheService.getCacheInstance(USER_ACCESS_TOKEN_CACHE_NAME);
  }

  /**
   * Kicks off the connection to the Matrix server on a background thread so
   * that the (possibly slow, possibly retried) handshake never blocks the WAR
   * deployment / server startup. See {@link AsyncTaskUtils#runAsync} for the
   * escape hatch used by tests to run it synchronously instead.
   */
  @PostConstruct
  public void init() {
    AsyncTaskUtils.runAsync("Matrix connection init Thread", this::connectToMatrixServer);
  }

  private void connectToMatrixServer() {
    int maxAttempts = getConnectionRetryAttempts();
    long retryDelayMs = getConnectionRetryDelay() * 1000L;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        this.getMatrixAccessToken();

        String userFullMatrixID = getUserFullMatrixID(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
        this.overrideAdminRateLimit(userFullMatrixID);
        String displayName = System.getProperty(MATRIX_ADMIN_DISPLAY_NAME, "Chat Bot");
        if (StringUtils.isNotBlank(displayName)) {
          this.updateUserDisplayName(userFullMatrixID, displayName);
        }
        this.serviceAvailable = true;
        LOG.info("Matrix service initialized successfully (attempt {}/{})", attempt, maxAttempts);
        return;
      } catch (IllegalArgumentException e) {
        // Non-transient configuration error (e.g. missing server URL or admin
        // username): waiting/retrying will never make it succeed, so fail fast.
        LOG.error("Could not initialize Matrix service because of an invalid configuration, the service is unavailable: {}",
                  e.getMessage());
        this.serviceAvailable = false;
        return;
      } catch (Exception e) {
        this.serviceAvailable = false;
        if (attempt < maxAttempts) {
          LOG.warn("Matrix service is not available yet (attempt {}/{}), retrying in {}s.",
                   attempt,
                   maxAttempts,
                   retryDelayMs / 1000);
          try {
            Thread.sleep(retryDelayMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for the Matrix service to become available, aborting initialization");
            return;
          }
        } else {
          LOG.error("Could not initialize Matrix service after {} attempt(s), the service is unavailable", maxAttempts, e);
        }
      }
    }
  }

  private int getConnectionRetryAttempts() {
    int attempts = getIntProperty(MATRIX_CONNECTION_RETRY_ATTEMPTS, DEFAULT_CONNECTION_RETRY_ATTEMPTS);
    return attempts < 1 ? 1 : attempts;
  }

  private int getConnectionRetryDelay() {
    int delay = getIntProperty(MATRIX_CONNECTION_RETRY_DELAY, DEFAULT_CONNECTION_RETRY_DELAY);
    return delay < 0 ? 0 : delay;
  }

  private int getIntProperty(String key, int defaultValue) {
    String value = PropertyManager.getProperty(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      LOG.warn("Invalid value '{}' for property {}, falling back to default {}", value, key, defaultValue);
      return defaultValue;
    }
  }

  /**
   * Checks if the Chat feature is enabled and that the Matrix service is
   * available
   * 
   * @return the status fo the chat service and feature
   */
  public boolean isServiceEnabled() {
    return this.serviceAvailable && this.isChatFeatureEnabled();
  }

  /**
   * Checks if the chat feature if enabled or not
   * 
   * @return boolean : chat status
   */
  private boolean isChatFeatureEnabled() {
    ChatSettingsEntity settings = loadChatSettings();
    return settings == null || settings.isChatEnabled();
  }

  /**
   * Convert the user id into the full Matrix ID format
   * 
   * @param userName the username
   * @return formatted username
   */
  public String getUserFullMatrixID(String userName) {
    if (StringUtils.isNotBlank(userName) && userName.startsWith("@") && userName.indexOf(":") > 0) { // NOSONAR
      return userName;
    }
    return "@" + userName + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
  }

  private String getMatrixAccessToken() throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(this.matrixAccessToken)) {
      try {
        String jwtAccessToken = this.getJWTSessionToken(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
        this.matrixAccessToken = matrixHttpClient.getAccessToken(jwtAccessToken);
      } catch (JsonException | IOException e) {
        LOG.error("Could not get Matrix Access token for the administrator account !", e);
        throw e;
      }
    }
    return this.matrixAccessToken;
  }

  /**
   * Retrieves an access token for a user using a JWT token
   * 
   * @param jwtToken the Json Web Token
   * @return String the access token
   */
  public String getAccessToken(String jwtToken) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.getAccessToken(jwtToken);
  }

  /**
   * Invalidate a specific access token
   * 
   * @param accessToken the access token to be invalidated
   * @return true if success
   */
  public boolean invalidateAccessToken(String accessToken) {
    try {
      return matrixHttpClient.invalidateAccessToken(accessToken);
    } catch (IOException e) {
      LOG.error("Could not invalidate an access token !", e);
      return false;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.error("Could not invalidate an access token !", interruptedException);
      return false;
    }
  }

  public void updateUserDisplayName(String matrixFullID, String newDisplayName) {
    try {
      String currentUserDisplayName = matrixHttpClient.getUserDisplayName(matrixFullID, getMatrixAccessToken());
      if (StringUtils.isNotBlank(currentUserDisplayName) && !currentUserDisplayName.equals(newDisplayName)) {
        matrixHttpClient.updateUserDisplayName(matrixFullID, newDisplayName, getMatrixAccessToken());
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.error("Couldn't update the display name of the user {}", matrixFullID, ie);
    } catch (Exception e) {
      LOG.error("Couldn't update the display name of the user {}", matrixFullID, e);
    }
  }

  /**
   * Returns the ID of the room linked to a space
   * 
   * @param space the space
   * @return the roomId linked to the space
   */
  public Room getRoomBySpace(Space space) {
    return getRoomBySpaceId(Long.valueOf(space.getId()));
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param space the space
   * @return the roomId linked to the space
   */
  public Room getRoomBySpace(Space space, boolean includeDisabled) {
    return getRoomBySpaceId(Long.valueOf(space.getId()), includeDisabled);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(Long spaceId) {
    return this.getRoomBySpaceId(spaceId, false);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(Long spaceId, boolean includeDisabled) {
    return matrixRoomStorage.getMatrixRoomBySpaceId(spaceId, includeDisabled);
  }

  /**
   * Get a room by its technical ID
   * 
   * @param roomId the room technical ID
   * @return Room
   */
  public Room getById(String roomId) {
    return this.getById(roomId, false);
  }

  /**
   * Get a room by its technical ID
   *
   * @param roomId the room technical ID
   * @param includeDisabled return room even if it is disabled
   * @return Room
   */
  public Room getById(String roomId, boolean includeDisabled) {
    if (StringUtils.isNotBlank(roomId)) {
      roomId = extractRoomId(roomId);
      return matrixRoomStorage.getById(roomId, includeDisabled);
    }
    return null;
  }

  /**
   * records the matrix ID of the room linked to the space
   *
   * @param space the Space
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public Room linkSpaceToMatrixRoom(Space space, String roomId) {
    return linkSpaceToMatrixRoom(Long.parseLong(space.getId()), roomId, RoomStatus.ENABLED);
  }

  /**
   * records the matrix ID of the room linked to the space
   *
   * @param spaceId the Space identifier
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public Room linkSpaceToMatrixRoom(long spaceId, String roomId, RoomStatus roomStatus) {
    return matrixRoomStorage.saveRoomForSpace(spaceId, roomId, roomStatus);
  }

  /**
   * Creates a room on Matrix for the space
   * 
   * @param space the space
   * @return String representing the room id
   * @throws Exception when creating the room fails on Matrix
   */
  public String createRoomForSpaceOnMatrix(Space space) throws Exception {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    return matrixHttpClient.createRoom(teamDisplayName, description, getMatrixAccessToken());
  }

  /**
   * Get the matrix ID of a defined user
   * 
   * @param userName of the user
   * @return the matrix ID
   */
  public String getMatrixIdForUser(String userName) {
    Identity newMember = identityManager.getOrCreateUserIdentity(userName);
    Profile newMemberProfile = newMember.getProfile();
    if (StringUtils.isNotBlank((String) newMemberProfile.getProperty(USER_MATRIX_ID))) {
      return newMemberProfile.getProperty(USER_MATRIX_ID).toString();
    }
    return null;
  }

  /**
   * Returns the JWT for user authentication
   * 
   * @param userNameOnMatrix the username of the current user
   * @return String
   */
  public String getJWTSessionToken(String userNameOnMatrix) {
    Date expirtaionDate = Date.from(Instant.now().plusSeconds(7 * 24 * 60 * 60L)); // adds one week to the current instant
    userNameOnMatrix = userNameOnMatrix.replaceAll("[^a-zA-Z0-9=_\\-\\.\\/+]+", "-");
    return Jwts.builder()
               .setSubject(userNameOnMatrix)
               .signWith(Keys.hmacShaKeyFor(PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes()))
               .setExpiration(expirtaionDate)
               .compact();

  }

  /**
   * Saves a new user on Matrix
   * 
   * @param user the user identity
   * @param isNew if the user has been just created
   * @return the matrix user ID
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String saveUserAccount(Identity user, boolean isNew) throws JsonException, IOException, InterruptedException {
    return saveUserAccount(user, isNew, false, true);
  }

  /**
   * Saves a new user on Matrix
   * 
   * @param user the user to create on Matrix
   * @param isNew boolean if the user is new, then true
   * @return String the matrix user ID
   */
  public String saveUserAccount(Identity user,
                                boolean isNew,
                                boolean isEnableUserOperation,
                                boolean isUserEnabled) throws JsonException, IOException, InterruptedException {

    String matrixUserId = user.getRemoteId();
    if (isNumeric(user.getRemoteId())) {
      String prefix =
                    StringUtils.isNotBlank(PropertyManager.getProperty(MATRIX_USERNAME_PREFIX)) ? PropertyManager.getProperty(MATRIX_USERNAME_PREFIX)
                                                                                                : "u";
      matrixUserId = prefix + user.getRemoteId();
    }
    matrixUserId = cleanMatrixUsername(matrixUserId);
    String matrixId = matrixHttpClient.saveUserAccount(user,
                                                       matrixUserId,
                                                       isNew,
                                                       this.getMatrixAccessToken(),
                                                       isEnableUserOperation,
                                                       isUserEnabled);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getRemoteId());
    Profile userProfile = userIdentity.getProfile();
    if (StringUtils.isNotBlank(matrixId) && (userProfile.getProperty(USER_MATRIX_ID) == null
        || StringUtils.isBlank(userProfile.getProperty(USER_MATRIX_ID).toString()))) {
      userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
      identityManager.updateProfile(userProfile);
    }
    return matrixId;
  }

  public String uploadFileOnMatrix(String fileName, String mimeType, byte[] fileBytes) throws JsonException,
                                                                                       IOException,
                                                                                       InterruptedException {
    return matrixHttpClient.uploadFile(fileName, mimeType, fileBytes, this.getMatrixAccessToken());
  }

  public void updateUserAvatar(Profile profile, String userMatrixID) throws JsonException, IOException, InterruptedException {
    try {
      if (StringUtils.isNotBlank(userMatrixID)) {
        FileItem avatarFileItem = identityStorage.getAvatarFile(profile.getIdentity());
        String mimeType = "image/jpg";
        if (avatarFileItem != null && avatarFileItem.getFileInfo() != null
            && !"DEFAULT_AVATAR".equals(avatarFileItem.getFileInfo().getName())) {
          if (!"application/octet-stream".equals(avatarFileItem.getFileInfo().getMimetype())) {
            mimeType = avatarFileItem.getFileInfo().getMimetype();
          }
          String userAvatarUrl = this.uploadFileOnMatrix("avatar-of-" + profile.getIdentity().getRemoteId() + ".jpg",
                                                         mimeType,
                                                         avatarFileItem.getAsByte());
          if (StringUtils.isNotBlank(userMatrixID) && StringUtils.isNotBlank(userAvatarUrl)) {
            matrixHttpClient.updateUserAvatar(userMatrixID, userAvatarUrl, this.getMatrixAccessToken());
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Could not save the avatar of {} on Matrix", profile.getFullName(), e);
    }
  }

  public void disableAccount(String matrixUsername) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.disableAccount(matrixUsername, false, this.getMatrixAccessToken());
  }

  public boolean updateRoomDescription(String roomId,
                                       String description) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.updateRoomDescription(roomId, description, this.getMatrixAccessToken());
  }

  public void updateRoomAvatar(Space space, String roomId) throws Exception {
    String mimeType = "";
    String avatarURL;
    String fileExtension = "";
    String fileName = "";
    try {
      Identity identity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());
      FileItem spaceAvatarFileItem = identityManager.getAvatarFile(identity);
      byte[] imageBytes = new byte[0];
      if (space.getAvatarAttachment() != null && space.getAvatarAttachment().getImageBytes() != null) {
        imageBytes = space.getAvatarAttachment().getImageBytes();
        mimeType = space.getAvatarAttachment().getMimeType();
        fileName = space.getAvatarAttachment().getFileName();
        fileExtension = StringUtils.isNotBlank(fileName) && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf("."))
                                                                                   : ".jpg";
      } else if ((spaceAvatarFileItem != null && spaceAvatarFileItem.getAsByte() != null)) {
        imageBytes = spaceAvatarFileItem.getAsByte();
        mimeType = spaceAvatarFileItem.getFileInfo().getMimetype();
        fileName = spaceAvatarFileItem.getFileInfo().getName();
        fileExtension = StringUtils.isNotBlank(fileName) && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf("."))
                                                                                   : ".jpg";
      }
      if ("application/octet-stream".equals(mimeType)) {
        mimeType = "image/jpg";
      }
      fileName = "avatar-space-%s%s".formatted(space.getPrettyName(), fileExtension);
      if (StringUtils.isNotBlank(roomId) && imageBytes != null) {
        avatarURL = this.uploadFileOnMatrix(fileName, mimeType, imageBytes);
        matrixHttpClient.updateRoomAvatar(roomId, avatarURL, this.getMatrixAccessToken());
      }
    } catch (Exception e) {
      throw new Exception("Could not save the avatar of the space %s".formatted(space.getDisplayName()), e);
    }
  }

  public MatrixRoomPermissions getRoomSettings(String roomId) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.getRoomSettings(roomId, this.getMatrixAccessToken());
  }

  public boolean updateRoomSettings(String roomId, MatrixRoomPermissions matrixRoomPermissions) throws JsonException,
                                                                                                IOException,
                                                                                                InterruptedException {
    return matrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, this.getMatrixAccessToken()) != null;
  }

  public void kickUserFromRoom(String roomId, String matrixIdOfUser, String message) throws JsonException,
                                                                                     IOException,
                                                                                     InterruptedException {
    matrixHttpClient.kickUserFromRoom(roomId, matrixIdOfUser, message, this.getMatrixAccessToken());
  }

  public void joinUserToRoom(String roomId, String matrixIdOfUser) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.joinUserToRoom(roomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  /**
   * Checks if the user is a member of the room
   *
   * @param roomId the room ID
   * @param matrixIdOfUser the ID of the user on Matrix
   * @return true if the user is a member of the room, false otherwise
   */
  public boolean isUserMemberOfRoom(String roomId,
                                    String matrixIdOfUser) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.isUserMemberOfRoom(roomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  public void renameRoom(String roomId, String spaceDisplayName) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.renameRoom(roomId, spaceDisplayName, this.getMatrixAccessToken());
  }

  public void makeUserAdminInRoom(String matrixRoomId,
                                  String matrixIdOfUser) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.makeUserAdminInRoom(matrixRoomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  /**
   * This function do : - Create a room on Matrix - Links the room to the space on
   * Meeds - Update room permissions
   * 
   * @param space The space
   * @return The room ID
   * @throws Exception
   */
  public String createRoom(Space space) throws Exception {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    String matrixRoomId = matrixHttpClient.createRoom(teamDisplayName, description, this.getMatrixAccessToken());
    if (StringUtils.isNotBlank(matrixRoomId)) {
      // link the room on Meeds server
      this.linkSpaceToMatrixRoom(space, matrixRoomId);
      // Disable inviting user but for Moderators
      MatrixRoomPermissions matrixRoomPermissions = this.getRoomSettings(matrixRoomId);
      matrixRoomPermissions.setInvite(ADMIN_ROLE);
      this.updateRoomSettings(matrixRoomId, matrixRoomPermissions);
    }
    return matrixRoomId;
  }

  public Room getDirectMessagingRoom(String firstParticipant, String secondParticipant) {
    return matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
  }

  /**
   * Delete a Matrix room
   *
   * @param roomId the room identifier
   */
  public void deleteRoom(String roomId) throws JsonException, IOException, InterruptedException {
    boolean success = matrixHttpClient.deleteRoom(roomId, getMatrixAccessToken());
    if (success) {
      matrixRoomStorage.removeMatrixRoom(roomId);
    }
  }

  /**
   * Creates a one to one room
   * 
   * @param directMessagingRoom the Object representation of a one to one room
   * @return the created Room
   * @throws ObjectAlreadyExistsException in case a room between the same users is
   *           already created
   */
  public Room createDirectMessagingRoom(Room directMessagingRoom) throws ObjectAlreadyExistsException {
    String firstParticipant = directMessagingRoom.getFirstParticipant();
    String secondParticipant = directMessagingRoom.getSecondParticipant();
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new IllegalArgumentException("The ids of the room participants should not be null");
    }
    if (identityManager.getOrCreateUserIdentity(directMessagingRoom.getFirstParticipant()) == null
        || identityManager.getOrCreateUserIdentity(directMessagingRoom.getSecondParticipant()) == null) {
      throw new IllegalArgumentException("The ids of the room participants should be valid user identity ids");
    }
    Room matrixRoom = matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (matrixRoom == null) {
      return matrixRoomStorage.saveDirectMessagingRoom(directMessagingRoom.getFirstParticipant(),
                                                       directMessagingRoom.getSecondParticipant(),
                                                       directMessagingRoom.getRoomId());
    } else {
      throw new ObjectAlreadyExistsException("A direct messaging room is already created for the users %s and %s".formatted(firstParticipant,
                                                                                                                            secondParticipant));
    }
  }

  /**
   * Loads all the one to one rooms of a defined user
   * 
   * @param user the username of the user
   * @return List of Room
   */
  public List<Room> getMatrixDMRoomsOfUser(String user) {
    return matrixRoomStorage.getMatrixDMRoomsOfUser(user);
  }

  /**
   * Checks if the user is a member of a group defined by its name
   * 
   * @param userName the user name
   * @param groupId the user group ID
   * @return true if the user is a member of the group
   * @throws Exception
   */
  private boolean isUserMemberOfGroup(String userName, String groupId) throws Exception {
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      Collection<Membership> userMemberships = this.organizationService.getMembershipHandler()
                                                                       .findMembershipsByUserAndGroup(userName, groupId);
      return !userMemberships.isEmpty();
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Checks if the user is a member of a group defined by its name
   * 
   * @param userName the userName
   * @param groups the list of groups
   * @return true if the user is a member of the group
   * @throws Exception error finding the user
   */
  public boolean isUserMemberOfGroups(String userName, String... groups) throws Exception {
    for (String group : groups) {
      if (this.isUserMemberOfGroup(userName, group)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a list of all space rooms
   * 
   * @return List of Space rooms
   */
  public List<Room> getSpaceRooms() {
    return matrixRoomStorage.getSpaceRooms();
  }

  /**
   * Extracts the user ID from the full User Id on Matrix
   * 
   * @param fullMatrixUserId the full Matrix user Id
   * @return the user Identifier
   */
  public String extractUserId(String fullMatrixUserId) {
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (fullMatrixUserId.startsWith("@") && fullMatrixUserId.endsWith(serverName)) {
      return fullMatrixUserId.substring(1, fullMatrixUserId.indexOf(":"));
    }
    return fullMatrixUserId;
  }

  /**
   * Extracts the room ID from the full room Id on Matrix
   *
   * @param fullMatrixUserId the full Matrix user Id
   * @return the user Identifier
   */
  public String extractRoomId(String fullMatrixUserId) {
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (fullMatrixUserId.startsWith("!") && fullMatrixUserId.endsWith(serverName)) {
      return fullMatrixUserId.substring(0, fullMatrixUserId.indexOf(":"));
    }
    return fullMatrixUserId;
  }

  /**
   * update the user presence status on Matrix
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param presence the presence value: online , unavailable, offline
   * @param statusMessage a personalized status message
   */
  public String updateUserPresence(String userIdOnMatrix, String presence, String statusMessage) {
    try {
      return matrixHttpClient.setUserPresence(userIdOnMatrix, presence, statusMessage, getMatrixAccessToken());
    } catch (Exception e) {
      LOG.error("Could not update the presence onf the user {} on Matrix", userIdOnMatrix, e);
    }
    return null;
  }

  /**
   * Checks if the user able to access the room
   * 
   * @param room the room
   * @param userName the username of the user
   * @return true if he has access, false otherwise
   */
  public boolean canAccess(Room room, String userName) {
    if (room.getSpaceId() == null) {
      return userName.equals(room.getFirstParticipant()) || userName.equals(room.getSecondParticipant());
    } else {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      return spaceService.canViewSpace(space, userName);
    }
  }

  /**
   * Enable the chat for the space
   *
   * @param space the space where the chat will be disabled/enabled
   * @param enable true to enable the room, false to disable it
   * @return Room the updated room
   */
  public Room enableSpaceChat(Space space, boolean enable) throws ObjectNotFoundException {
    if (space == null) {
      throw new IllegalArgumentException("The space should not be null");
    }
    Room spaceRoom = this.getRoomBySpace(space, true);
    if (spaceRoom == null) {
      throw new ObjectNotFoundException("Could not find a chat room for the space " + space.getDisplayName());
    }
    String matrixAdminUsername = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);

    RoomStatus currentRoomStatus = RoomStatus.valueOf(spaceRoom.getStatus());
    try {
      this.changeRoomStatus(spaceRoom.getRoomId(), enable ? RoomStatus.ENABLE_IN_PROGRESS : RoomStatus.DISABLED_IN_PROGRESS);
      this.changeChatRoomReadability(spaceRoom.getRoomId(), !enable);
      if (enable) {
        for (String member : space.getMembers()) {
          String matrixIdOfMember = getMatrixIdForUser(member);
          if (!matrixAdminUsername.equals(matrixIdOfMember)) {
            joinUserToRoom(spaceRoom.getRoomId(), matrixIdOfMember);
          }
        }
      }
      spaceRoom = this.changeRoomStatus(spaceRoom.getRoomId(), enable ? RoomStatus.ENABLED : RoomStatus.DISABLED);
    } catch (Exception e) {
      // revert room to the original status
      spaceRoom = this.changeRoomStatus(spaceRoom.getRoomId(), currentRoomStatus);
      this.changeChatRoomReadability(spaceRoom.getRoomId(), enable);
      LOG.error("An error occurred when enabling/disabling the room {}", spaceRoom.getRoomId(), e);
    }
    return spaceRoom;
  }

  /**
   * Enable or disable a room
   * 
   * @param roomId the ID of the Chat room
   * @param status the status to set: ENABLED, DISABLED, ENABLE_IN_PROGRESS,
   *          DISABLED_IN_PROGRESS
   * @return Room the updated room
   */
  private Room changeRoomStatus(String roomId, RoomStatus status) {
    return matrixRoomStorage.setRoomEnabled(roomId, status);
  }

  /**
   * Overrides the rate limits for the current administrator to unlimited
   * 
   * @param adminUserId the admin username on Matrix
   */
  public void overrideAdminRateLimit(String adminUserId) {
    try {
      String currentRateLimits = matrixHttpClient.getOverriddenRateLimitForUser(adminUserId, getMatrixAccessToken());
      JsonValue currentLimits =
                              StringUtils.isBlank(currentRateLimits) ? null
                                                                     : new JsonGeneratorImpl().createJsonObjectFromString(currentRateLimits);
      JsonValue messagePerSecond = currentLimits == null ? null : currentLimits.getElement("messages_per_second");
      JsonValue burstCount = currentLimits == null ? null : currentLimits.getElement("burst_count");
      if (messagePerSecond == null || burstCount == null || messagePerSecond.getIntValue() > 0 || burstCount.getIntValue() > 0) {
        // set the messages per second to zero -> unlimited
        // set the burst count to 0 : No burst count
        matrixHttpClient.overrideRateLimitForUser(adminUserId, 0, 0, getMatrixAccessToken());
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.error("Could not update the rate limits for the Matrix admin user", e);
    }
  }

  /**
   * Retrieves the message related to the event ID
   * 
   * @param eventId the event ID
   * @param roomId the room ID
   * @return MatrixMessage the received message
   */
  public MatrixMessage getRoomEvent(String eventId, String roomId, String token) {
    try {
      String accessToken = StringUtils.isNotBlank(token) ? token : getMatrixAccessToken();
      MatrixMessage message = matrixHttpClient.getEventById(eventId, roomId, accessToken);
      if (message != null && "m.room.message".equals(message.getType())) {
        return message;
      }
    } catch (IOException | InterruptedException | JsonException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.error("Could not retrieve the Room event {}", eventId, e);
    }
    return null;
  }

  /**
   * Retrieves the message related to the event ID
   *
   * @param eventId the event ID
   * @param roomId the room ID
   * @return MatrixMessage the received message
   */
  public MatrixMessage getRoomEvent(String eventId, String roomId) {
    try {
      return getRoomEvent(eventId, roomId, getMatrixAccessToken());
    } catch (IOException | JsonException e) {
      LOG.error("Could not retrieve the Room event {}", eventId, e);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.error("Could not retrieve the Room event {}", eventId, interruptedException);
    }
    return null;
  }

  /**
   * Finds the identity of a user based on its Matrix ID and a space where he is a
   * member
   * 
   * @param matrixId the Matrix identifier of the user
   * @param space the space
   * @return the identity of the space member
   */
  public Identity findSpaceMemberByMatrixId(String matrixId, Space space) {
    matrixId = extractUserId(matrixId);
    if (userMatrixIdsCache.get(matrixId) != null) {
      return identityManager.getOrCreateUserIdentity(userMatrixIdsCache.get(matrixId));
    }
    for (String member : space.getMembers()) {
      Identity memberIdentity = identityManager.getOrCreateUserIdentity(member);
      if (memberIdentity != null && memberIdentity.getProfile().getProperties().containsKey(USER_MATRIX_ID)
          && memberIdentity.getProfile().getProperties().get(USER_MATRIX_ID).equals(matrixId)) {
        userMatrixIdsCache.put(matrixId, member);
        return memberIdentity;
      }
    }
    return null;
  }

  /**
   * find the user on Meeds based on his id on Matrix
   * 
   * @param userMatrixId the ID of the user on Matrix
   * @return the user Identity
   */
  public String findUserByMatrixId(String userMatrixId) {
    if (StringUtils.isBlank(userMatrixId)) {
      throw new IllegalArgumentException("the user Matrix ID is required");
    }
    String simplifiedMatrixId = extractUserId(userMatrixId);
    if (userMatrixIdsCache.get(simplifiedMatrixId) != null) {
      return userMatrixIdsCache.get(simplifiedMatrixId);
    }
    try {
      String userAsJson = matrixHttpClient.getUser(userMatrixId, getMatrixAccessToken());
      Iterator<JsonValue> jsonIterator = new JsonGeneratorImpl().createJsonObjectFromString(userAsJson)
                                                                .getElement("threepids")
                                                                .getElements();
      while (jsonIterator.hasNext()) {
        JsonValue threePids = jsonIterator.next();
        if ("email".equals(threePids.getElement("medium").getStringValue())) {
          User user = getUserByEmail(threePids.getElement("address").getStringValue());
          if (user != null) {
            userMatrixIdsCache.put(userMatrixId, user.getUserName());
            return user.getUserName();
          }
        }
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      return userMatrixId;
    } catch (Exception e) {
      return userMatrixId;
    }
    return userMatrixId;
  }

  /**
   * Find a user in Meeds DB using his email
   * 
   * @param email The user email
   * @return the User if he is found, otherwise null
   */
  private User getUserByEmail(String email) {
    if (email == null) {
      return null;
    }
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      Query query = new Query();
      query.setEmail(email);
      User[] users = organizationService.getUserHandler().findUsersByQuery(query).load(0, 10);
      if (users.length > 0) {
        return users[0];
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Cleans the username to be compatible with the usernames on Matrix server
   *
   * @param userName the user identifier
   * @return String the cleaned username
   */
  public String cleanMatrixUsername(String userName) {
    return userName.replaceAll("[^a-zA-Z0-9=_\\-\\.\\/+]+", "-").toLowerCase();
  }

  /**
   * Returns the restricted group of users if it is configured
   * 
   * @return Array of group names
   */
  public String[] getRestrictedGroups() {
    String groupNames = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    if (StringUtils.isBlank(groupNames)) {
      return new String[] {};
    }
    return Arrays.stream(groupNames.split(",")).map(String::trim).toArray(String[]::new);
  }

  /**
   * Returns synapse media info by its ID
   * 
   * @param mediaId synapse media id
   * @return {@link MediaInfo} wrapped in {@link Optional}, or
   *         {@link Optional#empty()} if mediaId is null or blank
   * @throws JsonException if there is an error parsing the JSON response from the
   *           server
   * @throws IOException if a network or I/O error occurs during the request
   * @throws InterruptedException if the thread executing the request is
   *           interrupted
   */
  public Optional<MediaInfo> getMediaInfo(String mediaId) throws JsonException, IOException, InterruptedException {
    if (mediaId == null || mediaId.isBlank()) {
      return Optional.empty();
    }
    return matrixHttpClient.getMediaInfo(mediaId, getMatrixAccessToken());
  }

  /**
   * Deletes synapse media by its ID
   * 
   * @param mediaId synapse media id
   * @throws IllegalArgumentException if mediaId is null or blank
   * @throws JsonException if there is an error parsing the JSON response from the
   *           server
   * @throws IOException if a network or I/O error occurs during the request
   * @throws InterruptedException if the thread executing the request is
   *           interrupted
   */
  public void deleteMedia(String mediaId) throws JsonException, IOException, InterruptedException {
    if (mediaId == null || mediaId.isBlank()) {
      throw new IllegalArgumentException("mediaId must not be null or empty");
    }
    matrixHttpClient.deleteMedia(mediaId, getMatrixAccessToken());
  }

  /**
   * retrieves the chat settings
   * 
   * @return the status of the chat feature
   */
  public ChatSettingsEntity loadChatSettings() {
    return this.loadChatSettings(null, Locale.getDefault());
  }

  /**
   * retrieves the chat settings
   * 
   * @param userName the caller username
   * @param locale the caller Locale
   * @return the Chat settings
   */
  public ChatSettingsEntity loadChatSettings(String userName, Locale locale) {
    SettingValue<?> settings = settingService.get(Context.GLOBAL, Scope.APPLICATION, CHAT_SETTINGS);
    ChatSettings chatSettings = settings == null ? null : ChatSettings.fromString((String) settings.getValue());
    ChatSettingsEntity chatSettingsEntity;
    if (chatSettings == null) {
      chatSettingsEntity = new ChatSettingsEntity(true, true, true, new ArrayList<>());
    } else {
      chatSettingsEntity = new ChatSettingsEntity(chatSettings.isChatEnabled(),
                                                  chatSettings.isPrivateRoomsEnabled(),
                                                  chatSettings.isSpaceRoomsEnabled(),
                                                  new ArrayList<>());
    }
    List<SpaceTemplate> spaceTemplates;
    if (StringUtils.isNotBlank(userName)) {
      SpaceTemplateFilter spaceTemplateFilter = new SpaceTemplateFilter(userName, locale, false);
      spaceTemplates = spaceTemplateService.getSpaceTemplates(spaceTemplateFilter, Pageable.unpaged(), true);
    } else {
      spaceTemplates = spaceTemplateService.getSpaceTemplates();
    }
    for (SpaceTemplate spaceTemplate : spaceTemplates) {
      SpaceTemplateSetting spaceTemplateSetting;
      if (spaceTemplate.getExtendedProperties() == null || spaceTemplate.getExtendedProperties().isEmpty()) {
        boolean isSpaceTemplateAuthorizedByDefault = getDefaultAuthorizedSpaceTemplates().contains(spaceTemplate.getLayout()); // we
                                                                                                                               // use
                                                                                                                               // layout
                                                                                                                               // because
                                                                                                                               // the
                                                                                                                               // space
                                                                                                                               // template
                                                                                                                               // name
                                                                                                                               // is
                                                                                                                               // Localized
        spaceTemplateSetting = new SpaceTemplateSetting(spaceTemplate.getId(),
                                                        spaceTemplate.getName(),
                                                        spaceTemplate.getIcon(),
                                                        isSpaceTemplateAuthorizedByDefault,
                                                        isSpaceTemplateAuthorizedByDefault);
      } else {
        spaceTemplateSetting = new SpaceTemplateSetting();
        spaceTemplateSetting.setName(spaceTemplate.getName());
        spaceTemplateSetting.setIcon(spaceTemplate.getIcon());
        spaceTemplateSetting.setId(spaceTemplate.getId());
        spaceTemplateSetting.setAuthorized("true".equals(spaceTemplate.getExtendedProperties().get(SPACE_CHAT_AUTHORIZED)));
        spaceTemplateSetting.setChatEnabledByDefault("true".equals(spaceTemplate.getExtendedProperties()
                                                                                .get(SPACE_CHAT_ENABLED_BY_DEFAULT)));
      }
      chatSettingsEntity.getSpaceTemplateSetting().add(spaceTemplateSetting);
    }
    return chatSettingsEntity;
  }

  /**
   * Sets the settings of the chat feature
   * 
   * @param chatSettings the new status fo the chat enabled/disabled
   */
  public void saveChatSettings(ChatSettings chatSettings) {
    settingService.set(Context.GLOBAL, Scope.APPLICATION, CHAT_SETTINGS, new SettingValue<>(chatSettings.toString()));

    boolean chatFeatureEnabled = chatSettings.isChatEnabled();

    // Enable/Disable notifications
    PluginSettingService pluginSettingService = CommonsUtils.getService(PluginSettingService.class);
    pluginSettingService.saveActivePlugin(WebChannel.ID, MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN, chatFeatureEnabled);
    pluginSettingService.saveActivePlugin(MailChannel.ID, MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN, chatFeatureEnabled);

    // Disable chat application in Topbar configuration
    NavigationConfigurationService navigationConfigurationService = CommonsUtils.getService(NavigationConfigurationService.class);
    io.meeds.portal.navigation.model.NavigationConfiguration navigationConfiguration =
                                                                                     navigationConfigurationService.getConfiguration();
    List<TopbarApplication> applications = navigationConfiguration.getTopbar().getApplications();
    for (int i = 0; i < applications.size(); i++) {
      TopbarApplication topbarApplication = applications.get(i);
      if (CHAT_APPLICATION_ID.equals(topbarApplication.getId())) {
        topbarApplication.setEnabled(chatFeatureEnabled);
        applications.set(i, topbarApplication);
        navigationConfiguration.getTopbar().setApplications(applications);
        navigationConfigurationService.updateConfiguration(navigationConfiguration);
        break;
      }
    }
  }

  /**
   * Check if Chat application is authorized for the space based on its template
   * 
   * @param spaceId The space identifier
   * @return boolean : true if the chat is authorized
   */
  public boolean isChatAuthorizedForSpace(Long spaceId) {
    Space space = spaceService.getSpaceById(spaceId);
    return space != null && isChatAuthorizedForSpace(space);
  }

  /**
   * Check if Chat application is authorized for the space based on its template
   *
   * @param space The space
   * @return boolean : true if the chat is authorized
   */
  public boolean isChatAuthorizedForSpace(Space space) {
    return isChatAuthorizedByAdministration(space);
  }

  /**
   * Checks if the Chat is authorized for the template used to create the space
   * 
   * @param space The given space
   * @return True if the chat was authorized for this space template
   */
  public boolean isChatAuthorizedForSpaceTemplate(Space space) {
    ChatSettingsEntity settings = loadChatSettings();
    if (settings == null) {
      return true;
    }
    SpaceTemplateSetting spaceTemplateSetting = settings.getSpaceTemplateSetting()
                                                        .stream()
                                                        .filter(template -> space.getTemplateId() == template.getId())
                                                        .findAny()
                                                        .orElse(null);
    return spaceTemplateSetting == null || spaceTemplateSetting.isAuthorized();
  }

  /**
   * Checks if the Chat is authorized from the administration for the space
   * 
   * @param space The given space
   * @return True if the chat was authorized for this space
   */
  public boolean isChatAuthorizedByAdministration(Space space) {
    return space != null
        && (space.getExtendedProperties() == null || space.getExtendedProperties().get(SPACE_CHAT_AUTHORIZED) == null
            || space.getExtendedProperties().get(SPACE_CHAT_AUTHORIZED).equals("true"));
  }

  /**
   * Check if the chat is enabled and synched be default for the provided space
   * 
   * @param space the related space
   * @return boolean True if the chat is enabled by default
   */
  public boolean isChatEnabledByDefault(Space space) {
    ChatSettingsEntity settings = loadChatSettings();
    if (settings == null) {
      return true;
    }
    SpaceTemplateSetting spaceTemplateSetting = settings.getSpaceTemplateSetting()
                                                        .stream()
                                                        .filter(template -> space.getTemplateId() == template.getId())
                                                        .findAny()
                                                        .orElse(null);
    return spaceTemplateSetting == null || spaceTemplateSetting.isChatEnabledByDefault();
  }

  /**
   * Sets the room to readOnly
   *
   * @param roomId the room identifier
   * @param readOnly true if the room will be set readonly
   */
  public void changeChatRoomReadability(String roomId, boolean readOnly) {
    MatrixRoomPermissions roomPermissions;
    try {
      roomPermissions = getRoomSettings(roomId);
      roomPermissions.setEventsDefault(readOnly ? ADMIN_ROLE : SIMPLE_USER_ROLE);
      updateRoomSettings(roomId, roomPermissions);
    } catch (JsonException | InterruptedException | IOException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Lists the conversations the given user participates in: their direct
   * messaging rooms and the rooms of the spaces they are a member of. Used by the
   * {@code list_chat_conversations} MCP tool to give AI agents the user's chat
   * context.
   *
   * @param userName the Meeds username
   * @return the user's conversations, never {@code null}
   */
  public List<ChatConversation> getUserConversations(String userName) {
    if (StringUtils.isBlank(userName)) {
      return Collections.emptyList();
    }
    List<ChatConversation> conversations = new ArrayList<>();

    // Direct messaging rooms of the user
    for (Room room : getMatrixDMRoomsOfUser(userName)) {
      if (StringUtils.isBlank(room.getRoomId())) {
        continue;
      }
      String other = userName.equals(room.getFirstParticipant()) ? room.getSecondParticipant()
                                                                 : room.getFirstParticipant();
      conversations.add(new ChatConversation(room.getRoomId(), "dm", getUserDisplayName(other), null));
    }

    // Space rooms the user is a member of
    try {
      ListAccess<Space> memberSpaces = spaceService.getMemberSpaces(userName);
      int size = memberSpaces.getSize();
      if (size > 0) {
        for (Space space : memberSpaces.load(0, size)) {
          Room room = getRoomBySpaceId(Long.valueOf(space.getId()));
          if (room != null && StringUtils.isNotBlank(room.getRoomId())) {
            conversations.add(new ChatConversation(room.getRoomId(),
                                                   "space",
                                                   space.getDisplayName(),
                                                   Long.valueOf(space.getId())));
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Could not list space conversations for user {}", userName, e);
    }
    return conversations;
  }

  private String getUserDisplayName(String userName) {
    if (StringUtils.isBlank(userName)) {
      return userName;
    }
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity != null && identity.getProfile() != null && StringUtils.isNotBlank(identity.getProfile().getFullName())) {
      return identity.getProfile().getFullName();
    }
    return userName;
  }

  /**
   * Reads one event back with the user's own Matrix identity, so an event of a
   * room the administrator account cannot see (a direct message) resolves too.
   *
   * @param userName the platform user
   * @param roomId the room (local or full id)
   * @param eventId the event to read back
   * @return the event, or null when it cannot be read
   */
  public MatrixMessage getRoomEventAsUser(String userName, String roomId, String eventId) {
    return callAsUser(userName, null, accessToken -> matrixHttpClient.getEventById(eventId, roomId, accessToken));
  }

  /**
   * Marks a room as read for a user up to an event, with the user's own Matrix
   * identity (server-side read anchor): posts the {@code m.read} receipt so
   * every client of the user converges through Matrix sync.
   *
   * @param userName the platform user
   * @param roomId the room (local or full id)
   * @param eventId the event read up to (high-water mark)
   * @return true when the receipt was posted
   */
  public boolean markRoomAsRead(String userName, String roomId, String eventId) {
    return callAsUser(userName, false, accessToken -> {
      matrixHttpClient.sendReadReceipt(roomId, eventId, accessToken);
      return true;
    });
  }

  /**
   * Returns a Matrix access token for the given Meeds user, minted with the same
   * JWT login the browser uses so server-side reads/writes happen with the user's
   * own identity and permissions. Tokens are cached per user to avoid creating a
   * new Synapse device on every call; pass {@code forceRefresh} to mint a fresh one
   * (e.g. after a 401). Returns {@code null} when the user has no Matrix account.
   *
   * @param userName the Meeds username
   * @param forceRefresh whether to bypass the cache and mint a new token
   * @return the user's Matrix access token, or {@code null}
   */
  private String getUserAccessToken(String userName, boolean forceRefresh) throws JsonException, IOException, InterruptedException {
    if (!forceRefresh) {
      String cachedToken = userAccessTokensCache.get(userName);
      if (StringUtils.isNotBlank(cachedToken)) {
        return cachedToken;
      }
    }
    String matrixId = getMatrixIdForUser(userName);
    if (StringUtils.isBlank(matrixId)) {
      return null;
    }
    String matrixLocalPart = extractUserId(matrixId);
    String jwtToken = getJWTSessionToken(matrixLocalPart);
    String accessToken = matrixHttpClient.getAccessToken(jwtToken);
    if (StringUtils.isNotBlank(accessToken)) {
      userAccessTokensCache.put(userName, accessToken);
    }
    return accessToken;
  }

  /**
   * Executes a Matrix operation on behalf of the given user with their cached
   * access token. If the token has been rejected (HTTP 401), the token is
   * refreshed once and the operation retried. Any other failure, or a missing
   * Matrix account, yields the provided fallback value.
   *
   * @param userName the Meeds username acting
   * @param fallback the value to return when the operation cannot be performed
   * @param operation the operation to run with a valid access token
   * @return the operation result, or {@code fallback}
   */
  private <T> T callAsUser(String userName, T fallback, UserMatrixCall<T> operation) {
    try {
      String accessToken = getUserAccessToken(userName, false);
      if (StringUtils.isBlank(accessToken)) {
        LOG.warn("User {} has no Matrix account, skipping chat operation", userName);
        return fallback;
      }
      return callWithTokenRefresh(userName, fallback, operation, accessToken);
    } catch (InterruptedException e) {
      // Preserve the interrupt status so callers up the stack can react to it.
      Thread.currentThread().interrupt();
      LOG.error("Matrix chat operation interrupted for user {}", userName, e);
      return fallback;
    } catch (Exception e) {
      LOG.warn("Matrix chat operation failed for user {}", userName, e);
      return fallback;
    }
  }

  /**
   * Runs the operation with the given access token and, if Synapse rejects the
   * token (HTTP 401), refreshes it once and retries. Extracted from
   * {@link #callAsUser} so the retry stays a flat, single-purpose block.
   *
   * @param <T> the operation result type
   * @param userName the Meeds username acting
   * @param fallback the value to return when the refreshed token is unavailable
   * @param operation the operation to run with a valid access token
   * @param accessToken the initial access token to use
   * @return the operation result, or {@code fallback}
   * @throws Exception any failure raised by the operation itself
   */
  private <T> T callWithTokenRefresh(String userName,
                                     T fallback,
                                     UserMatrixCall<T> operation,
                                     String accessToken) throws Exception { // NOSONAR
    try {
      return operation.call(accessToken);
    } catch (MatrixUnauthorizedException unauthorized) {
      LOG.info("Matrix access token for user {} is no longer valid, refreshing and retrying", userName);
      String refreshedToken = getUserAccessToken(userName, true);
      if (StringUtils.isBlank(refreshedToken)) {
        return fallback;
      }
      return operation.call(refreshedToken);
    }
  }

  @FunctionalInterface
  private interface UserMatrixCall<T> {
    T call(String accessToken) throws Exception; // NOSONAR
  }

  /**
   * Returns the most recent messages of a conversation, in chronological order,
   * read <strong>as the given user</strong> so that Synapse enforces exactly what
   * that user is allowed to see (membership, history visibility, redactions). A
   * cheap participant pre-check avoids reading conversations the user is obviously
   * not part of. Used by the {@code get_chat_messages} MCP tool.
   *
   * @param userName the Meeds username acting
   * @param roomId the Matrix room id of the conversation
   * @param limit the maximum number of messages to return (clamped to [1, 200])
   * @return the conversation messages, oldest first, never {@code null}
   */
  public List<ChatMessage> getRoomMessages(String userName, String roomId, int limit) {
    if (StringUtils.isBlank(userName) || StringUtils.isBlank(roomId)) {
      return Collections.emptyList();
    }
    String normalizedRoomId = extractRoomId(roomId);
    boolean isParticipant = getUserConversations(userName).stream()
                                                          .anyMatch(conversation -> normalizedRoomId.equals(extractRoomId(conversation.getRoomId())));
    if (!isParticipant) {
      LOG.warn("User {} is not a participant of conversation {}, refusing to read its messages", userName, normalizedRoomId);
      return Collections.emptyList();
    }
    int effectiveLimit = Math.clamp(limit, 1, 200);
    return callAsUser(userName, Collections.<ChatMessage> emptyList(), accessToken -> {
      List<MatrixMessage> matrixMessages = matrixHttpClient.getRoomMessages(normalizedRoomId, effectiveLimit, accessToken);
      List<ChatMessage> messages = new ArrayList<>();
      for (MatrixMessage matrixMessage : matrixMessages) {
        messages.add(new ChatMessage(extractUserId(matrixMessage.getSender()),
                                     matrixMessage.getMessageContent(),
                                     matrixMessage.getTimeStamp()));
      }
      // The client API returns events newest-first (dir=b); flip to chronological.
      Collections.reverse(messages);
      return messages;
    });
  }

  /**
   * Returns the unread conversations of the given user, read <strong>as that
   * user</strong> via a Matrix {@code /sync}, so only the rooms the user has
   * joined are considered. Each entry carries the unread count and the recent
   * messages, with a human readable title resolved from the user's conversations.
   * Used by the {@code get_unread_chat_messages} MCP tool.
   *
   * @param userName the Meeds username acting
   * @return the user's unread conversations, never {@code null}
   */
  public List<ChatUnread> getUnreadConversations(String userName) {
    if (StringUtils.isBlank(userName)) {
      return Collections.emptyList();
    }
    return callAsUser(userName, Collections.<ChatUnread> emptyList(), accessToken -> {
      Map<String, String> titlesByRoomId = new HashMap<>();
      for (ChatConversation conversation : getUserConversations(userName)) {
        // Key by the normalized (local-part) id: getUnreadRooms returns local-part ids,
        // while a conversation's stored id can be full (DM rooms) — extractRoomId both.
        titlesByRoomId.put(extractRoomId(conversation.getRoomId()), conversation.getTitle());
      }
      List<ChatUnread> unread = new ArrayList<>();
      for (MatrixUnreadRoom unreadRoom : matrixHttpClient.getUnreadRooms(accessToken, 20)) {
        List<ChatMessage> messages = new ArrayList<>();
        // The /sync timeline is already in chronological order (oldest first).
        for (MatrixMessage matrixMessage : unreadRoom.getMessages()) {
          messages.add(new ChatMessage(extractUserId(matrixMessage.getSender()),
                                       matrixMessage.getMessageContent(),
                                       matrixMessage.getTimeStamp()));
        }
        unread.add(new ChatUnread(unreadRoom.getRoomId(),
                                  titlesByRoomId.get(unreadRoom.getRoomId()),
                                  unreadRoom.getUnreadCount(),
                                  messages));
      }
      return unread;
    });
  }

  /**
   * Sends a textual message to a conversation <strong>as the given user</strong>,
   * after checking that the user participates in it. Synapse additionally enforces
   * that the user is allowed to post. Used by the {@code send_chat_message} MCP
   * tool, e.g. to let an AI agent post a real-time reply on the user's behalf.
   *
   * @param userName the Meeds username acting
   * @param roomId the Matrix room id of the conversation
   * @param text the plain text message to send
   * @return the created event id, or {@code null} when the message could not be sent
   */
  public String sendMessage(String userName, String roomId, String text) {
    if (StringUtils.isBlank(userName) || StringUtils.isBlank(roomId) || StringUtils.isBlank(text)) {
      return null;
    }
    String normalizedRoomId = extractRoomId(roomId);
    List<ChatConversation> conversations = getUserConversations(userName);
    boolean isParticipant = conversations.stream().anyMatch(conversation -> normalizedRoomId.equals(extractRoomId(conversation.getRoomId())));
    if (!isParticipant) {
      LOG.warn("User {} is not a participant of conversation {}, refusing to send a message. Known conversations: {}",
               userName,
               normalizedRoomId,
               conversations.stream().map(ChatConversation::getRoomId).toList());
      return null;
    }
    return callAsUser(userName,
                      null,
                      accessToken -> matrixHttpClient.sendMessage(normalizedRoomId, text, UUID.randomUUID().toString(), accessToken));
  }

  /**
   * Resolves the display title of a conversation carrying a search hit. The user's
   * conversation list answers for the rooms the platform tracks; for the others — a
   * direct message opened from another Matrix client, a room whose platform record is
   * gone — Matrix itself is asked for the room name, or for the other member's name
   * when the room has none. What Matrix answers is memoised, including a miss, so a
   * search returning several hits of the same conversation resolves it once.
   *
   * @param roomLocalId the room local part the hit belongs to
   * @param titlesByRoomId the titles of the conversations the platform tracks
   * @param titlesFromMatrix the titles already resolved from Matrix, updated in place
   * @param currentUserMatrixId the requesting user's full Matrix id
   * @param accessToken the requesting user's Matrix access token
   * @return the conversation title, or {@code null} when neither side can name it
   */
  /**
   * Tells whether a conversation carrying a search hit is one the given user may see in the chat
   * and open from a result: the room is tracked by the platform and the user can access it. This
   * is the same test the chat list (processRooms) and the open action (byRoomId) rely on, so a
   * search result is shown only when it maps to a real, openable conversation. Results are
   * memoised per room so a search returning several hits of one conversation checks it once.
   *
   * @param roomLocalId the room local part the hit belongs to
   * @param userName the login of the searching user
   * @param accessibleByRoomId the decisions already taken, updated in place
   * @return {@code true} when the conversation is tracked and accessible to the user
   */
  private boolean isAccessibleConversation(String roomLocalId, String userName, Map<String, Boolean> accessibleByRoomId) {
    return accessibleByRoomId.computeIfAbsent(roomLocalId, id -> {
      Room room = getById(id, true);
      return room != null && canAccess(room, userName);
    });
  }

  private String resolveConversationTitle(String roomLocalId,
                                          Map<String, String> titlesByRoomId,
                                          Map<String, String> titlesFromMatrix,
                                          String currentUserMatrixId,
                                          String accessToken) {
    String knownTitle = titlesByRoomId.get(roomLocalId);
    if (StringUtils.isNotBlank(knownTitle)) {
      return knownTitle;
    }
    if (titlesFromMatrix.containsKey(roomLocalId)) {
      return titlesFromMatrix.get(roomLocalId);
    }
    String matrixTitle = matrixHttpClient.getRoomDisplayName(roomLocalId, currentUserMatrixId, accessToken);
    titlesFromMatrix.put(roomLocalId, matrixTitle);
    return matrixTitle;
  }

  /**
   * Runs a full-text search of message bodies <strong>as the given user</strong>,
   * either across all their conversations (when {@code conversationId} is blank) or
   * scoped to a single conversation. Synapse enforces the user's Matrix visibility, and
   * the hits are then restricted to the conversations that are tracked by the platform
   * and accessible to the user — the same rooms the chat list shows and the row can open
   * — so a room they still belong to on Matrix but not in the chat (created from another
   * Matrix client, left behind by a failed space kick) is never returned. Each hit is
   * resolved to a human readable conversation title. Backs the
   * {@code search_chat_messages} MCP tool and the chat UI search.
   *
   * @param userName the Meeds username acting
   * @param query the free-text search term
   * @param conversationId the Matrix room id to scope the search to, or
   *          {@code null}/blank to search across all the user's conversations
   * @param limit the maximum number of hits to return (clamped to [1, 100])
   * @return the matching messages, most recent first, never {@code null}
   */
  public List<ChatSearchResult> searchChatMessages(String userName, String query, String conversationId, int limit) {
    if (StringUtils.isBlank(userName) || StringUtils.isBlank(query)) {
      return Collections.emptyList();
    }
    String scopedRoomId = StringUtils.isNotBlank(conversationId) ? extractRoomId(conversationId) : null;
    Map<String, String> titlesByRoomId = new HashMap<>();
    for (ChatConversation conversation : getUserConversations(userName)) {
      titlesByRoomId.put(extractRoomId(conversation.getRoomId()), conversation.getTitle());
    }
    Map<String, Boolean> accessibleByRoomId = new HashMap<>();
    if (scopedRoomId != null && !isAccessibleConversation(scopedRoomId, userName, accessibleByRoomId)) {
      LOG.warn("User {} cannot access conversation {}, refusing to search it", userName, scopedRoomId);
      return Collections.emptyList();
    }
    int effectiveLimit = Math.clamp(limit, 1, 100);
    String currentUserMatrixId = getUserFullMatrixID(userName);
    Map<String, String> titlesFromMatrix = new HashMap<>();
    return callAsUser(userName, Collections.<ChatSearchResult> emptyList(), accessToken -> {
      List<MatrixMessage> matches = matrixHttpClient.searchMessages(query, scopedRoomId, effectiveLimit, accessToken);
      List<ChatSearchResult> results = new ArrayList<>();
      for (MatrixMessage match : matches) {
        String roomLocalId = extractRoomId(match.getRoomId());
        if (!isAccessibleConversation(roomLocalId, userName, accessibleByRoomId)) {
          // Synapse answers for every room the user belongs to on Matrix, which can outlive what
          // the platform grants — a room created from another Matrix client, a space kick that
          // failed on leave, a restored backup. Keep only the conversations the platform tracks
          // and the user may view: those are the ones the chat list shows (processRooms builds it
          // from the same tracked rooms) and the only ones the row can open (byRoomId uses the
          // same getById + canAccess). A hit outside that set is dropped rather than shown as a
          // card that leads nowhere.
          LOG.debug("Skipping a search hit of user {} in conversation {}, which they cannot access",
                    userName,
                    roomLocalId);
          continue;
        }
        results.add(new ChatSearchResult(roomLocalId,
                                         match.getEventId(),
                                         resolveConversationTitle(roomLocalId,
                                                                  titlesByRoomId,
                                                                  titlesFromMatrix,
                                                                  currentUserMatrixId,
                                                                  accessToken),
                                         extractUserId(match.getSender()),
                                         match.getMessageContent(),
                                         match.getTimeStamp(),
                                         null,
                                         false));
      }
      return results;
    });
  }
}
