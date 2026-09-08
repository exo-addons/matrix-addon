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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.meeds.chat.service.ChatNotificationService;

@ExtendWith(MockitoExtension.class)
class ChatDirectNotificationActionPluginTest {

  @Mock
  private ChatNotificationService           chatNotificationService;

  @InjectMocks
  private ChatDirectNotificationActionPlugin plugin;

  @Test
  void markReadDelegatesToTheReadAnchor() throws Exception {
    assertEquals(ChatNotificationService.CHAT_NOTIFICATION_KIND, plugin.getNotificationKind());
    // the target is the token-scoped object key; an echoed roomId is ignored
    plugin.handleAction("demo", "markRead", "!room:server", Map.of("roomId", "!other:server", "eventId", "$evt", "ts", "12345"));
    verify(chatNotificationService).markRoomAsRead("demo", "!room:server", "$evt", 12345L);

    // an unparsable timestamp falls back to "now" on the service side
    plugin.handleAction("demo", "markRead", "!room:server", Map.of("eventId", "$evt", "ts", "x"));
    verify(chatNotificationService).markRoomAsRead("demo", "!room:server", "$evt", null);
  }

  @Test
  void unsupportedActionIsRejected() {
    assertThrows(IllegalArgumentException.class,
                 () -> plugin.handleAction("demo", "like", "!room:server", Map.of("eventId", "$evt")));
    verifyNoInteractions(chatNotificationService);
  }
}
