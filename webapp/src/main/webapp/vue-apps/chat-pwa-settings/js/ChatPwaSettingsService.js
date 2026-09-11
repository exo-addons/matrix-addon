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

const CHAT_NOTIFICATION_KIND = 'chat';

export function getChatNotificationSetting(subscriptionId) {
  return fetch(`/pwa/rest/subscriptions/settings/${subscriptionId}/${CHAT_NOTIFICATION_KIND}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (resp?.status === 404) {
      // the device has no server-side subscription (yet)
      const error = new Error('subscription-not-found');
      error.notFound = true;
      throw error;
    } else if (!resp?.ok) {
      throw new Error('Error retrieving chat notification setting');
    }
    return resp.text();
  }).then(text => text?.length && JSON.parse(text) || null);
}

export function saveChatNotificationSetting(subscriptionId, enabled, delayMinutes) {
  return fetch(`/pwa/rest/subscriptions/settings/${subscriptionId}/${CHAT_NOTIFICATION_KIND}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      enabled,
      delayMinutes,
    }),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Error saving chat notification setting');
    }
  });
}
