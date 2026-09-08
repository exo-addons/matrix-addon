/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.notification;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;

import io.meeds.chat.service.ChatNotificationService;
import io.meeds.pwa.plugin.PwaDirectNotificationActionPlugin;

/**
 * Quick actions of the chat room popup: "mark as read" marks the room read up
 * to the popup's latest message (the device was authenticated by pwa).
 * Declared as a {@code @Service} so the Kernel/Spring bridge exposes it to the
 * pwa context, which collects the action plugins.
 */
@Service
public class ChatDirectNotificationActionPlugin implements PwaDirectNotificationActionPlugin {

  @Autowired
  private ChatNotificationService chatNotificationService;

  @Override
  public String getNotificationKind() {
    return ChatNotificationService.CHAT_NOTIFICATION_KIND;
  }

  @Override
  public void handleAction(String username,
                           String action,
                           String objectKey,
                           Map<String, String> data) throws ObjectNotFoundException, IllegalAccessException {
    if (!StringUtils.equals(action, ChatNotificationService.MARK_READ_ACTION)) {
      throw new IllegalArgumentException("matrix.notification.unsupportedAction");
    }
    Long timestamp = null;
    if (data.get("ts") != null) {
      try {
        timestamp = Long.parseLong(data.get("ts"));
      } catch (NumberFormatException e) {
        timestamp = null;
      }
    }
    // the room is the token-scoped object (popup tag); the echoed roomId is ignored
    chatNotificationService.markRoomAsRead(username, objectKey, data.get("eventId"), timestamp);
  }

}
