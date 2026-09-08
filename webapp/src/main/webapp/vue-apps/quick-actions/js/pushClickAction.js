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
import {chatConstants} from '../../matrix/js/Constants.js';
import {ensureChatApp, isChatReady, TOPBAR_APP_ID} from './chatApp.js';

/**
 * Opens a conversation in this page, mounting the chat application first when
 * the topbar does not carry it. `$matrixService` is installed by the Matrix
 * module `ensureChatApp()` loads, so nothing of it is bundled here.
 *
 * @param {string} roomId Matrix room id
 * @returns {Promise} rejected when no chat instance is listening or the room
 *          cannot be read by the current user
 */
function openRoom(roomId) {
  return ensureChatApp()
    .then(() => {
      if (!isChatReady()) {
        throw new Error('No chat instance is listening on this page');
      }
      return Vue.prototype.$matrixService.getRoomById(roomId);
    })
    .then(room => document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, {
      detail: {room},
    })));
}

/**
 * Opens the conversation a chat push popup was about, in the page the user is
 * already on: the pwa service worker forwards the popup's client action to the
 * focused app page instead of reloading it. Claiming the event (preventDefault)
 * tells pwa the action is owned here; when the room cannot be opened after all,
 * the popup's own url is followed, which is what an unclaimed click would do.
 */
document.addEventListener(chatConstants.ACTION_OPEN_CHAT_ROOM_FROM_PUSH, event => {
  const roomId = event?.detail?.roomId;
  if (!roomId) {
    return;
  }
  event.preventDefault();
  openRoom(roomId)
    .catch(error => {
      console.error('Error opening the chat room of a push notification', error);
      const url = event?.detail?.url;
      if (url?.startsWith(`${window.location.origin}/`)) {
        window.location.href = url;
      }
    });
});

// A page reached by a popup url carries ?roomId=: the chat button opens that
// room when it mounts. Without the topbar one (unpinned by an administrator),
// mounting the hidden instance is enough — it reads the url the same way
if (new URLSearchParams(window.location.search).get('roomId')
    && !document.querySelector(`#${TOPBAR_APP_ID}`)
    && typeof meedsChat !== 'undefined' && meedsChat.chatEnabled) {
  ensureChatApp()
    .catch(error => console.error('Error mounting the chat for the room of the page url', error));
}
