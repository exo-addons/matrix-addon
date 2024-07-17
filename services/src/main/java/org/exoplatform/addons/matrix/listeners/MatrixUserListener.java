package org.exoplatform.addons.matrix.listeners;

import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;

public class MatrixUserListener extends UserEventListener {

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    if(isNew) {
      MatrixHttpClient.createUserAccount(user);
    }
  }
}
