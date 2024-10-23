package org.exoplatform.addons.matrix.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Events {
  private String name;
  private String powerLevels;
  private String historyVisibility;
  private String canonicalAlias;
  private String avatar;
  private String tombstone;
  private String serverAcl;
  private String encryption;
  public String toJson() {
    return """
            {
                "m.room.name": %s,
                "m.room.power_levels": %s,
                "m.room.history_visibility": %s,
                "m.room.canonical_alias": %s,
                "m.room.avatar": %s,
                "m.room.tombstone": %s,
                "m.room.server_acl": %s,
                "m.room.encryption": %s
             }
            """.formatted(this.getName(), this.getPowerLevels(), this.getHistoryVisibility(), this.getCanonicalAlias(), this.getAvatar(), this.getTombstone(), this.getServerAcl(), this.getEncryption());
  }
  public static Events fromJson(JsonValue jsonValue) {
    return new Events(jsonValue.getElement("m.room.name").getStringValue(),
            jsonValue.getElement("m.room.power_levels").getStringValue(),
            jsonValue.getElement("m.room.history_visibility").getStringValue(),
            jsonValue.getElement("m.room.canonical_alias").getStringValue(),
            jsonValue.getElement("m.room.avatar").getStringValue(),
            jsonValue.getElement("m.room.tombstone").getStringValue(),
            jsonValue.getElement("m.room.server_acl").getStringValue(),
            jsonValue.getElement("m.room.encryption").getStringValue());

  }
}
