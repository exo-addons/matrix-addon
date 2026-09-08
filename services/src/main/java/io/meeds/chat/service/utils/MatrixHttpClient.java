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
package io.meeds.chat.service.utils;

import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.service.model.MediaInfo;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.model.MatrixRoomPermissions;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.meeds.chat.service.utils.HTTPHelper.*;
import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixHttpClient {
  private static final Log    LOG                   = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  /** Marker Matrix prepends to the fallback body of an edit event ("* the new text"). */
  private static final String EDIT_FALLBACK_PREFIX  = "* ";

  /**
   * Get an authenticated access token for the administrative tasks
   *
   * @param userJWTToken JWT token used to authenticate the admin user
   * @return String the access token for the authenticated user
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String getAccessToken(String userJWTToken) throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    if (StringUtils.isEmpty(userJWTToken)) {
      throw new IllegalArgumentException(MATRIX_ADMIN_USERNAME_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/r0/login";
    String payload = """
        {
          "type":"org.matrix.login.jwt",
          "token": "%s"
        }
        """.formatted(userJWTToken);
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, "", payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the authentication of the admin after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return getAccessToken(userJWTToken);
        } else {
          LOG.warn("Could not authenticate admin account with JWT, Matrix server returned HTTP {}", response.statusCode());
          if (LOG.isDebugEnabled()) {
            LOG.debug("Matrix authentication error response body: {}", response.body());
          }
          throw new IllegalStateException("Could not authenticate Admin account on Matrix (HTTP " + response.statusCode() + ")");
        }
      }
    } catch (Exception e) {
      LOG.debug("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  /**
   * Authenticates a user using his userName and password
   * 
   * @param userName the username
   * @param password the user password
   * @return String : access token for the authenticated user
   */
  public String authenticateUser(String userName, String password) throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/login";
    String payload = """
          {
            "identifier": {
              "type": "m.id.user",
              "user": "%s"
            },
            "password": "%s",
              "type": "m.login.password"
          }

        """.formatted(userName, password);
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, "", payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          LOG.warn("Too many requests on Matrix server, retrying the authentication of {} after {}ms", userName, sleepInMs);
          Thread.sleep(sleepInMs);
          return authenticateUser(userName, password);
        } else {
          LOG.error("Error Authenticating user {} with a password, Matrix server returned HTTP {} error {}",
                    userName,
                    String.valueOf(response.statusCode()),
                    response.body());
          return null;
        }
      }
    } catch (Exception e) {
      LOG.error("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  public String createRoom(String name, String description, String token) throws Exception {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/createRoom";

    String payload = """
        {
          "name": "%s",
          "topic": "%s",
          "preset": "private_chat",
          "visibility": "private",
          "initial_state": [
            {
              "type": "m.room.guest_access",
              "state_key": "",
              "content": {
                "guest_access": "forbidden"
              }
            }
          ]
        }
        """.formatted(name.replace("\"", "\\\""), cleanDescription(description));

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        String roomId = jsonGenerator.createJsonObjectFromString(response.body()).getElement("room_id").getStringValue();
        return roomId.substring(0, roomId.indexOf(":" + PropertyManager.getProperty(MATRIX_SERVER_NAME)));
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          LOG.warn("Too many requests on Matrix server, retrying the creation of the room of {} after {}ms", name, sleepInMs);
          Thread.sleep(sleepInMs);
          return createRoom(name, description, token);
        } else {
          LOG.error("Error creating a team, Matrix server returned HTTP {} error {}",
                    String.valueOf(response.statusCode()),
                    response.body());

          throw new Exception("Error creating a team, Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                         response.body()));
        }
      }
    } catch (Exception e) {
      LOG.error("Could not create a team on Matrix", e);
      throw e;
    }
  }

  private String cleanDescription(String description) {
    String plainTextDescription = Jsoup.parse(description).text();
    if (StringUtils.isNotBlank(plainTextDescription)) {
      return plainTextDescription.replace("\"", "\\\"");
    }
    return "";
  }

  public String createUserAccount(User user, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String nonce = getRegistrationNonce(token);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    String hmac = hmacUserProperties(nonce, user.getUserName(), user.getUserName(), false);

    String payload = """
        {
           "nonce": "%s",
           "username": "%s",
           "displayname": "%s",
           "password": "%s",
           "admin": false,
           "mac": "%s"
          }
        """.formatted(nonce, user.getUserName(), user.getDisplayName(), user.getUserName(), hmac);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        return userAccount.getElement("user_id").getStringValue();
      } else {
        LOG.error("Error creating a user account, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a user account on Matrix", e);
      return null;
    }
  }

  /**
   * Sets a new display name for the user
   * 
   * @param userMatrixId the ID of the user on Matrix
   * @param displayName the new display name
   * @param token the access token
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public void updateUserDisplayName(String userMatrixId, String displayName, String token) throws IOException,
                                                                                           InterruptedException,
                                                                                           JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userMatrixId, StandardCharsets.UTF_8);

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + encodedUserMatrixId
        + "/displayname";

    String payload = """
        {
          "displayname": "%s"
        }
        """.formatted(displayName);
    HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("The display name of the User {} was updated successfully", userMatrixId);
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to update the display name of the user {} after {}ms",
                 userMatrixId,
                 sleepInMs);
        Thread.sleep(sleepInMs);
        updateUserDisplayName(userMatrixId, displayName, token);
      } else {
        throw new RuntimeException("Error Updating the display name of the user %s, Matrix server returned HTTP %s error %s".formatted(userMatrixId,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
      }
    }

  }

  /**
   * Saves the user account on Matrix
   * 
   * @param user the user identity
   * @param matrixUserId the user Matrix ID to set
   * @param isNew if the user has been just created
   * @param token the authorization token
   * @return the user Matrix ID
   */
  public String saveUserAccount(Identity user, String matrixUserId, boolean isNew, String token) {
    return saveUserAccount(user, matrixUserId, isNew, token, false, true);
  }

  /**
   * Saves the user account
   * 
   * @param user the User
   * @param matrixUserId the corresponding matrix ID
   * @param isNew if the user account is one
   * @param token the access token
   * @param isEnableUserOperation is this an operation to enable/disable the user
   *          on Matrix
   * @return the user Matrix ID
   */

  public String saveUserAccount(Identity user,
                                String matrixUserId,
                                boolean isNew,
                                String token,
                                boolean isEnableUserOperation,
                                boolean isUserEnabled) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String fullMatrixUserId = "@%s:%s".formatted(matrixUserId,
                                                 PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + fullMatrixUserId;

    String payload;
    String password = null;
    if (isNew) {
      password = PasswordKeyGenerator.generatePassword(10);
      payload = """
           {
            "password": "%s",
            "logout_devices": false,
            "displayname": "%s",
            "threepids": [
                {
                    "medium": "email",
                    "address": "%s"
                }
            ],
            "user_type": null,
            "locked": false
          }
          """.formatted(password, user.getProfile().getFullName(), user.getProfile().getEmail());
    } else if (isEnableUserOperation && isUserEnabled) {
      payload = """
          {
           "password": "%s",
           "displayname": "%s",
           "threepids": [
             {
               "medium": "email",
               "address": "%s"
             }
           ],
           "deactivated": %s
           }
          """.formatted(PasswordKeyGenerator.generatePassword(10),
                        user.getProfile().getFullName(),
                        user.getProfile().getEmail(),
                        String.valueOf(false));
    } else {
      payload = """
          {
            "displayname": "%s",
            "threepids": [
              {
                "medium": "email",
                "address": "%s"
              }
            ],
            "deactivated": %s
          }
          """.formatted(user.getProfile().getFullName(), user.getProfile().getEmail(), String.valueOf(!isUserEnabled));
    }
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        String fullMatrixID = userAccount.getElement("name").getStringValue();
        // If the user is a new user, we need to authenticate him
        if (isNew) {
          authenticateUser(matrixUserId, password);
          LOG.info("User {} authenticated successfully", user.getRemoteId());
        }
        return fullMatrixID.contains(":") ? fullMatrixID.substring(1, fullMatrixID.indexOf(":")) : fullMatrixID;
      } else {
        throw new RuntimeException("Error creating a user account, Matrix server returned HTTP %s error %s".formatted(response.statusCode(),
                                                                                                                      response.body()));
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not create a user account on Matrix", e);
    }
  }

  private String hmacUserProperties(String nonce, String userName, String password, boolean isAdmin) {
    String userProperties = nonce + "\0" + userName + "\0" + password + "\0" + (isAdmin ? "admin" : "notadmin");
    return new HmacUtils(HmacAlgorithms.HMAC_SHA_1,
                         PropertyManager.getProperty(SHARED_SECRET_REGISTRATION)).hmacHex(userProperties);
  }

  private String getRegistrationNonce(String accessToken) {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    try {
      HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("nonce").getStringValue();
      } else {
        LOG.error("Error getting Nonce, Matrix server returned HTTloginP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not get the nonce on Matrix", e);
      return null;
    }
  }

  public String disableAccount(String userName, boolean eraseData, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/deactivate/" + userName;
    String payload = """
        {
          "erase": %s
        }
        """.formatted(Boolean.FALSE.toString()); // erase or not the user data on Matrix, currently : Not erase

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("id_server_unbind_result").getStringValue();
      } else {
        LOG.error("Error deactivating user, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not deactivate the user on Matrix", e);
      return null;
    }
  }

  public String renameRoom(String roomId, String newRoomName, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/state/m.room.name/";
    String payload = """
        {
          "name": "%s"
        }
        """.formatted(newRoomName);

    try {
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("event_id").getStringValue();
      } else {
        LOG.error("Error renaming the room {}, Matrix server returned HTTP {} error {}",
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not rename the room on Matrix", e);
      return null;
    }
  }

  /**
   * Invites a user to a specific room on Matrix
   * 
   * @param roomId the Id of the room
   * @param userMatrixId the Matrix id of the user
   * @param invitationMessage a custom invitation message
   * @return
   */
  public boolean inviteUserToRoom(String roomId, String userMatrixId, String invitationMessage, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/invite";
    String payload = """
          {
            "reason": "%s",
            "user_id": "%s"
          }
        """.formatted(invitationMessage, fullMatrixUserId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully invited to room {}", userMatrixId, roomId);
        return true;
      } else {
        LOG.error("Error inviting user {} to the room {}, Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not invite a user to a room on Matrix", e);
      return false;
    }
  }

  /**
   * Kicks a user out of a specific room on Matrix
   *
   * @param roomId the Id of the room
   * @param userMatrixId the Matrix id of the user
   * @param raisonMessage the raison message
   */
  public boolean kickUserFromRoom(String roomId, String userMatrixId, String raisonMessage, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/kick";
    String payload = """
          {
            "reason": "%s",
            "user_id": "%s"
          }
        """.formatted(raisonMessage, fullMatrixUserId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully kicked out of room {}", userMatrixId, roomId);
        return true;
      } else {
        LOG.error("Error kicking user {} from room {}, Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not kick out a user from the room on Matrix", e);
      return false;
    }
  }

  /**
   * Adds directly a user to a room
   * 
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the ID of the user of Matrix
   * @return Boolean true if operation succeeded
   */
  public boolean joinUserToRoom(String matrixRoomId, String matrixIdOfUser, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/join/" + fullRoomId;
    String payload = """
          {
            "user_id": "%s"
          }
        """.formatted(fullUserMatrixId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully joined the room {}", matrixIdOfUser, matrixRoomId);
        return true;
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          LOG.warn("Too many requests on Matrix server, retrying to join the user {} on the room {} after {}ms",
                   matrixIdOfUser,
                   matrixRoomId,
                   sleepInMs);
          Thread.sleep(sleepInMs);
          return joinUserToRoom(matrixRoomId, matrixIdOfUser, token);
        } else {
          LOG.error("Error joining user {} to the room {}, Matrix server returned HTTP {} error {}",
                    matrixIdOfUser,
                    matrixRoomId,
                    String.valueOf(response.statusCode()),
                    response.body());
          return false;
        }
      }
    } catch (Exception e) {
      LOG.error("Could not join a user to a room on Matrix", e);
      return false;
    }
  }

  /**
   * Make user an admin of the room
   * 
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the id of the user
   * @return Boolean true if succeeded
   */

  public boolean makeUserAdminInRoom(String matrixRoomId, String matrixIdOfUser, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/rooms/" + fullRoomId + "/make_room_admin";
    String payload = """
          {
            "user_id": "%s"
          }
        """.formatted(fullUserMatrixId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} is successfully an admin of the room {}", matrixIdOfUser, matrixRoomId);
        return true;
      } else {
        LOG.error("Error upgrading user {} to Admin in the room {}, Matrix server returned HTTP {} error {}",
                  matrixIdOfUser,
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not make a user an admin in a room on Matrix", e);
      return false;
    }
  }

  /**
   * Checks if a user is a member of a room
   *
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the ID of the user on Matrix
   * @return true if the user is a member of the room, false otherwise
   */
  public boolean isUserMemberOfRoom(String matrixRoomId, String matrixIdOfUser, String accessToken) throws IOException,
                                                                                                      InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/joined_members";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      JSONObject joinedMembers = new JSONObject(response.body()).optJSONObject("joined");
      return joinedMembers != null && joinedMembers.has(fullUserMatrixId);
    } else {
      LOG.error("Error getting members of room {}, Matrix server returned HTTP {} error {}",
                matrixRoomId,
                String.valueOf(response.statusCode()),
                response.body());
      return false;
    }
  }

  /**
   * Get permissions settings of a room
   *
   * @param matrixRoomId
   * @return MatrixRoomPermissions object containing settings of the room
   */
  public MatrixRoomPermissions getRoomSettings(String matrixRoomId,
                                               String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId
        + "/state/m.room.power_levels/";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("Permissions of room  {} were successfully loaded !", matrixRoomId);
      JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
      JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
      return MatrixRoomPermissions.fromJson(jsonResponse);
    } else {
      throw new RuntimeException("Error getting room permissions of %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                           String.valueOf(response.statusCode()),
                                                                                                                           response.body()));
    }
  }

  /**
   * Updates the room settings
   *
   * @param matrixRoomId the Id of the room
   * @return MatrixRoomPermissions updated room settings
   */
  public String updateRoomSettings(String matrixRoomId,
                                   MatrixRoomPermissions roomSettings,
                                   String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String payload = roomSettings.toJson();
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId
        + "/state/m.room.power_levels/";

    HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("Permissions of room  {} were successfully updated !", matrixRoomId);
      JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
      JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
      return jsonResponse.getElement("event_id").getStringValue();
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to update the settings of the room {} after {}ms",
                 matrixRoomId,
                 sleepInMs);
        Thread.sleep(sleepInMs);
        return updateRoomSettings(matrixRoomId, roomSettings, accessToken);
      } else {
        throw new RuntimeException("Error updating room permissions of %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                              String.valueOf(response.statusCode()),
                                                                                                                              response.body()));
      }
    }
  }

  public String uploadFile(String fileName, String mimeType, byte[] imageBytes, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/media/v3/upload?filename=" + fileName;
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, accessToken, mimeType, imageBytes);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("File uploaded successfully !");
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("content_uri").getStringValue();
      } else {
        LOG.error("Error uploading the file ,Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not upload the file on Matrix", e);
      return null;
    }
  }

  /**
   * Update room avatar
   * 
   * @param matrixRoomId the room ID
   * @param avatarURL the Avatar URL on
   * @return true if succeeded otherwise false
   */
  public boolean updateRoomAvatar(String matrixRoomId, String avatarURL, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8) + "/state/m.room.avatar/";
    String payload = """
        {
          "url":"%s"
        }
        """.formatted(avatarURL);
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Avatar of room {} updated successfully !", matrixRoomId);
        return true;
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the update of the room avatar after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return updateRoomAvatar(matrixRoomId, avatarURL, accessToken);
        } else {
          throw new RuntimeException("Error updating the avatar of the room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                                   String.valueOf(response.statusCode()),
                                                                                                                                   response.body()));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not update the avatar of the room on Matrix", e);
    }

  }

  /**
   * Update the user avatar
   * 
   * @param userMatrixId the Matrix room ID
   * @param avatarURL the avatar URL on Matrix
   * @return true if updated, false otherwise
   */
  public boolean updateUserAvatar(String userMatrixId, String avatarURL, String accessToken) {
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + fullMatrixUserId + "/avatar_url";
    String payload = """
        {
          "avatar_url":"%s"
        }
        """.formatted(avatarURL);
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Avatar of user {} updated successfully !", userMatrixId);
        return true;
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the update of the room avatar after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return updateUserAvatar(userMatrixId, avatarURL, accessToken);
        } else {
          LOG.error("Error updating the avatar of the user {} ,Matrix server returned HTTP {} error {}",
                    userMatrixId,
                    String.valueOf(response.statusCode()),
                    response.body());
          return false;
        }
      }
    } catch (Exception e) {
      LOG.error("Could not update the avatar of the user on Matrix", e);
      return false;
    }
  }

  /**
   * Updates the room description
   * 
   * @param matrixRoomId the ID of the room on Matrix
   * @param description the updated description
   * @return true if the operation is successful, false otherwise
   */
  public boolean updateRoomDescription(String matrixRoomId, String description, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8) + "/state/m.room.topic/";
    String plainDescription = description.replaceAll("<[^>]*>", "").replace("\n", "");
    String payload = """
        {
        "topic":"%s",
        "org.matrix.msc3765.topic":
          [
            {
              "body":"%s",
              "mimetype":"text/html"
            }
          ]
        }
        """.formatted(plainDescription, URLEncoder.encode(description, StandardCharsets.UTF_8));
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Description of room {} updated successfully !", matrixRoomId);
        return true;
      } else {
        LOG.error("Error updating the description of the room {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not update the description of the room on Matrix", e);
      return false;
    }

  }

  /**
   * Deletes a Matrix room
   * 
   * @param matrixRoomId the ID of the room
   * @param accessToken the access token
   * @return boolean True if the deletion is successful otherwise False
   */
  public boolean deleteRoom(String matrixRoomId, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/rooms/"
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8);
    String payload = """
          {
          }
        """;
    try {
      HttpResponse<String> response = sendHttpDeleteRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("The room {} was deleted successfully !", matrixRoomId);
        return true;
      } else {
        LOG.error("Error deleting the room {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not delete the room on Matrix", e);
      return false;
    }
  }

  public String getUserDisplayName(String userMatrixId,
                                   String matrixAccessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userMatrixId, StandardCharsets.UTF_8);

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + encodedUserMatrixId
        + "/displayname";

    HttpResponse<String> response = sendHttpGetRequest(url, matrixAccessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("displayname").getStringValue();
    } else {
      throw new RuntimeException("Error Updating the display name of the user %s, Matrix server returned HTTP %s error %s".formatted(userMatrixId,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
    }

  }

  /**
   * Retrieve the user presence from Matrix server
   * 
   * @param matrixIdOfUser the ID of the user on Matrix
   * @param accessToken the access token
   * @return the value of the presence
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public String getUserPresence(String matrixIdOfUser,
                                String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/presence/" + encodedUserMatrixId + "/status";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("presence").getStringValue();
    } else {
      throw new RuntimeException("Error retrieving the presence of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                   String.valueOf(response.statusCode()),
                                                                                                                                   response.body()));
    }
  }

  /**
   * Retrieve the user details from Matrix server
   *
   * @param matrixIdOfUser the ID of the user on Matrix
   * @param accessToken the access token
   * @return String representing JSON of the user
   * @throws IOException
   * @throws InterruptedException
   */
  public String getUser(String matrixIdOfUser, String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + encodedUserMatrixId;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error retrieving the details of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                  String.valueOf(response.statusCode()),
                                                                                                                                  response.body()));
    }
  }

  /**
   * Set the user presence on Matrix server
   * 
   * @param matrixIdOfUser the ID of the user n Matrix
   * @param presence the presence value : 'online, offline , unavailable"
   * @param statusMessage : the personalized status message
   * @param accessToken the access token
   * @return
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public String setUserPresence(String matrixIdOfUser,
                                String presence,
                                String statusMessage,
                                String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/presence/" + encodedUserMatrixId + "/status";

    String payload = """
        {
          "presence": "%s",
          "status_msg": "%s"
        }
        """.formatted(presence, statusMessage);

    HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to retrieve the user presence after {}ms", sleepInMs);
        Thread.sleep(sleepInMs);
        return setUserPresence(matrixIdOfUser, presence, statusMessage, accessToken);
      } else {
        throw new RuntimeException("Error retrieving the presence of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
      }
    }
  }

  /**
   * Overrides the rate limits for a given user
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param messagesPerSecond the allowed number of messages per second
   * @param burstCount how many actions that can be performed before being limited
   * @param accessToken the access token
   * @return String the applied rate limits configuration
   * @throws IOException
   * @throws InterruptedException
   */
  public String overrideRateLimitForUser(String userIdOnMatrix,
                                         int messagesPerSecond,
                                         int burstCount,
                                         String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userIdOnMatrix, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/users/" + encodedUserMatrixId
        + "/override_ratelimit";

    String payload = """
        {
          "messages_per_second": %s,
          "burst_count": %s
        }
        """.formatted(messagesPerSecond, burstCount);

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error overriding the rate limits for the user %s ,Matrix server returned HTTP %s error %s".formatted(userIdOnMatrix,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
    }
  }

  /**
   * Gets the overridden the rate limits for a given user
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param accessToken the access token
   * @return String the applied rate limits configuration
   * @throws IOException
   * @throws InterruptedException
   */
  public String getOverriddenRateLimitForUser(String userIdOnMatrix, String accessToken) throws IOException,
                                                                                         InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userIdOnMatrix, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/users/" + encodedUserMatrixId
        + "/override_ratelimit";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error overriding the rate limits for the user %s ,Matrix server returned HTTP %s error %s".formatted(userIdOnMatrix,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
    }
  }

  /**
   * Retrieves an event from Matrix by its Id
   *
   * @param eventId the event ID
   * @return Map representing the message
   */
  public MatrixMessage getEventById(String eventId, String roomId, String accessToken) throws IOException,
                                                                                       InterruptedException,
                                                                                       JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + roomId + "/event/" + eventId;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      JsonValue jsonMessage = new JsonGeneratorImpl().createJsonObjectFromString(response.body());
      MatrixMessage message = new MatrixMessage();
      message.setEventId(eventId);
      message.setRoomId(jsonMessage.getElement("room_id").getStringValue());
      message.setType(jsonMessage.getElement("type").getStringValue());
      message.setSender(jsonMessage.getElement("sender").getStringValue());
      message.setTimeStamp(Long.parseLong(jsonMessage.getElement("origin_server_ts").getStringValue()));
      if (jsonMessage.getElement("content") != null) {
        JsonValue content = jsonMessage.getElement("content");
        if(content.getElement("body") != null) {
          message.setMessageContent(content.getElement("body").getStringValue());
        }
        if (content.getElement("msgtype") != null) {
          message.setMessageType(content.getElement("msgtype").getStringValue());
          if ("m.text".equals(message.getMessageType()) && content.getElement("org.matrix.custom.html") != null) {
            message.setMessageContent(jsonMessage.getElement("formatted_body").getStringValue());
          }
        }
        JsonValue mentionsElement = content.getElement("m.mentions");
        if (mentionsElement != null) {
          JsonValue mentionedUsersElement = mentionsElement.getElement("user_ids");
          if (mentionedUsersElement != null) {
            Iterator<JsonValue> mentionedUsersIterator = mentionedUsersElement.getElements();
            List<String> mentionedUsers = new ArrayList<>();
            while (mentionedUsersIterator.hasNext()) {
              JsonValue nextMentioned = mentionedUsersIterator.next();
              mentionedUsers.add(nextMentioned.getStringValue());
            }
            message.setMentionedUsers(mentionedUsers);
          }
        }
      }
      return message;
    } else {
      if (response.statusCode() != 404) {
        throw new RuntimeException("Error retrieving the message of the event %s ,Matrix server returned HTTP %s error %s".formatted(eventId,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
      } else {
        // if the event is missing or the user has not the right to access it (event in
        // a private room)
        return null;
      }
    }
  }

  /**
   * Retrieves the most recent messages of a room as the user owning the given
   * access token, using the Matrix client API
   * ({@code /_matrix/client/v3/rooms/{roomId}/messages}). Synapse enforces the
   * user's own visibility (membership, history visibility, redactions); if the
   * user is not allowed to read the room an empty list is returned. Events are
   * returned newest-first (the {@code dir=b} direction).
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param limit the maximum number of events to fetch
   * @param accessToken the requesting user's Matrix access token
   * @return the {@code m.room.message} events the user can see, newest first
   */
  public List<MatrixMessage> getRoomMessages(String matrixRoomId,
                                             int limit,
                                             String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/messages?dir=b&limit="
        + limit;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while reading messages of room " + matrixRoomId);
    }
    if (response.statusCode() == 403 || response.statusCode() == 404) {
      // The user is not a member of the room or cannot see its history
      LOG.debug("User is not allowed to read messages of room {} (HTTP {})", matrixRoomId, response.statusCode());
      return new ArrayList<>();
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error retrieving messages of room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                          String.valueOf(response.statusCode()),
                                                                                                                          response.body()));
    }
    List<MatrixMessage> messages = new ArrayList<>();
    JsonValue chunk = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("chunk");
    if (chunk == null || !chunk.isArray()) {
      return messages;
    }
    Iterator<JsonValue> events = chunk.getElements();
    while (events.hasNext()) {
      MatrixMessage message = parseMessageEvent(events.next(), matrixRoomId);
      if (message != null) {
        messages.add(message);
      }
    }
    return messages;
  }

  /**
   * Parses a single Matrix timeline event into a {@link MatrixMessage}, or returns
   * {@code null} when the event is not a textual {@code m.room.message}.
   *
   * @param event the JSON event
   * @param roomLocalId the room local part the event belongs to
   * @return the parsed message, or {@code null}
   */
  private MatrixMessage parseMessageEvent(JsonValue event, String roomLocalId) {
    JsonValue typeElement = event.getElement("type");
    if (typeElement == null || !"m.room.message".equals(typeElement.getStringValue())) {
      return null;
    }
    JsonValue content = event.getElement("content");
    if (content == null || content.getElement("body") == null) {
      return null;
    }
    MatrixMessage message = new MatrixMessage();
    message.setRoomId(roomLocalId);
    message.setType(typeElement.getStringValue());
    if (event.getElement("event_id") != null) {
      message.setEventId(event.getElement("event_id").getStringValue());
    }
    if (event.getElement("sender") != null) {
      message.setSender(event.getElement("sender").getStringValue());
    }
    if (event.getElement("origin_server_ts") != null) {
      message.setTimeStamp(Long.parseLong(event.getElement("origin_server_ts").getStringValue()));
    }
    message.setMessageContent(content.getElement("body").getStringValue());
    if (content.getElement("msgtype") != null) {
      message.setMessageType(content.getElement("msgtype").getStringValue());
    }
    return message;
  }

  /**
   * Returns, for the user owning the given access token, the rooms that have
   * unread notifications together with their recent timeline messages, using a
   * lightweight {@code /sync} call. Synapse only returns rooms the user has
   * joined, so visibility is naturally enforced.
   *
   * @param accessToken the requesting user's Matrix access token
   * @param timelineLimit the maximum number of recent events to fetch per room
   * @return the user's unread rooms, never {@code null}
   */
  public List<MatrixUnreadRoom> getUnreadRooms(String accessToken,
                                               int timelineLimit) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String filter = URLEncoder.encode("{\"room\":{\"timeline\":{\"limit\":" + timelineLimit + "}}}", StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/sync?timeout=0&filter=" + filter;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while syncing unread messages");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error syncing unread messages ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                                    response.body()));
    }
    List<MatrixUnreadRoom> unreadRooms = new ArrayList<>();
    JsonValue rooms = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("rooms");
    if (rooms == null || rooms.getElement("join") == null) {
      return unreadRooms;
    }
    JsonValue joinedRooms = rooms.getElement("join");
    Iterator<String> roomIds = joinedRooms.getKeys();
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    while (roomIds.hasNext()) {
      String fullRoomId = roomIds.next();
      JsonValue room = joinedRooms.getElement(fullRoomId);
      JsonValue notifications = room.getElement("unread_notifications");
      long unreadCount = notifications != null && notifications.getElement("notification_count") != null ?
                                                                                                          notifications.getElement("notification_count")
                                                                                                                       .getLongValue() :
                                                                                                          0;
      if (unreadCount <= 0) {
        continue;
      }
      String roomLocalId = fullRoomId.contains(":" + serverName) ? fullRoomId.substring(0, fullRoomId.indexOf(":" + serverName))
                                                                 : fullRoomId;
      List<MatrixMessage> messages = new ArrayList<>();
      JsonValue timeline = room.getElement("timeline");
      if (timeline != null && timeline.getElement("events") != null && timeline.getElement("events").isArray()) {
        Iterator<JsonValue> events = timeline.getElement("events").getElements();
        while (events.hasNext()) {
          MatrixMessage message = parseMessageEvent(events.next(), roomLocalId);
          if (message != null) {
            messages.add(message);
          }
        }
      }
      unreadRooms.add(new MatrixUnreadRoom(roomLocalId, (int) unreadCount, messages));
    }
    return unreadRooms;
  }

  /**
   * Reads a room's display name from Matrix itself, <strong>as the given user</strong>:
   * its {@code m.room.name}, or — for a two-member room, i.e. a direct message — the
   * other member's display name. Used to name a conversation the platform does not
   * track, so it is shown by its real name instead of a placeholder.
   *
   * @param roomIdLocalPart the room local part (without the server name suffix)
   * @param currentUserMatrixId the requesting user's full Matrix id, used to pick the
   *          other member of a direct message
   * @param accessToken the requesting user's Matrix access token
   * @return the room display name, or {@code null} when Matrix has none either
   */
  public String getRoomDisplayName(String roomIdLocalPart, String currentUserMatrixId, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL)) || StringUtils.isBlank(roomIdLocalPart)) {
      return null;
    }
    String fullRoomId = roomIdLocalPart + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String baseUrl = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId;
    try {
      HttpResponse<String> nameResponse = sendHttpGetRequest(baseUrl + "/state/m.room.name", accessToken);
      if (nameResponse.statusCode() >= 200 && nameResponse.statusCode() < 300) {
        String name = new JSONObject(nameResponse.body()).optString("name", null);
        if (StringUtils.isNotBlank(name)) {
          return name;
        }
      }
      // No room name: a direct message is named after the person on the other side.
      HttpResponse<String> membersResponse = sendHttpGetRequest(baseUrl + "/joined_members", accessToken);
      if (membersResponse.statusCode() < 200 || membersResponse.statusCode() >= 300) {
        return null;
      }
      JSONObject joined = new JSONObject(membersResponse.body()).optJSONObject("joined");
      if (joined == null) {
        return null;
      }
      List<String> others = joined.keySet().stream().filter(id -> !id.equals(currentUserMatrixId)).toList();
      if (others.size() != 1) {
        return null;
      }
      JSONObject member = joined.optJSONObject(others.get(0));
      String displayName = member == null ? null : member.optString("display_name", null);
      return StringUtils.isNotBlank(displayName) ? displayName : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.debug("Interrupted while reading the display name of room {} from Matrix", roomIdLocalPart, e);
      return null;
    } catch (Exception e) {
      LOG.debug("Could not read the display name of room {} from Matrix", roomIdLocalPart, e);
      return null;
    }
  }

  /**
   * Runs a Matrix full-text search of message bodies <strong>as the user</strong>
   * owning the given access token, optionally scoped to a single room. Synapse
   * enforces the user's visibility, so only rooms the user can see are searched.
   * Uses the {@code /_matrix/client/v3/search} endpoint (room_events category),
   * ordered by recency.
   *
   * <p>
   * An edited message is stored by Matrix as an <strong>additional</strong>
   * {@code m.room.message} event ({@code m.relates_to.rel_type = m.replace}) whose
   * fallback body repeats the text, so Synapse returns both the original and every
   * edit as separate hits. Each edit is therefore folded back onto the event it
   * replaces, redacted events are dropped, and the hits are de-duplicated by event
   * id, so one message counts once and each hit points at an event the client
   * actually renders.
   *
   * @param query the free-text search term
   * @param roomIdLocalPart the room local part to scope the search to, or
   *          {@code null}/blank to search across all of the user's rooms
   * @param limit the maximum number of hits to return
   * @param accessToken the requesting user's Matrix access token
   * @return the matching messages, most recent first, each carrying its room id,
   *         one entry per message
   */
  public List<MatrixMessage> searchMessages(String query,
                                            String roomIdLocalPart,
                                            int limit,
                                            String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/search";

    JSONObject filter = new JSONObject().put("limit", limit);
    if (StringUtils.isNotBlank(roomIdLocalPart)) {
      String fullRoomId = roomIdLocalPart + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
      filter.put("rooms", new JSONArray().put(fullRoomId));
    }
    JSONObject roomEvents = new JSONObject().put("search_term", query).put("order_by", "recent").put("filter", filter);
    String payload = new JSONObject().put("search_categories", new JSONObject().put("room_events", roomEvents)).toString();

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while searching messages");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException("Error searching messages ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                              response.body()));
    }
    Map<String, MatrixMessage> messagesByEventId = new LinkedHashMap<>();
    Set<String> editedAwayEventIds = new HashSet<>();
    JsonValue searchCategories = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("search_categories");
    if (searchCategories == null || searchCategories.getElement("room_events") == null) {
      return new ArrayList<>();
    }
    JsonValue results = searchCategories.getElement("room_events").getElement("results");
    if (results == null || !results.isArray()) {
      return new ArrayList<>();
    }
    String serverSuffix = ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    Iterator<JsonValue> hits = results.getElements();
    while (hits.hasNext()) {
      collectSearchHit(hits.next().getElement("result"), serverSuffix, query, messagesByEventId, editedAwayEventIds);
    }
    return new ArrayList<>(messagesByEventId.values());
  }

  /**
   * Adds a single search hit to the collected results, folding edits onto the message
   * they replace and leaving out what must not be reported: redacted events, non
   * textual events and messages whose edited text no longer contains the searched
   * term.
   *
   * @param event the JSON event of the hit, may be {@code null}
   * @param serverSuffix the {@code :server.name} suffix to strip from the room id
   * @param query the free-text search term the user looked for
   * @param messagesByEventId the collected messages, keyed by the event the client
   *          renders, updated in place
   * @param editedAwayEventIds the ids of messages edited to no longer contain the
   *          term, updated in place so their outdated original is skipped too
   */
  private void collectSearchHit(JsonValue event,
                                String serverSuffix,
                                String query,
                                Map<String, MatrixMessage> messagesByEventId,
                                Set<String> editedAwayEventIds) {
    if (event == null || isRedacted(event)) {
      return;
    }
    MatrixMessage message = parseMessageEvent(event, extractRoomLocalId(event, serverSuffix));
    if (message == null) {
      return;
    }
    boolean stillMatches = applyEditRelation(event, message, query);
    String eventId = message.getEventId();
    if (eventId == null) {
      return;
    }
    if (!stillMatches) {
      // Edited to remove the term: drop the edit and the original event it replaces, which
      // Synapse still matches on its outdated body.
      editedAwayEventIds.add(eventId);
      messagesByEventId.remove(eventId);
      return;
    }
    if (!editedAwayEventIds.contains(eventId)) {
      // Hits come most recent first: an edit is seen before the event it replaces, and the
      // edited text is what the client renders, so the first entry for an id wins.
      messagesByEventId.putIfAbsent(eventId, message);
    }
  }

  /**
   * Extracts the room local part from a search hit, i.e. the room id without its
   * {@code :server.name} suffix.
   *
   * @param event the JSON event of the hit
   * @param serverSuffix the {@code :server.name} suffix to strip
   * @return the room local part, or {@code null} when the event carries no room id
   */
  private String extractRoomLocalId(JsonValue event, String serverSuffix) {
    if (event.getElement("room_id") == null) {
      return null;
    }
    String fullRoomId = event.getElement("room_id").getStringValue();
    return fullRoomId.contains(serverSuffix) ? fullRoomId.substring(0, fullRoomId.indexOf(serverSuffix)) : fullRoomId;
  }

  /**
   * Tells whether a Matrix event has been redacted (deleted). Synapse keeps redacted
   * events in its full-text index, so a deleted message would otherwise still be
   * returned — and shown — as a search hit.
   *
   * @param event the JSON event returned by the search
   * @return {@code true} when the event carries a redaction marker
   */
  private boolean isRedacted(JsonValue event) {
    JsonValue unsigned = event.getElement("unsigned");
    return unsigned != null && unsigned.getElement("redacted_because") != null;
  }

  /**
   * Folds a search hit that is an <em>edit</em> back onto the message it replaces:
   * the hit is re-pointed at the original event id (the one the client renders and
   * can scroll to) and its text is taken from {@code m.new_content} so the result
   * shows the current wording rather than the {@code * } fallback body. Hits that
   * are not edits are left untouched.
   * <p>
   * @param event the JSON event returned by the search
   * @param message the message parsed from that event, updated in place
   * @param query the free-text search term the user looked for
   * @return {@code false} when the hit is an edit whose new text no longer contains
   *         the searched term — the message must then stop being reported as a
   *         match; {@code true} for any other hit
   */
  private boolean applyEditRelation(JsonValue event, MatrixMessage message, String query) {
    JsonValue content = event.getElement("content");
    JsonValue relatesTo = content == null ? null : content.getElement("m.relates_to");
    JsonValue relationType = relatesTo == null ? null : relatesTo.getElement("rel_type");
    if (relationType == null || !"m.replace".equals(relationType.getStringValue())) {
      return true;
    }
    JsonValue replacedEventId = relatesTo.getElement("event_id");
    if (replacedEventId != null) {
      message.setEventId(replacedEventId.getStringValue());
    }
    JsonValue newContent = content.getElement("m.new_content");
    JsonValue newBody = newContent == null ? null : newContent.getElement("body");
    if (newBody != null) {
      message.setMessageContent(newBody.getStringValue());
    } else if (StringUtils.startsWith(message.getMessageContent(), EDIT_FALLBACK_PREFIX)) {
      // No m.new_content (older clients): strip the "* " fallback marker Matrix prepends.
      message.setMessageContent(message.getMessageContent().substring(EDIT_FALLBACK_PREFIX.length()));
    }
    String currentBody = message.getMessageContent();
    return StringUtils.isBlank(query) || StringUtils.isBlank(currentBody)
           || StringUtils.containsIgnoreCase(currentBody, query.trim());
  }

  /**
   * Sends a textual message to a room as the user owning the given access token,
   * using the Matrix client API. Synapse enforces that the user is allowed to send
   * to the room.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param text the plain text message body
   * @param transactionId a unique client transaction id (for idempotency)
   * @param accessToken the requesting user's Matrix access token
   * @return the created event id
   */
  public String sendMessage(String matrixRoomId,
                            String text,
                            String transactionId,
                            String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId
        + "/send/m.room.message/" + transactionId;
    String payload = new JSONObject().put("msgtype", "m.text").put("body", text).toString();

    HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while sending a message to room " + matrixRoomId);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error sending a message to room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                        String.valueOf(response.statusCode()),
                                                                                                                        response.body()));
    }
    return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("event_id").getStringValue();
  }

  /**
   * Posts an {@code m.read} receipt on behalf of a user: a Matrix read
   * receipt is a high-water mark, everything up to the event becomes read.
   *
   * @param matrixRoomId the room local id
   * @param eventId the event read up to
   * @param accessToken the user's Matrix access token
   */
  public void sendReadReceipt(String matrixRoomId, String eventId, String accessToken) throws IOException,
                                                                                       InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId.contains(":") ? matrixRoomId
                                                   : matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/receipt/m.read/" + eventId;
    // the web client sends the same threaded receipt: its sync handler resets
    // the room badge only when thread_id is present
    String payload = new JSONObject().put("thread_id", "main").toString();
    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while sending a read receipt to room " + matrixRoomId);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error sending a read receipt to room %s, Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                             String.valueOf(response.statusCode()),
                                                                                                                             response.body()));
    }
  }

  /**
   * Invalidates an access token on Matrix server
   *
   * @param accessToken
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public boolean invalidateAccessToken(String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/logout";

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, "");
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return true;
    } else {
      throw new RuntimeException("Error invalidating access token ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                                      response.body()));
    }
  }

  public Optional<MediaInfo> getMediaInfo(String mediaId, String accessToken) throws IOException, InterruptedException {
    String serverUrl = PropertyManager.getProperty(MATRIX_SERVER_URL);
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = String.format("%s/_synapse/admin/v1/media/%s/%s", serverUrl, serverName, mediaId);
    HttpResponse<String> response = HTTPHelper.sendHttpGetRequest(url, accessToken);

    if (response.statusCode() == 200) {
        JSONObject json = new JSONObject(response.body());
        JSONObject mediaInfoJson = json.getJSONObject("media_info");

        MediaInfo info = new MediaInfo();
        info.setMediaId(mediaInfoJson.getString("media_id"));
        info.setServerName(serverName);
        info.setOwner(mediaInfoJson.optString("user_id", null));
        info.setFilename(mediaInfoJson.optString("upload_name", mediaId));
        info.setContentType(mediaInfoJson.optString("media_type", null));
        info.setContentLength(mediaInfoJson.has("media_length") ? mediaInfoJson.getLong("media_length") : null);
        info.setCreatedTs(mediaInfoJson.has("created_ts") ? mediaInfoJson.getLong("created_ts") : null);

        return Optional.of(info);
    }
    return Optional.empty();
  }

  public HttpResponse<String> deleteMedia(String mediaId, String accessToken) throws IOException, InterruptedException {
    String serverUrl = PropertyManager.getProperty(MATRIX_SERVER_URL);
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = String.format("%s/_synapse/admin/v1/media/%s/%s", serverUrl, serverName, mediaId);
    return HTTPHelper.sendHttpDeleteRequest(url, accessToken, "");
  }
}
