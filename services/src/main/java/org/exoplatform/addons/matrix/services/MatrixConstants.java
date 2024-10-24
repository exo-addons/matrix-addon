package org.exoplatform.addons.matrix.services;

public class MatrixConstants {

  private MatrixConstants() {
  }

  // Meeds server configurations

  public static final String MATRIX_SERVER_URL             = "meeds.matrix.server.url";

  public static final String MATRIX_ADMIN_USERNAME         = "meeds.matrix.user.name";

  public static final String SERVER_NAME                   = "meeds.matrix.server.name";

  public static final String SHARED_SECRET_REGISTRATION    = "meeds.matrix.shared_secret_registration";

  public static final String MATRIX_METADATA_TYPE          = "matrixSpaceIntegration";

  public static final String MATRIX_METADATA_NAME          = "MatrixTeamMetadata";

  public static final String MATRIX_ROOM_ID                = "MatrixRoomId";

  public static final String MATRIX_SERVER_URL_IS_REQUIRED =
                                                           "The URL of the Matrix server is required, please provide it using System properties !";

  public static final String MATRIX_ADMIN_USERNAME_IS_REQUIRED =
                                                           "The username of the admin the Matrix server is required, please provide it using System properties !";

  public static final String BEARER                        = "Bearer ";

  public static final String AUTHORIZATION                 = "Authorization";

  public static final String MATRIX_JWT_SECRET             = "meeds.matrix.jwt.secret";

  public static final String MATRIX_JWT_COOKIE             = "matrix_jwt_token";

  public static final String CONTENT_TYPE                  = "Content-type";

  public static final String USER_MATRIX_ID                = "matrixId";

  public static final String MESSAGE_USER_JOINED_SPACE     = "You are invited to join the room linked to the space %s !";

  public static final String MESSAGE_USER_KICKED_SPACE     =
                                                       "You are no more member of the space %s, thus you were kicked out of this room!";

  // User roles on Matrix
  public static final String ADMIN_ROLE                    = "100";

  public static final String SIMPLE_USER_ROLE              = "0";

}
