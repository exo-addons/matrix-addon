<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2025 Meeds Association contact@meeds.io
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->

<template>
  <div
    id="chatMessagesContainer"
    ref="chatMessagesContainer"
    class="specific-scrollbar position-relative overflow-x-hidden"
    v-touch="{down: () => loadMoreMessages()}"
    @scroll="onMessagesContainerScroll">
    <div
      v-if="searchActive || findBarOpen"
      class="chat-find-bar d-flex align-center px-2 py-1 elevation-2 border-radius"
      style="position:sticky;top:8px;z-index:1000;width:fit-content;max-width:calc(100% - 32px);margin-inline-start:auto;margin-inline-end:16px;background-color:var(--allPagesBaseBackground, #fff);">
      <template v-if="findBarOpen">
        <v-icon size="16" class="icon-default-color me-2">fa-filter</v-icon>
        <input
          id="chatFindBarInput"
          ref="findBarInput"
          v-model="findBarText"
          type="text"
          :placeholder="$t('matrix.chat.search.placeholder')"
          :aria-label="$t('matrix.chat.search.placeholder')"
          class="chat-find-input me-2"
          @input="onFindBarInput"
          @keydown.enter.prevent="searchNav('next')"
          @keydown.esc.prevent="closeFindBar">
      </template>
      <span class="text-caption text-sub-title me-1" style="white-space:nowrap;">{{ matchLabel }}</span>
      <v-btn
        icon
        small
        :disabled="!matchEventIds.length"
        :title="$t('matrix.chat.search.previous')"
        @click="searchNav('previous')">
        <v-icon size="16" class="icon-default-color">fa-chevron-up</v-icon>
      </v-btn>
      <v-btn
        icon
        small
        :disabled="!matchEventIds.length"
        :title="$t('matrix.chat.search.next')"
        @click="searchNav('next')">
        <v-icon size="16" class="icon-default-color">fa-chevron-down</v-icon>
      </v-btn>
      <v-btn
        v-if="findBarOpen"
        icon
        small
        :title="$t('matrix.chat.cancel')"
        @click="closeFindBar">
        <v-icon size="16" class="icon-default-color">fa-times</v-icon>
      </v-btn>
    </div>
    <div
      v-if="loadingNewMessages"
      class="application-background-color application-border application-border-radius flex d-flex flex-column">
      <v-progress-circular
        color="primary"
        size="20"
        indeterminate
        class="mx-auto my-5" />
    </div>
    <div
      id="roomChatMessages"
      v-show="messages && !loading"
      class="d-flex flex-column fill-height"
      @wheel="loadMoreMessages"
      @scroll="loadMoreMessages">
      <matrix-chat-message
        v-for="(message, i) in messages"
        :id="`chat-message-${i}`"
        :ref="`chat-message-${i}`"
        :key="message.event_id"
        :message="message"
        :previous-message="messages?.[i - 1]"
        :next-message="messages?.[i + 1]"
        :is-last-message="i === messages?.length - 1"
        :room="room"
        :unseen-messages-data="unSeenMessagesData"
        :is-input-focused="isInputFocused"
        :unseen-messages-count="unseenMessagesCount"
        :room-last-read-receipts="roomLastReadReceipts"
        class="transition-2s"
        @reply="replyToMessage"
        @reaction="reactToMessage"
        @reset-unseen="resetUnseenData" />
      <matrix-message-typing-indicator
        v-if="isTyping"
        :room="room"
        :typing-users="typingUsers"
        class="ms-4 mt-2"
        @scroll="scrollToEnd" />
    </div>
    <floating-arrow-button
      v-if="!loading && hasUnseenMessages"
      ref="unseenScrollArrow"
      :show-badge="hasUnseenMessages"
      :closeable-tooltip="$t('matrix.messages.mark.as.read')"
      :button-tooltip="$t('matrix.messages.check.new')"
      scroll-target="unseenSeparator"
      class="mt-16 pt-2 me-5"
      top-position
      closeable
      up-arrow
      @click="scrollToUnseenSectionSeparator"
      @closed="clearUnseenData" />
    <floating-arrow-button
      v-if="!loading && !isAtBottomMessages"
      :show-badge="hasUnseenNewReceivedMessage"
      :button-tooltip="$t('matrix.messages.jump.to.last')"
      class="mb-16 pb-2 me-5"
      @click="scrollToBottomMessages" />
  </div>
