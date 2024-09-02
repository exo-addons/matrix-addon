package org.exoplatform.addons.matrix.services;

public class MatrixConstants {

  private MatrixConstants() {
  }

  public static final String MATRIX_SERVER_URL             = "exo.matrix.server.url";

  public static final String MATRIX_METADATA_TYPE          = "matrixSpaceIntegration";

  public static final String MATRIX_METADATA_NAME          = "MatrixTeamMetadata";

  public static final String MATRIX_ROOM_ID                = "MatrixRoomId";

  public static final String MATRIX_SERVER_URL_IS_REQUIRED =
                                                           "The URL of the Matrix server is required, please provide it using System properties !";

  public static final String MATRIX_ACCESS_TOKEN           = "exo.matrix.access_token";

  public static final String BEARER                        = "Bearer ";

  public static final String AUTHORIZATION                 = "Authorization";

  public static final String CONTENT_TYPE                  = "Content-type";

  public static final String SERVER_NAME                   = "exo.matrix.server.name";

  public static final String SHARED_SECRET_REGISTRATION    = "exo.matrix.shared_secret_registration";

  public static final String USER_MATRIX_ID                = "matrixId";

  public static final String MESSAGE_USER_JOINED_SPACE     = "You are invited to join the room linked to the space %s !";

  public static final String MESSAGE_USER_KICKED_SPACE     =
                                                       "You are no more member of the space %s, thus you were kicked out of this room!";

  // User roles on Matrix
  public static final String ADMIN_ROLE                    = "100";

  public static final String MODERATOR_ROLE                = "50";

  public static final String SIMPLE_USER_ROLE              = "0";

}
