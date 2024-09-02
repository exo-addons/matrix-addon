package org.exoplatform.addons.matrix.listeners;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;
import org.exoplatform.social.core.storage.api.IdentityStorage;


public class IdentityListener extends ProfileListenerPlugin {

  private IdentityStorage identityStorage;

  public IdentityListener(IdentityStorage identityStorage) {
    this.identityStorage = identityStorage;
  }

  @Override
  public void avatarUpdated(ProfileLifeCycleEvent event) {
    Profile profile = event.getProfile();
    FileItem avatarFileItem = identityStorage.getAvatarFile(profile.getIdentity());
    String mimeType = "image/jpg";
    if (avatarFileItem != null && avatarFileItem.getFileInfo() != null) {
      if(!"application/octet-stream".equals(avatarFileItem.getFileInfo().getMimetype())) {
        mimeType = avatarFileItem.getFileInfo().getMimetype();
      }
      String userAvatarUrl = MatrixHttpClient.uploadFile("avatar-of-" + event.getUsername(), mimeType, avatarFileItem.getAsByte());
      String userMatrixID = (String) profile.getProperty(MatrixConstants.USER_MATRIX_ID);
      if(StringUtils.isNotBlank(userMatrixID) && StringUtils.isNotBlank(userAvatarUrl)) {
        MatrixHttpClient.updateUserAvatar(userMatrixID, userAvatarUrl);
      }
    }
  }
}
