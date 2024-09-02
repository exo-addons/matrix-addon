package org.exoplatform.addons.matrix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatrixUserPermission {
  private String userName;
  private String userRole;
}
