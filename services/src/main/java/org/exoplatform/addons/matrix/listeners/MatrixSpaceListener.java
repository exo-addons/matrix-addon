package org.exoplatform.addons.matrix.listeners;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;

import java.util.*;

public class MatrixSpaceListener extends SpaceListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(MatrixSpaceListener.class);

  private MatrixService matrixService;

  public MatrixSpaceListener(MatrixService matrixService) {
    this.matrixService = matrixService;
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    try {
      String teamDisplayName = space.getDisplayName();
      String matrixRoomId = MatrixHttpClient.createRoom(teamDisplayName);

      if (StringUtils.isNotBlank(matrixRoomId)) {
        matrixService.addMatrixMetadata(space, matrixRoomId);
        LinkedHashSet<String> allMembers = new LinkedHashSet<>();
        allMembers.addAll(Arrays.asList(space.getManagers()));
        allMembers.addAll(Arrays.asList(space.getMembers()));
        MatrixHttpClient.sendInvitationToMembers(new ArrayList<>(allMembers), matrixRoomId);
      }
    } catch (Exception e) {
      LOG.error("Mattermost integration: Could not create a team for space {}", space.getDisplayName(), e);
    }
  }

}
