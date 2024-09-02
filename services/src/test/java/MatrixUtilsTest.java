import org.exoplatform.addons.matrix.model.MatrixRoomPermissions;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.idm.UserImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * This test class requires an available mattermost server
 * You can configure the connection inside the setUp function
 * with the server using the System properties : - exo.addon.mattermost.userName
 *                                               - exo.addon.mattermost.password
 *                                               - exo.addon.mattermost.url
 */

public class MatrixUtilsTest {

  @Before
  public void setUp() {
    System.setProperty("exo.matrix.access_token", "syt_cm9vdA_HxkqWHDpGLbuvKoCYucM_1WlWyy");
    System.setProperty("exo.matrix.server.url", "http://localhost:8008");
    System.setProperty("exo.matrix.shared_secret_registration", "4fzT.7xvkyp1EA-*bX#fzpVgOc_cb0y9z6*uOCUht1DO5ksad8");
    System.setProperty("exo.matrix.server.name", "matrix.exo.tn");
  }

  @Test
  public void testCreateUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 100));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    MatrixHttpClient.createUserAccount(user);
  }

  @Test
  public void testSaveUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 100));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    MatrixHttpClient.saveUserAccount(user, true);
  }

  @Test
  public void testDisableUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 10000));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test" + randomKey + "@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    String username = MatrixHttpClient.saveUserAccount(user, true);
    MatrixHttpClient.disableAccount(username, false);
  }

  public void testRenameSpace() {
    String roomId = null;
    try {
      roomId = MatrixHttpClient.createRoom("new room", "new room description");
    } catch (JsonException | IOException | InterruptedException e) {

    }
    String eventId = MatrixHttpClient.renameRoom(roomId, "new room renamed" + new Date().getTime());
  }

  public void testInviteUser() throws JsonException, IOException, InterruptedException {
    String randomKey = String.valueOf(Math.round(Math.random() * 10000));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test" + randomKey + "@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    String invitee = MatrixHttpClient.saveUserAccount(user, true);
    String roomId = MatrixHttpClient.createRoom("Football game", "Description of Football team");
    MatrixHttpClient.inviteUserToRoom(roomId, invitee, "Welcome to Football game room !");
  }

  /*
  This test requires that we have already a user member of a room to kick him out
   */
  public void testKickUser() {
    String roomId = "!rYdqPkQhIzNWyVPDFX";
    MatrixHttpClient.kickUserFromRoom(roomId, "@testuser1:matrix.exo.tn", "Talking too much!");
  }

  public void updateRoomSettings() {
    String roomId = "!rYdqPkQhIzNWyVPDFX";
    MatrixRoomPermissions settings = MatrixHttpClient.getRoomSettings(roomId);
    settings.setInvite("0");
    String updateEventId = MatrixHttpClient.updateRoomSettings(roomId, settings);
  }

  public void updateRoomAvatar() {
    try {
      String roomId = "!HaTqHwWINwoSoIGfZx";
      byte[] resource = getClass().getClassLoader().getResourceAsStream("meeds.png").readAllBytes();
      String imageStored = MatrixHttpClient.uploadFile("image.png", "image/png", resource);
      boolean success = MatrixHttpClient.updateRoomAvatar(roomId, imageStored);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public void updateUserAvatar() {
    try {
      String roomId = "@root:matrix.exo.tn";
      int randomInt = new Random().nextInt(3);
      byte[] resource = getClass().getClassLoader().getResourceAsStream("avatar" + randomInt + ".png").readAllBytes();
      String imageStored = MatrixHttpClient.uploadFile("image.png", "image/png", resource);
      boolean success = MatrixHttpClient.updateUserAvatar(roomId, imageStored);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
