package org.exoplatform.addons.matrix.listeners;

import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;

public class MatrixUserListener extends UserEventListener {

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    if(isNew) {
      MatrixHttpClient.saveUserAccount(user);
    }
  }

  @Override
  public void postSetEnabled(User user) throws Exception {
    String matrixUsername = "@" + user.getUserName() + ":" + PropertyManager.getProperty(MatrixConstants.SERVER_NAME);
     MatrixHttpClient.disableAccount(matrixUsername, false);
  }
}
