import org.junit.Before;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * This test class requires an available mattermost server
 * You can configure the connection inside the setUp function
 * with the server using the System properties : - exo.addon.mattermost.userName
 *                                               - exo.addon.mattermost.password
 *                                               - exo.addon.mattermost.url
 */

public class MattermostUtilsTest {

  @Before
  public void setUp() {
    System.setProperty("exo.addon.mattermost.userName", "admin");
    System.setProperty("exo.addon.mattermost.password", "Pass@123456");
    System.setProperty("exo.addon.mattermost.url", "http://localhost:8065");
  }

  
}
