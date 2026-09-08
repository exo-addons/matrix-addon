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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import {chatConstants} from '../../matrix/js/Constants.js';
import {chatRootOptions} from '../../matrix/js/chatRootOptions.js';

/** Id of the container the chat portlet mounts into in the topbar. */
export const TOPBAR_APP_ID = 'matrixChatButton';

/** Id of the app mounted here when the topbar one is absent. */
const QUICK_ACTION_APP_ID = 'chat-quick-actions';

/** How long to wait for an already-present chat instance to start listening. */
const READY_TIMEOUT_MS = 15000;

/**
 * Single mount guard: several callers may need the chat application at the
 * same moment — the unread badge of every rendered Chat tile, plus the quick
 * action itself — and only one instance must ever exist.
 */
let mountingPromise = null;

/**
 * Whether a chat instance has announced that its document listeners are
 * registered. Tracked from module load so an announcement made before anyone
 * asked is not missed.
 */
let chatReady = false;

document.addEventListener(chatConstants.CHAT_READY, () => chatReady = true);

/**
 * Whether a chat instance has announced it is listening: `ensureChatApp()`
 * resolves on timeout too, so a caller that needs a listener asks this.
 *
 * @returns {boolean} true once a chat instance dispatched CHAT_READY
 */
export function isChatReady() {
  return chatReady;
}

/**
 * Ensures one chat application is present on the page *and listening*,
 * mounting a hidden one when needed.
 * <p>
 * The chat application owns a Matrix client: an authenticated session, a sync
 * loop, the room list and the unread counters. Mounting a second one alongside
 * the topbar instance would double the sync traffic and let the two fight over
 * unread state — so an already present instance is reused, and a new one is
 * mounted only when the chat portlet is not on the page (its topbar item can be
 * unpinned by an administrator).
 *
 * @returns {Promise} resolved once an instance is present and listening
 */
export function ensureChatApp() {
  if (chatReady) {
    return Promise.resolve();
  }
  // The topbar container is a static JSP element, in the DOM from HTML parse:
  // finding it says an instance is coming, not that it can hear us yet
  if (document.querySelector(`#${TOPBAR_APP_ID}`)) {
    return whenChatReady();
  }
  if (!mountingPromise) {
    mountingPromise = new Promise((resolve, reject) => {
      window.require(
        ['SHARED/eXoVueI18n', 'PORTLET/matrix/Matrix'],
        exoi18n => initChatApp(exoi18n).then(resolve, reject),
        error => reject(error));
    })
      .then(() => whenChatReady())
      .catch(error => {
        // Never keep a rejected promise: a cached failure would make every
        // later click resolve nothing, silently and for the whole session
        mountingPromise = null;
        throw error;
      });
  }
  return mountingPromise;
}

/**
 * Resolves once a chat instance announces it is listening, asking it to
 * re-announce in case it became ready before this call started listening.
 * Resolves anyway after a timeout, so a caller is never left hanging.
 *
 * @returns {Promise} resolved when a chat instance is listening, or on timeout
 */
function whenChatReady() {
  if (chatReady) {
    return Promise.resolve();
  }
  return new Promise(resolve => {
    let timer = null;
    const onReady = () => {
      chatReady = true;
      clearTimeout(timer);
      document.removeEventListener(chatConstants.CHAT_READY, onReady);
      resolve();
    };
    timer = setTimeout(() => {
      document.removeEventListener(chatConstants.CHAT_READY, onReady);
      resolve();
    }, READY_TIMEOUT_MS);
    document.addEventListener(chatConstants.CHAT_READY, onReady);
    document.dispatchEvent(new CustomEvent(chatConstants.CHAT_READY_REQUEST));
  });
}

/**
 * Mounts the chat application with its button hidden: everything the drawers
 * and the badge need comes along — the sync loop, the rooms, the presence
 * polling — without a second chat icon appearing next to the quick actions.
 *
 * @param {Object} exoi18n the shared i18n module, loaded through AMD
 * @returns {Promise} resolved once the hidden application is mounted
 */
function initChatApp(exoi18n) {
  const lang = eXo.env.portal.language;
  const urls = [
    `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`,
    `/social/i18n/locale.portlet.Portlets?lang=${lang}`,
  ];
  const parent = document.createElement('div');
  parent.id = QUICK_ACTION_APP_ID;
  document.querySelector('#vuetify-apps').appendChild(parent);

  return new Promise((resolve, reject) => exoi18n.loadLanguageAsync(lang, urls)
    .then(i18n => Vue.createApp({
      template: `
        <matrix-chat-button
          id="${QUICK_ACTION_APP_ID}Instance"
          :server-name="serverName"
          hidden-button />
      `,
      // The globals come from UIMatrixHeadTemplate.gtmpl, which runs on every
      // page — so they are available here even though the chat portlet itself
      // is not displayed
      ...chatRootOptions(
        typeof matrixServerName === 'undefined' ? null : matrixServerName,
        new BroadcastChannel(TOPBAR_APP_ID)),
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        resolve();
      },
      vuetify: Vue.prototype.vuetifyOptions,
      i18n,
    }, `#${parent.id}`, 'Chat Quick Action'))
    .catch(reject));
}
