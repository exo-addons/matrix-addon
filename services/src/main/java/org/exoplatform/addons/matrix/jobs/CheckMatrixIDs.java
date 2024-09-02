package org.exoplatform.addons.matrix.jobs;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixHttpClient;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.exoplatform.addons.matrix.services.MatrixConstants.USER_MATRIX_ID;

@DisallowConcurrentExecution
public class CheckMatrixIDs implements Job {
  private static final Log LOG                = ExoLogger.getLogger(CheckMatrixIDs.class);

  private static final int LOADED_USERS_COUNT = 3;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    LOG.info("Start Checking users fro their Matrix IDs");
    IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
    OrganizationService organizationService = CommonsUtils.getService(OrganizationService.class);

    int checkedUsers = 0;

    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      ListAccess<User> users = organizationService.getUserHandler().findAllUsers();
      int usersCount = users.getSize();
      while(checkedUsers < usersCount) {
        int usersToCheck = usersCount > checkedUsers + LOADED_USERS_COUNT ? LOADED_USERS_COUNT : (usersCount - checkedUsers);
        User[] usersArray = users.load(checkedUsers, usersToCheck);
        for (User user : usersArray) {
          Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getUserName());
          Profile userProfile = userIdentity.getProfile();
          String userMatrixId = (String) userProfile.getProperty(MatrixConstants.USER_MATRIX_ID);
          if(StringUtils.isBlank(userMatrixId)) {
            String matrixId = MatrixHttpClient.saveUserAccount(user, false);
            userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
            identityManager.updateProfile(userProfile);
          }
        }
        checkedUsers += usersArray.length;
        LOG.info("Checked {} of {} user profiles for Matrix id", checkedUsers, usersCount);
      }
    } catch (Exception e) {
      LOG.error("Error while loading user identities", e);
    } finally {
      RequestLifeCycle.end();
    }
    if(checkedUsers > 0) {
      LOG.info("End Checking users fro their Matrix IDs : {} profiles were checked", checkedUsers);
    } else {
      LOG.info("End Checking users fro their Matrix IDs : All users have already Matrix IDs");
    }
  }
}
