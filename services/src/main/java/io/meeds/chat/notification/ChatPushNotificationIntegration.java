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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.meeds.pwa.service.PwaNotificationService;

import jakarta.annotation.PostConstruct;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;

/**
 * Chat contributions to the generic PWA push pipeline: mentions keep their
 * on-site and mail channels but must not produce a push popup of their own —
 * the popup covering a mentioned message is the room's deferred one, like for
 * any message.
 */
@Component
public class ChatPushNotificationIntegration {

  @Autowired
  private PwaNotificationService pwaNotificationService;

  @PostConstruct
  public void init() {
    pwaNotificationService.excludePluginFromPush(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN);
  }
}
