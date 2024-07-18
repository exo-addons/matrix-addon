package org.exoplatform.addons.matrix.services;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.ws.frameworks.json.impl.JsonException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.exoplatform.addons.matrix.services.MatrixConstants.*;

public class MatrixService {

  private static final Log LOG = ExoLogger.getLogger(MatrixService.class);

  private MetadataService metadataService;

  public MatrixService(MetadataService metadataService) {
    this.metadataService = metadataService;
  }

  public String getRomBySpace(Space space) throws ObjectNotFoundException, IllegalStateException {
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

  public String createMatrixRoomForSpace(Space space) throws JsonException, IOException, InterruptedException {
    String teamDisplayName = space.getDisplayName();
    return MatrixHttpClient.createRoom(teamDisplayName);

  }
}
