package org.exoplatform.addons.matrix.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.picocontainer.Startable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.exoplatform.addons.matrix.services.MatrixConstants.*;

public class MatrixService implements Startable {

  private static final Log LOG = ExoLogger.getLogger(MatrixService.class);

  private final MetadataService  metadataService;

  private final IdentityManager  identityManager;

  private String                 matrixAccessToken;

  public MatrixService(MetadataService metadataService, IdentityManager identityManager) {
    this.identityManager = identityManager;
    this.metadataService = metadataService;
  }

  public String getMatrixAccessToken() {
    return matrixAccessToken;
  }

  @Override
  public void start() {
    try {
      String jwtAccessToken = this.getJWTSessionToken(MATRIX_ADMIN_USERNAME);
      matrixAccessToken = MatrixHttpClient.getAdminAccessToken(jwtAccessToken);
    } catch (JsonException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the ID of the room linked to a space from the Metadata of the space
   * @param space
   * @return the roomId linked to the space
   * @throws ObjectNotFoundException
   * @throws IllegalStateException
   */
  public String getRoomBySpace(Space space) throws ObjectNotFoundException, IllegalStateException {
    MetadataKey metadataKey = new MetadataKey(MATRIX_METADATA_TYPE, MATRIX_METADATA_NAME, 0);
    MetadataObject metadataObject = new MetadataObject("Space", space.getId(), null, Long.parseLong(space.getId()));
    List<MetadataItem> metadataItems = metadataService.getMetadataItemsByMetadataTypeAndObject(metadataKey.getType(), metadataObject);
    if(metadataItems.size() > 1) {
      LOG.error("There are more than one items for type {} and space {}", metadataKey.getType(), space.getPrettyName());
      throw new IllegalStateException("There are more than one metadata for the space" + space.getPrettyName());
    } else if (!metadataItems.isEmpty()) {
      MetadataItem metadataItem = metadataItems.get(0);
      if (metadataItem != null) {
        return metadataItem.getProperties().get(MATRIX_ROOM_ID);
      } else {
        throw new IllegalStateException("No metadata items for space " + space.getPrettyName());
      }
    } else {
      throw new ObjectNotFoundException("No metadata items for space " + space.getPrettyName());
    }
  }

  /**
   * Adds metadata for the space including the matrix ID of the room linked top the space
   * @param space the Space
   * @param roomId the ID of the matrix room
   * @return the room ID if no exception is thrown
   * @throws ObjectAlreadyExistsException
   */
  public String addMatrixMetadata(Space space, String roomId) throws ObjectAlreadyExistsException {
    MetadataKey metadataKey = new MetadataKey(MATRIX_METADATA_TYPE, MATRIX_METADATA_NAME, 0);
    MetadataObject metadataObject = new MetadataObject("Space", space.getId(), null, Long.parseLong(space.getId()));

    // check if there is already a team linked to that space
    List<MetadataItem> items = metadataService.getMetadataItemsByMetadataAndObject(metadataKey, metadataObject);
    if (!items.isEmpty()) {
      metadataService.deleteMetadataItemsByMetadataTypeAndObject(metadataKey.getType(), metadataObject);
    }

    if (!StringUtils.isBlank(roomId)) {
      Map<String, String> properties = new HashMap<>();
      properties.put(MATRIX_ROOM_ID, roomId);
      metadataService.createMetadataItem(metadataObject, metadataKey, properties);
    }
    return roomId;
  }

  /**
   * Creates a room for predefined space
   * @param space the space
   * @return String representing the room id
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String createMatrixRoomForSpace(Space space) throws JsonException, IOException, InterruptedException {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    return MatrixHttpClient.createRoom(teamDisplayName, description, getMatrixAccessToken());
  }

  /**
   * Get the matrix ID of a defined user
   * @param userName of the user
   * @return the matrix ID
   */
  public String getMatrixIdForUser(String userName) {
    Identity newMember = identityManager.getOrCreateUserIdentity(userName);
    Profile newMemberProfile = newMember.getProfile();
    if(StringUtils.isNotBlank((String) newMemberProfile.getProperty(USER_MATRIX_ID))) {
      return newMemberProfile.getProperty(USER_MATRIX_ID).toString();
    }
    return null;
  }

  /**
   * Returns the JWT for user authentication
   * @param userName the username of the current user
   * @return String
   */
  public String getJWTSessionToken(String userName) {
    Identity identity = this.identityManager.getOrCreateUserIdentity(userName);
    return Jwts.builder()
            .setSubject((String)identity.getProfile().getProperty(MatrixConstants.USER_MATRIX_ID))
            .signWith(Keys.hmacShaKeyFor(PropertyManager.getProperty(MatrixConstants.MATRIX_JWT_SECRET).getBytes()))
            .setExpiration(Date.from(LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()))
            .compact();

  }
}
