package org.exoplatform.addons.matrix.listeners;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

import static org.exoplatform.addons.matrix.services.MatrixConstants.USER_MATRIX_ID;

@Asynchronous
public class MatrixUserLoginListener extends Listener<ConversationRegistry, ConversationState> {

  private static final Log LOG = ExoLogger.getLogger(MatrixUserLoginListener.class);

  private IdentityManager identityManager;

  private MatrixService matrixService;

  private OrganizationService organizationService;
  public MatrixUserLoginListener(IdentityManager identityManager, OrganizationService organizationService) {
    this.identityManager = identityManager;
    this.organizationService = organizationService;
  }

  public void onEvent(Event<ConversationRegistry, ConversationState> event) {
    RequestLifeCycle.begin(PortalContainer.getInstance());
    String userId = event.getData().getIdentity().getUserId();
    try {
      if(identityManager != null) {
        Profile userProfile = identityManager.getProfile(identityManager.getOrCreateUserIdentity(userId));
        String matrixUserId = (String) userProfile.getProperty(USER_MATRIX_ID);
        if(StringUtils.isBlank(matrixUserId)) {
          User user = organizationService.getUserHandler().findUserByName(userId);
          String matrixId = MatrixHttpClient.saveUserAccount(user, false, matrixService.getMatrixAccessToken());
          userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
          identityManager.updateProfile(userProfile);
        }
      }
    } catch (Exception e) {
      LOG.error("Could not add matrix information for user {}", userId, e);
    } finally {
      RequestLifeCycle.end();
    }
  }
}
