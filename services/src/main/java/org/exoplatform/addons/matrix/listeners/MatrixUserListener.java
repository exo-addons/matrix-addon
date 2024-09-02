package org.exoplatform.addons.matrix.listeners;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;

import static org.exoplatform.addons.matrix.services.MatrixConstants.USER_MATRIX_ID;

public class MatrixUserListener extends UserEventListener {

  private IdentityManager identityManager;

  public MatrixUserListener(IdentityManager identityManager) {
    this.identityManager = identityManager;
  }

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    if(identityManager != null) {
      Profile userProfile = identityManager.getProfile(identityManager.getOrCreateUserIdentity(user.getUserName()));
      String matrixID = MatrixHttpClient.saveUserAccount(user, isNew);
      if(userProfile.getProperty(USER_MATRIX_ID) == null || StringUtils.isBlank(userProfile.getProperty(USER_MATRIX_ID).toString())) {
        userProfile.getProperties().put(USER_MATRIX_ID, matrixID);
        identityManager.updateProfile(userProfile);
      }
    }
  }

  @Override
  public void postSetEnabled(User user) throws Exception {
    String matrixUsername = "@" + user.getUserName() + ":" + PropertyManager.getProperty(MatrixConstants.SERVER_NAME);
     MatrixHttpClient.disableAccount(matrixUsername, false);
  }


}
