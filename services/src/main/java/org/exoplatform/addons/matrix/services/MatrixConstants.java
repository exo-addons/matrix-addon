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

  public static final String SERVER_NAME                   = "exo.matrix.server.name";

  public static final String SHARED_SECRET_REGISTRATION    = "exo.matrix.shared_secret_registration";
}
