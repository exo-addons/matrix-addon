import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.idm.UserImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

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
    MatrixHttpClient.saveUserAccount(user);
  }

  @Test
  public void testDisableUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 10000));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test" + randomKey + "@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    String username = MatrixHttpClient.saveUserAccount(user);
    MatrixHttpClient.disableAccount(username, false);
  }

  @Test
  public void testRenameSpace() {
    String roomId = null;
    try {
      roomId = MatrixHttpClient.createRoom("new room");
    } catch (JsonException | IOException | InterruptedException e) {
      
    }
    String eventId = MatrixHttpClient.renameRoom(roomId, "new room renamed" + new Date().getTime());
  }
}
