package org.exoplatform.addons.matrix.services;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

public class MatrixHttpClient {
  private static final Log    LOG                           = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  public static final String  MATRIX_SERVER_URL             = "exo.matrix.server.url";

  private static final String MATRIX_SERVER_URL_IS_REQUIRED =
                                                            "The URL of the Matrix server is required, please provide it using System properties !";

  private static final String MATRIX_ACCESS_TOKEN           = "exo.matrix.access_token";

  private static final String BEARER                        = "Bearer ";

  private static final String AUTHORIZATION                 = "Authorization";

  private static final String SERVER_NAME                   = "exo.matrix.server.name";

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
        LOG.error("Error creating a team, Mattermost server returned HTTP {} error {}", response.statusCode(), response.body());
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
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER + token)
            .POST(HttpRequest.BodyPublishers.ofString(contentAsJson))
            .build();
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
}