</template>

<script>
// Breathing space kept between a message scrolled into view and the edges of the
// visible area (below the floating find bar, above the bottom of the container).
const SCROLL_TARGET_GAP = 12;
// Fallback clearance when the find bar element cannot be measured yet (top:8px + its height).
const FIND_BAR_SCROLL_MARGIN = 56;
// Delay after the smooth scroll before checking the match is really fully visible:
// avatars, images and reactions can still change the layout while the scroll runs.
const SCROLL_SETTLE_DELAY = 400;
// Class marking the highlighted occurrences of the searched term.
const SEARCH_HIGHLIGHT_CLASS = 'chat-search-highlight';

export default {
  data() {
    return {
      messages: [],
      loading: false,
      space: null,
      lastScrollTop: 0,
      hasUnseenNewReceivedMessage: false,
      loadingNewMessages: false,
      hasMoreMessages: true,
      composerDefaultHeight: 40,
      messageContainerScrollTop: 0,
      messageContainerScrollHeight: 0,
      messageContainerClientHeight: 0,
      unSeenMessagesData: {
        firstUnseenEventId: null,
        inViewport: {
          visibleTop: false,
          above: false,
          below: false
        }
      },
      typingCache: {},
      messagesContainerElement: null,
      messagesContainerId: 'chatMessagesContainer',
      roomLastReadReceipts: null,
      currentLoadToken: 0,
      isUserScrolling: false,
      userScrollTimeout: null,
      lastMarkedReadEventId: null,
      scrollBottomThreshold: 60,
      matchEventIds: [],
      currentMatch: -1,
      currentTerm: '',
      highlightEls: [],
      searchActive: false,
      searchToken: 0,
      findBarOpen: false,
      findBarText: '',
      findBarDebounce: null,
      scrollSettleTimeout: null,
      scrollCorrectionCleanup: null
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    isInputFocused: {
      type: Boolean,
      default: false
    }
  },
  created() {
    document.addEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.addEventListener('matrix-message-received', this.messageReceived);
    document.addEventListener('matrix-message-deleted', this.messageDeleted);
    document.addEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.addEventListener('matrix-unseen-data-updated', this.handleUpdateUnseenData);
    document.addEventListener('matrix-unseen-data-reset', this.resetLocalUnseenData);
    document.addEventListener('matrix-message-reaction-added', this.reactionAdded);
    this.$root.$on('room-discussion-opened', this.markRoomAsRead);
    this.$root.$on('move-to-message',  this.moveToMessage);
    this.$root.$on('conversation-search', this.runSearch);
    this.$root.$on('conversation-search-close', this.clearSearch);
    this.$root.$on('open-conversation-find', this.openFindBar);
  },
  beforeDestroy() {
    clearTimeout(this.findBarDebounce);
    this.cancelScrollCorrection();
    document.removeEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.removeEventListener('matrix-message-received', this.messageReceived);
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    document.removeEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.removeEventListener('matrix-unseen-data-updated', this.handleUpdateUnseenData);
    document.removeEventListener('matrix-unseen-data-reset', this.resetLocalUnseenData);
    document.removeEventListener('matrix-message-reaction-added', this.reactionAdded);
    this.$root.$off('room-discussion-opened', this.markRoomAsRead);
    this.$root.$off('move-to-message',  this.moveToMessage);
    this.$root.$off('conversation-search', this.runSearch);
    this.$root.$off('conversation-search-close', this.clearSearch);
    this.$root.$off('open-conversation-find', this.openFindBar);
  },
  computed: {
    fullPageMode() {
      return this.$root?.fullPageMode;
    },
    matchLabel() {
      if (!this.searchActive) {
        return '';
      }
      return this.matchEventIds.length ? `${this.currentMatch + 1}/${this.matchEventIds.length}`
                                       : this.$t('matrix.chat.search.noResults');
    },
    typingUsers() {
      return this.typingCache?.[this.room?.id]?.typingUsers || [];
    },
    isTyping() {
      return this.typingUsers.length > 0;
    },
    unseenViewPortInfo() {
      return this.unSeenMessagesData?.viewPortInfo;
    },
    hasUnseenMessages() {
      const info = this.unseenViewPortInfo;
      if (!info) {
        return false;
      }

      return info.visibleTop === false && (info.above === true || info.below === false);
    },
    isAtBottomMessages() {
      return (
        this.messageContainerScrollHeight - this.messageContainerScrollTop - this.messageContainerClientHeight
          <= this.scrollBottomThreshold
      );
    },
    unseenMessagesCount() {
      const firstUnseenMessageId = this.unSeenMessagesData?.firstUnseenEventId;
      const messages = this.messages || [];

      if (!firstUnseenMessageId || !messages.length) {
        return 0;
      }

      const index = messages.findIndex(msg => msg.event_id === firstUnseenMessageId);
      if (index < 0) {
        return 0;
      }

      const unseen = messages.slice(index + 1);
      const filtered = unseen.filter(msg => msg.sender !== matrixUserId);
      return filtered.length;
    }
  },
  watch: {
    loading() {
      this.$nextTick(() => {
        this.$emit('loading', this.loading);
      });
    },
    room() {
      // cancel any ongoing loads
      this.currentLoadToken++;
    },
    fullPageMode(isFullPage) {
      // In drawer mode the search input is the exo-drawer header filter; when the
      // drawer is expanded that header (and its filter) is replaced, so a search
      // started in the drawer would keep its highlights with no visible field.
      // Surface it in the floating find bar, seeded with the current term.
      if (isFullPage && this.searchActive && !this.findBarOpen) {
        this.findBarOpen = true;
        this.findBarText = this.currentTerm;
      }
    }
  },
  methods: {
    isContainerAtBottomMessages() {
      const container = this.getMessagesContainerElement();
      if (!container) {
        return false;
      }
      const distanceFromBottom =
          container.scrollHeight - container.scrollTop - container.clientHeight;
      return distanceFromBottom <= this.scrollBottomThreshold;
    },
    reactionAdded({ detail: { roomId } }) {
      if (roomId !== this.room?.id) {
        return;
      }

      const container = this.getMessagesContainerElement();
      const oldHeight = container.scrollHeight;

      const applyScroll = () => {
        const newHeight = container.scrollHeight;
        const delta = newHeight - oldHeight;
        if (delta !== 0) {
          container.scrollBy({ top: delta, behavior: 'auto' });
        }
      };

      const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);

      if (isIOS) {
        requestAnimationFrame(() => requestAnimationFrame(applyScroll));
      } else {
        requestAnimationFrame(applyScroll);
      }
    },
    async initDiscussion(room) {
      this.reset();
      this.resetData();

      this.roomLastReadReceipts = await this.$matrixService.loadLastReadReceipts(room?.id || this.room?.id);
    },
    async loadAndProcessMessages() {
      if (!this.room?.id) {
        return;
      }

      const roomId = this.room.id;
      const loadToken = ++this.currentLoadToken;

      this.loading = true;
      await this.$nextTick();

      try {
        const resp = await this.$matrixService.loadAllMessagesWithOriginalCount(roomId);
        this.from = resp.start;
        this.to = resp.end;
        this.hasMoreMessages = resp.hasMore;

        if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
          this.loading = false;
          return;
        }

        const allMessages = resp.chunk;
        if (!allMessages?.length) {
          this.loading = false;
          return;
        }
        const reversedMessages = allMessages.slice();
        const processed = await new Promise(resolve => {
          (window.requestIdleCallback || window.requestAnimationFrame)(async () => {
            const result = await this.$matrixService.processMessages(this.room, reversedMessages);
            resolve(result);
          });
        });

        if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
          return;
        }

        const result = processed.messages;
        const chunkSize = 10;
        const chunks = [];

        for (let i = 0; i < result.length; i += chunkSize) {
          chunks.push(result.slice(i, i + chunkSize).reverse());
        }

        this.messages = chunks.shift();

        const container = this.getMessagesContainerElement();

        for (const chunk of chunks) {
          const prevScrollHeight = container.scrollHeight;
          this.messages = [...chunk, ...this.messages];
          await new Promise(resolve => requestAnimationFrame(resolve));
          const newScrollHeight = container.scrollHeight;
          container.scrollTop += newScrollHeight - prevScrollHeight;
        }

      } catch (err) {
        console.error('Error loading messages:', err);
      } finally {
        this.loading = false;
      }
    },
    async messageReceived(event) {
      if (!this.messages) {
        return;
      }
      if (this.room?.id !== event.detail.roomId) {
        return;
      }
      const wasAtBottom = this.isContainerAtBottomMessages();
      const receivedMessage = event.detail.message;
      const relatesTo = receivedMessage.content['m.relates_to'];
      const inReplyTo = relatesTo?.['m.in_reply_to']?.event_id;
      await this.$matrixService.processMessageMentions(receivedMessage, this.room);
      await this.$matrixService.processMediaExistence(receivedMessage);
      if (receivedMessage.edited) {
        const index = this.messages.findIndex(msg => msg.event_id === receivedMessage.event_id);
        if (index !== -1) {
          this.$set(this.messages, index, {
            ...this.messages[index],
            content: receivedMessage.content,
            formattedMessage: receivedMessage?.formattedMessage,
            updatedAt: receivedMessage.updatedAt,
            edited: true
          });
        }

        for (let i = 0; i < this.messages.length; i++) {
          const message = this.messages[i];
          if (message?.replyTo?.targetEventId === receivedMessage.event_id) {
            const replyTo = await this.$matrixService.buildReplyToObject(this.messages, message.replyTo.targetEventId);
            this.$set(this.messages, i, { ...message, replyTo });
          }
        }
      } else {
        if (inReplyTo) {
          receivedMessage.replyTo = await this.$matrixService.buildReplyToObject(this.messages, inReplyTo);
        }
        this.messages.push(receivedMessage);
        setTimeout(() => {
          if (wasAtBottom) {
            this.scrollToEnd();
            if (!document.hidden && this.fullPageMode) {
              this.markRoomAsRead(this.room?.id);
            }
          } else {
            this.hasUnseenNewReceivedMessage = true;
          }
        }, 100);
      }
    },
    messageDeleted(event) {
      if (!this.messages || this.room?.id !== event.detail.roomId) {
        return;
      }

      const redactedEventId = event?.detail?.eventId;
      const redaction = event.detail?.redaction;
      const index = this.messages.findIndex(msg => msg.event_id === redactedEventId);
      if (index === -1) {
        return;
      }

      const original = this.messages[index];
      const redacted = {
        ...original,
        redacted_because: redaction || { redacts: redactedEventId, reason: 'Redacted' },
        content: {
          ...original.content,
          body: undefined,
          formatted_body: undefined,
          format: undefined,
          msgtype: undefined
        }
      };

      if (original.edited) {
        redacted.edited = false;
        redacted.updatedAt = undefined;
      }

      this.$set(this.messages, index, redacted);
      this.updateUnseenOnMessageDelete(redactedEventId, index);
    },
    async markRoomAsRead(roomId) {
      if (this.messages?.length) {
        const eventId = await this.$matrixService.getRoomLastMessageEventId(roomId);
        if (eventId === this.lastMarkedReadEventId) {
          return;
        }
        this.$matrixService.markRoomAsFullyRead(roomId, eventId).then(() => {
          document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', {
            detail: {roomId: roomId}
          }));
          this.lastMarkedReadEventId = eventId;
        }).catch(e => console.error('Failed to mark room as read:', roomId, e));
      }
    },
    resetLocalUnseenData({detail: {roomId, userId}}) {
      if (this.room?.id === roomId && userId === matrixUserId) {
        this.resetData();
      }
    },
    resetData() {
      if (!this.unSeenMessagesData?.viewPortInfo) {
        return;
      }
      this.unSeenMessagesData.firstUnseenEventId = null;
      this.unSeenMessagesData.viewPortInfo.visibleTop = true;
      this.unSeenMessagesData.viewPortInfo.above = false;
      this.unSeenMessagesData.viewPortInfo.below = false;
    },
    updateUnseenOnMessageDelete(redactedEventId, index) {
      const unseenData = this.unSeenMessagesData;
      if (!unseenData) {
        return;
      }

      if (redactedEventId === unseenData.firstUnseenEventId) {
        const nextMessage = this.messages[index + 1];

        let updatedUnseen = {};
        if (nextMessage) {
          updatedUnseen.firstUnseenEventId = nextMessage.event_id;
        } else {
          updatedUnseen = null;
        }

        this.unSeenMessagesData = updatedUnseen;
        this.$matrixService.saveUnseenMessages(this.room.id, matrixUserId, updatedUnseen);
      }
    },
    handleTypingReceived(event) {
      const {roomId, users} = event.detail;
      const now = Date.now();
      this.$set(this.typingCache, roomId, {typingUsers: users, lastUpdated: now});
    },
    handleSpaceSettingsUpdate(event) {
      this.space = event.detail;
      if (this.space.id !== this.room?.spaceId) {
        return;
      }
      this.room.name = this.space.displayName;
    },
    handleUpdateUnseenData(event) {
      const {roomId} = event.detail;
      if (this.room?.id !== roomId) {
        return;
      }
      setTimeout(async () => {
        if (this.isInputFocused) {
          this.clearUnseenData();
        } else {
          await this.loadUnseenMessagesData();
        }
      }, 200);
    },
    async loadUnseenMessagesData() {
      this.unSeenMessagesData = await this.$matrixService.getUnseenMessagesData(this.room?.id, matrixUserId);
      this.$forceUpdate();
    },
    scrollToBottomMessages() {
      if (!this.hasUnseenMessages) {
        this.clearUnseenData();
      }
      this.scrollToEnd();
      this.markRoomAsRead(this.room?.id);
    },
    clearUnseenData() {
      this.$matrixService.resetUnseenOnFirstMessageSeen(this.room?.id, matrixUserId).then(() => {
        this.resetData();
      });
    },
    getMessagesContainerElement() {
      return document.getElementById(this.messagesContainerId);
    },
    scrollToEnd(loadToken = this.currentLoadToken, roomId = this.room?.id) {
      const container = this.$refs.chatMessagesContainer;
      if (!container || !this.messages?.length) {
        return;
      }

      const token = this.currentLoadToken;
      const currentRoomId = this.room?.id;
      if (token !== loadToken || currentRoomId !== roomId) {
        return;
      }

      this.$nextTick(() => {
        this.$matrixUtils.scrollToBottomWhenStable(container,
          () => this.currentLoadToken === token && this.room?.id === roomId
        );
      });
    },
    replyToMessage(message) {
      this.$root.$emit('reply-to-message', this.room?.id, this.messages, message);
    },
    async reactToMessage(emoji, targetMessage) {
      const existingReaction = targetMessage?.reactions?.find?.(reaction => reaction.key === emoji
          && reaction.userIds.includes(matrixUserId));
      if (existingReaction) {
        await this.removeReaction(emoji, targetMessage);
      } else {
        await this.$matrixService.reactToMessage(emoji, this.room.id, targetMessage.event_id);
      }
    },
    async removeReaction(emoji, targetMessage) {
      const reactionEventId = await this.$matrixService.findReactionEventId(
        emoji,
        targetMessage.event_id,
        matrixUserId,
        this.room.id);
      if (reactionEventId) {
        await this.$matrixService.redactEvent(this.room.id, reactionEventId);
      }
    },
    keepScrollAtBottom() {
      if (this.isAtBottomMessages) {
        this.scrollToEnd();
      }
    },
    onMessagesContainerScroll(event) {
      const container = event.target;
      this.messageContainerScrollTop = container.scrollTop;

      this.messageContainerScrollHeight = container.scrollHeight;
      this.messageContainerClientHeight = container.clientHeight;

      if (this.isAtBottomMessages) {
        this.hasUnseenNewReceivedMessage = false;
        if (!document.hidden) {
          clearTimeout(this.userScrollTimeout);
          this.userScrollTimeout = setTimeout(() => {
            this.markRoomAsRead(this.room?.id);
          }, 500);
        }
      }
    },
    resetUnseenData() {
      this.$matrixService.resetUnseenOnFirstMessageSeen(this.room?.id, matrixUserId).then(reset => {
        if (reset) {
          this.resetData();
        }
      });
    },
    loadMoreMessages() {
      const messagesDOMEl = this.getMessagesContainerElement();
      const scrollTop = messagesDOMEl.scrollTop;
      if (scrollTop < this.lastScrollTop) {
        const composerDOMEl = document.getElementById('messageComposerArea');
        composerDOMEl.style.height = `${this.composerDefaultHeight}px`;
      }
      this.lastScrollTop = scrollTop >= 0 && scrollTop || 0;
      if (this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
        return;
      }
      this.loadingNewMessages = true;
      setTimeout(async () => {
        try {
          await this.fetchAndAppendOlderMessages(true);
        } finally {
          this.loadingNewMessages = false;
        }
      }, 1000);
    },
    reset() {
      this.messages = [];
      this.lastScrollTop = 0;
    },
    getMessageContentElement(eventId) {
      return document.getElementById(`message-content-${eventId}`);
    },
    async runSearch(term) {
      const query = (term || '').trim();
      if (!query) {
        this.clearSearch();
        return;
      }
      this.searchActive = true;
      this.currentTerm = query.toLowerCase();
      const token = ++this.searchToken;
      let results = [];
      try {
        // Server-side full-text search over the whole conversation (not just loaded messages).
        results = await this.$matrixService.searchMessages(this.room?.id, query, 100) || [];
      } catch (error) {
        console.error('Chat message search failed', error);
        results = [];
      }
      if (token !== this.searchToken) {
        return; // a newer search superseded this one
      }
      // The server returns matches most-recent-first; flip to chronological for up/down navigation.
      this.matchEventIds = results.map(result => result.eventId).filter(Boolean).reverse();
      this.currentMatch = this.matchEventIds.length ? 0 : -1;
      this.$nextTick(() => {
        this.highlightAllMatches();
        this.scrollToMatch();
      });
    },
    searchNav(direction) {
      if (!this.matchEventIds.length) {
        return;
      }
      const step = direction === 'previous' ? -1 : 1;
      this.currentMatch = (this.currentMatch + step + this.matchEventIds.length) % this.matchEventIds.length;
      this.scrollToMatch();
    },
    clearSearch() {
      this.cancelScrollCorrection();
      this.clearHighlight();
      this.matchEventIds = [];
      this.currentMatch = -1;
      this.currentTerm = '';
      this.searchActive = false;
    },
    openFindBar() {
      this.findBarOpen = true;
      this.$nextTick(() => this.$refs.findBarInput?.focus?.());
    },
    onFindBarInput() {
      clearTimeout(this.findBarDebounce);
      this.findBarDebounce = setTimeout(() => this.runSearch(this.findBarText), 250);
    },
    closeFindBar() {
      this.findBarOpen = false;
      this.findBarText = '';
      this.clearSearch();
    },
    async scrollToMatch() {
      const eventId = this.matchEventIds[this.currentMatch];
      if (!eventId) {
        return;
      }
      const targetElement = await this.loadMessageElement(eventId);
      if (!targetElement) {
        return;
      }
      // Reaching the match may have paged in older messages: highlight the newly-loaded
      // matches before scrolling, so the scroll can anchor on the occurrence itself.
      this.highlightAllMatches();
      await this.$nextTick();
      this.scrollMessageIntoView(targetElement, this.getFirstHighlight(targetElement));
    },
    clearHighlight() {
      (this.highlightEls || []).forEach(mark => {
        const parent = mark.parentNode;
        if (parent) {
          parent.replaceChild(document.createTextNode(mark.textContent), mark);
          parent.normalize?.();
        }
      });
      this.highlightEls = [];
    },
    highlightAllMatches() {
      this.clearHighlight();
      if (!this.currentTerm) {
        return;
      }
      this.matchEventIds.forEach(eventId => {
        const container = this.getMessageContentElement(eventId);
        if (container) {
          this.highlightOccurrences(container, this.currentTerm);
        }
      });
    },
    highlightOccurrences(container, term) {
      const nodes = [];
      const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null);
      let node = walker.nextNode();
      while (node) {
        nodes.push(node);
        node = walker.nextNode();
      }
      nodes.forEach(textNode => {
        const text = textNode.nodeValue;
        const lower = text.toLowerCase();
        if (!lower.includes(term)) {
          return;
        }
        const fragment = document.createDocumentFragment();
        let from = 0;
        let index = lower.indexOf(term, from);
        while (index >= 0) {
          if (index > from) {
            fragment.appendChild(document.createTextNode(text.slice(from, index)));
          }
          const mark = document.createElement('span');
          mark.className = SEARCH_HIGHLIGHT_CLASS;
          mark.style.backgroundColor = '#ffeb3b';
          mark.style.color = '#000';
          mark.style.borderRadius = '2px';
          mark.textContent = text.slice(index, index + term.length);
          fragment.appendChild(mark);
          this.highlightEls.push(mark);
          from = index + term.length;
          index = lower.indexOf(term, from);
        }
        if (from < text.length) {
          fragment.appendChild(document.createTextNode(text.slice(from)));
        }
        textNode.parentNode.replaceChild(fragment, textNode);
      });
    },
    async scrollToUnseenSectionSeparator() {
      const separatorFound = document.getElementById('unseenSeparator');
      if (separatorFound) {
        return;
      }
      const container = this.getMessagesContainerElement();
      if (!container) {
        return;
      }
      const firstUnseenEventId = this.unSeenMessagesData?.firstUnseenEventId;
      if (!firstUnseenEventId) {
        return;
      }
      await this.moveToMessage(firstUnseenEventId);
    },
    async moveToMessage(eventId) {
      const targetElement = await this.loadMessageElement(eventId);
      if (targetElement) {
        this.scrollMessageIntoView(targetElement);
      }
    },
    // Pages older messages in until the wanted message is rendered; returns its content element.
    async loadMessageElement(eventId) {
      let targetElement = this.getMessageContentElement(eventId);
      let tries = 0;
      const maxTries = 10;

      while (!targetElement && this.hasMoreMessages && tries < maxTries) {
        tries++;
        await this.forceLoadMoreMessages();
        await this.$nextTick();
        targetElement = this.getMessageContentElement(eventId);
      }

      if (!targetElement) {
        this.$root.$emit('alert-message', this.$t('matrix.unread.section.load.exceed'), 'success');
      }
      return targetElement;
    },
    getFirstHighlight(contentElement) {
      return contentElement?.querySelector?.(`.${SEARCH_HIGHLIGHT_CLASS}`) || null;
    },
    // Vertical space taken at the top of the scroll container by the floating find bar,
    // which is sticky over the messages and would otherwise clip the message scrolled to.
    getTopOverlayHeight(container) {
      const findBar = container?.querySelector?.('.chat-find-bar');
      if (!findBar) {
        return 0;
      }
      const height = findBar.getBoundingClientRect().bottom - container.getBoundingClientRect().top;
      return height > 0 && height || FIND_BAR_SCROLL_MARGIN;
    },
    // scrollIntoView({block: 'center'}) ignores the sticky find bar and, on messages taller
    // than the visible area, leaves the matched line half cut. Compute the scroll position
    // ourselves so the whole message bubble — or at least the matched line — is fully visible.
    scrollMessageIntoView(contentElement, highlightElement = null) {
      // Resolve the container from the message itself: the id is shared by the drawer and
      // the full-page conversation, so a lookup by id may return the other one.
      const container = contentElement?.closest?.(`#${this.messagesContainerId}`);
      if (!container || !contentElement?.isConnected) {
        return;
      }
      const top = this.computeScrollTopFor(container, contentElement, highlightElement);
      if (top === null) {
        return;
      }
      container.scrollTo({top, behavior: 'smooth'});
      this.scheduleScrollCorrection(container, contentElement, highlightElement);
    },
    // The layout can still shift while the smooth scroll runs (avatars, images, reactions):
    // check once it settled and correct the position, unless the user took over scrolling.
    scheduleScrollCorrection(container, contentElement, highlightElement) {
      this.cancelScrollCorrection();
      const cancel = () => this.cancelScrollCorrection();
      container.addEventListener('wheel', cancel, {passive: true});
      container.addEventListener('touchstart', cancel, {passive: true});
      this.scrollCorrectionCleanup = () => {
        container.removeEventListener('wheel', cancel);
        container.removeEventListener('touchstart', cancel);
      };
      this.scrollSettleTimeout = setTimeout(() => {
        const corrected = this.computeScrollTopFor(container, contentElement, highlightElement);
        if (corrected !== null && Math.abs(corrected - container.scrollTop) > SCROLL_TARGET_GAP) {
          container.scrollTo({top: corrected, behavior: 'smooth'});
        }
        this.cancelScrollCorrection();
      }, SCROLL_SETTLE_DELAY);
    },
    cancelScrollCorrection() {
      clearTimeout(this.scrollSettleTimeout);
      this.scrollSettleTimeout = null;
      this.scrollCorrectionCleanup?.();
      this.scrollCorrectionCleanup = null;
    },
    computeScrollTopFor(container, contentElement, highlightElement) {
      if (!contentElement?.isConnected) {
        return null;
      }
      // The bubble holds the reply quote, the text and the timestamp: showing it whole is
      // what makes the matched line readable, not just the text node.
      const bubble = contentElement.closest('.chat-message-content-body') || contentElement;
      const topInset = this.getTopOverlayHeight(container) + SCROLL_TARGET_GAP;
      const visibleHeight = container.clientHeight - topInset - SCROLL_TARGET_GAP;
      if (visibleHeight <= 0) {
        return null;
      }
      const containerTop = container.getBoundingClientRect().top;
      let rect = bubble.getBoundingClientRect();
      // A message longer than the visible area can never be shown whole: fall back on the
      // matched occurrence so its own line is the one guaranteed to be fully visible.
      if (rect.height > visibleHeight && highlightElement?.isConnected) {
        rect = highlightElement.getBoundingClientRect();
      }
      const offsetTop = rect.top - containerTop + container.scrollTop;
      const freeSpace = Math.max(0, visibleHeight - rect.height);
      const wantedTop = offsetTop - topInset - freeSpace / 2;
      const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
      return Math.min(Math.max(wantedTop, 0), maxScrollTop);
    },
    async fetchAndAppendOlderMessages(preserveScroll = true) {
      const lastMessageId = this.messages?.[0]?.event_id;

      const resp = await this.$matrixService.loadAllMessagesWithOriginalCount(this.room.id, this.to);
      if (!resp.chunk?.length) {
        this.hasMoreMessages = false;
        return;
      }
      if (resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
        this.hasMoreMessages = false;
      }

      const messagesToProcess = [...resp.chunk.reverse()];
      const processedMessages = await this.$matrixService.processMessages(this.room, messagesToProcess);
      this.messages = [...processedMessages.messages, ...this.messages];
      this.from = resp.start;
      this.to = resp.end;

      if (preserveScroll && lastMessageId) {
        await this.$nextTick();
        const lastMsgEl = this.getMessageContentElement(lastMessageId);
        lastMsgEl?.scrollIntoView({ behavior: 'instant' });
      }
    },
    async forceLoadMoreMessages() {
      if (this.loadingNewMessages || !this.hasMoreMessages) {
        return;
      }

      this.loadingNewMessages = true;
      try {
        await this.fetchAndAppendOlderMessages(true);
      } catch (err) {
        console.error('Error loading older messages:', err);
      } finally {
        this.loadingNewMessages = false;
      }
    },
  }
};
</script>
