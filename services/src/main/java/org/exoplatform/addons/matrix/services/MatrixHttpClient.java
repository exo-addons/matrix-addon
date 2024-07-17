package org.exoplatform.addons.matrix.services;

import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MatrixHttpClient {
  private static final Log    LOG                           = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  public static final String  MATRIX_SERVER_URL             = "exo.matrix.server.url";

  private static final String MATRIX_SERVER_URL_IS_REQUIRED =
                                                            "The URL of the Matrix server is required, please provide it using System properties !";

  private static final String MATRIX_ACCESS_TOKEN           = "exo.matrix.access_token";

  private static final String BEARER                        = "Bearer ";

  private static final String AUTHORIZATION                 = "Authorization";

  public static final String SERVER_NAME                   = "exo.matrix.server.name";
  private static final String SHARED_SECRET_REGISTRATION = "exo.matrix.shared_secret_registration";

  private MatrixHttpClient() {
  }
  

  public static String createRoom(String name) throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/createRoom";

    String payload = """
        {
          "name": "%s",
          "preset": "private_chat",
          "visibility": "private",
          "initial_state": [
            {
              "type": "m.room.guest_access",
              "state_key": "",
              "content": {
                "guest_access": "can_join"
              }
            },
            {
              "type": "m.room.encryption",
              "state_key": "",
              "content": {
                "algorithm": "m.megolm.v1.aes-sha2"
              }
            }
          ]
        }
        """.formatted(name);

    try {
      String token = PropertyManager.getProperty(MATRIX_ACCESS_TOKEN);
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if(response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        String roomId = jsonGenerator.createJsonObjectFromString(response.body()).getElement("room_id").getStringValue();
        return roomId.substring(0, roomId.indexOf(":" + PropertyManager.getProperty(SERVER_NAME)));
      } else {
        LOG.error("Error creating a team, Matrix server returned HTTP {} error {}", String.valueOf(response.statusCode()), response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a team on Mattermost", e);
      throw e;
    }
  }




  protected static HttpResponse<String> sendHttpGetRequest(String url, String token) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER + token)
            .GET()
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
  protected static HttpResponse<String> sendHttpPostRequest(String url, String token, String contentAsJson) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request;
    if(StringUtils.isNotBlank(token)) {
      request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header(AUTHORIZATION, BEARER + token)
              .POST(HttpRequest.BodyPublishers.ofString(contentAsJson))
              .build();
    } else {
      request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .POST(HttpRequest.BodyPublishers.ofString(contentAsJson))
              .build();
    }
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPutRequest(String url, String token, String contentAsJson) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER + token)
            .PUT(HttpRequest.BodyPublishers.ofString(contentAsJson))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  public static void sendInvitationToMembers(ArrayList<String> strings, String matrixRoomId) {

  }

  public static String createUserAccount(User user) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String nonce = getRegistrationNonce();
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    String hmac = hmacUserProperties(nonce, user.getUserName(), user.getUserName(), false);

    String payload = """
        {
           "nonce": "%s",
           "username": "%s",PUT /_synapse/admin/v2/users/<user_id>
           "displayname": "%s",
           "password": "%s",
           "admin": false,
           "mac": "%s"
          }
        """.formatted(nonce, user.getUserName(), user.getDisplayName(), user.getUserName(), hmac);

    try {
      String token = PropertyManager.getProperty(MATRIX_ACCESS_TOKEN);
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if(response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        return userAccount.getElement("user_id").getStringValue();
      } else {
        LOG.error("Error creating a user account, Matrix server returned HTTP {} error {}", String.valueOf(response.statusCode()), response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a user account on Matrix", e);
      return null;
    }
  }

  public static String saveUserAccount(User user) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + "@" + user.getUserName().toLowerCase() + ":" + PropertyManager.getProperty(SERVER_NAME);

    String payload = """
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
         "admin": false,
         "deactivated": %s,
         "user_type": null,
         "locked": false
       }
        """.formatted(user.getUserName().toLowerCase(), user.getDisplayName(), user.getEmail(), String.valueOf(user.isEnabled()));

    try {
      String token = PropertyManager.getProperty(MATRIX_ACCESS_TOKEN);
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if(response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        return userAccount.getElement("name").getStringValue();
      } else {
        LOG.error("Error creating a user account, Matrix server returned HTTP {} error {}", String.valueOf(response.statusCode()), response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a user account on Matrix", e);
      return null;
    }
  }


  private static String hmacUserProperties(String nonce, String userName, String password, boolean isAdmin) {
    String userProperties = nonce + "\0" + userName + "\0" + password + "\0" + (isAdmin? "admin" : "notadmin");
    return HmacUtils.hmacSha1Hex(PropertyManager.getProperty(SHARED_SECRET_REGISTRATION), userProperties);
  }

  private static String getRegistrationNonce() {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    try {
      String token = PropertyManager.getProperty(MATRIX_ACCESS_TOKEN);
      HttpResponse<String> response = sendHttpGetRequest(url, token);
      if(response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("nonce").getStringValue();
      } else {
        LOG.error("Error getting Nonce, Matrix server returned HTTP {} error {}", String.valueOf(response.statusCode()), response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not get the nonce on Matrix", e);
      return null;
    }
  }


}
