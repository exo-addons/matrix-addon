package org.exoplatform.addons.matrix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatrixRoomPermissions {
  private List<MatrixUserPermission> users;
  private String userDefault;
  private Events events;
  private String eventsDefault;
  private String stateDefault;
  private String ban;
  private String kick;
  private String redact;
  private String invite;
  private String historical;

  public String toJson() {
    StringBuilder usersString = new StringBuilder();
    for(int index = 0; index < users.size(); index ++) {
      usersString.append("\"") .append(this.users.get(index).getUserName()).append("\"").append(": ").append(this.users.get(index).getUserRole());
      if(index < users.size() - 1) {
        usersString.append(",");
      }
    }
    return """
              {
                  "users": {
                      %s
                  },
                  "users_default": %s,
                  "events": %s,
                  "events_default": %s,
                  "state_default": %s,
                  "ban": %s,
                  "kick": %s,
                  "redact": %s,
                  "invite": %s,
                  "historical": %s
              }
              """.formatted(usersString.toString(), this.userDefault, this.events.toJson(), this.eventsDefault, this.stateDefault, this.ban, this.kick, this.redact, this.invite, this.historical);
  }
  public static MatrixRoomPermissions fromJson(JsonValue jsonValue) {
    List<MatrixUserPermission> matrixUserPermissions = new ArrayList<>();
    Iterator<String> usersIterator = jsonValue.getElement("users").getKeys();
    while(usersIterator.hasNext()) {
      String userMatrixId = usersIterator.next();
      matrixUserPermissions.add(new MatrixUserPermission(userMatrixId, jsonValue.getElement("users").getElement(userMatrixId).getStringValue()));
    }
    return new MatrixRoomPermissions(matrixUserPermissions,
            jsonValue.getElement("users_default").getStringValue(),
            Events.fromJson(jsonValue.getElement("events")),
            jsonValue.getElement("events_default").getStringValue(),
            jsonValue.getElement("state_default").getStringValue(),
            jsonValue.getElement("ban").getStringValue(),
            jsonValue.getElement("kick").getStringValue(),
            jsonValue.getElement("redact").getStringValue(),
            jsonValue.getElement("invite").getStringValue(),
            jsonValue.getElement("historical").getStringValue());
  }
}
