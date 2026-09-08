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
  <v-sheet
    :max-width="composerContainerMaxWidth"
    :class="{'justify-self-center': expanded}"
    class="d-flex"
    width="100%">
    <matrix-message-upload-file-input
      v-if="!isRecording"
      :room="room"
      paste-target="messageComposerArea"
      drop-target="ChatDiscussionDrawer"
      class="me-2 mb-0_5 d-flex flex-column justify-end"
      @file-sent="setInputFocus" />
    <matrix-voice-message-recorder
      ref="voiceMessageRecorder"
      v-if="isRecording"
      :room="room"
      :expanded="expanded"
      :drawer-width="drawerWidth"
      @cancel="cancelRecording" />
    <div
      v-else
      class="flex-grow-1 no-min-width border-radius-16"
      :class="{'border-color-grey-lighten': hasReplyQuote || messageToEdit}">
      <matrix-message-reply-quote
        v-if="hasReplyQuote"
        ref="replyQuote"
        :message="targetReplyMessage"
        :room="room"
        class="background-grey-primary no-min-width mx-2 mt-2"
        read-only
        closeable
        @close="cancelReply" />
      <matrix-message-edit-banner
        v-if="messageToEdit"
        ref="editMessageBanner"
        @close="cancelEditMessage" />
      <div
        :class="{'no-border': hasReplyQuote || messageToEdit}"
        class="d-flex border-color-grey-lighten border-radius-16">
        <div
          id="messageComposerArea"
          :placeholder="$t('matrix.chat.message.label')"
          ref="messageComposerArea"
          contenteditable="true"
          class="meeds-chat-composer specific-scrollbar text-break no-border input-placeholder border-box-sizing ps-3 pe-1 py-2"
          @keypress.enter.prevent
          @blur="onInputBlur"
          @keydown.enter="checkIfMentioning"
          @keydown.enter.prevent="sendMessageWithEnter"
          @keyup="resizeComposerArea"
          @focus="onInputFocus"
          @input="onComposerInput">
        </div>
        <div class="mb-0_5 me-1 d-flex flex-column justify-end">
          <emoji-picker-button
            :icon-size="20"
            @select-emoji="insertEmojiIntoComposer" />
        </div>
      </div>
    </div>
    <div
      v-if="!isRecording"
      class="d-flex flex-column justify-end">
      <v-btn
        v-if="hasComposerContent"
        class="ms-2 mb-0_5"
        icon
        @click="sendMessage">
        <v-icon
          color="primary"
          size="20">
          fa-paper-plane
        </v-icon>
      </v-btn>
      <v-btn
        v-else
        class="ms-2 mb-0_5"
        icon
        @click="isRecording = true">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-solid fa-microphone
        </v-icon>
      </v-btn>
    </div>
    <emoji-suggester
      composer-id="messageComposerArea"
      :min-width="258"
      @select-emoji="insertEmojiIntoComposer" />
  </v-sheet>
</template>

<script>

export default {
  data() {
    return {
      isRecording: false,
      targetReplyMessage: null,
      messageToEdit: null,
      isInputFocused: false,
      composerDefaultHeight: 40,
      insertedNewLine: false,
      messageContent: null,
      typingTimeout: null,
      mentioningInProgress: false,
      isSending: false,
      sendingTypingTimeout: null
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    drawerWidth: {
      type: Number,
      default: 420
    },
    expanded: {
      type: Boolean,
      default: false
    },
  },
  created() {
    document.addEventListener('matrix-message-reaction-added', this.setInputFocus);
    document.addEventListener('matrix-message-deleted', this.setInputFocus);

    this.$root.$on('edit-message', this.editMessage);
    this.$root.$on('reply-to-message', this.replyToMessage);
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-reaction-added', this.setInputFocus);
    document.removeEventListener('matrix-message-deleted', this.setInputFocus);

    this.$root.$off('edit-message', this.editMessage);
    this.$root.$off('reply-to-message', this.replyToMessage);
  },
  computed: {
    hasReplyQuote() {
      return !!this.targetReplyMessage;
    },
    composerContainerMaxWidth() {
      return this.expanded && this.drawerWidth * 2 / 3 || undefined;
    },
    hasComposerContent() {
      return !!this.messageContent?.trim()?.length || this.messageToEdit;
    }
  },
  watch: {
    isRecording() {
      if (this.isRecording) {
        this.$nextTick(() => {
          this.$refs?.voiceMessageRecorder?.startRecording();
        });
      }
    }
  },
  methods: {
    sendMessageWithEnter(event) {
      const isMobile = this.$vuetify.breakpoint.name === 'sm'
          || this.$vuetify.breakpoint.name === 'xs'
          || this.$vuetify.breakpoint.name === 'md';
      if (event && event.keyCode === this.$chatConstants.ENTER_CODE_KEY && !this.mentioningInProgress) {
        if (event.ctrlKey || event.altKey || event.shiftKey || isMobile) {
          this.insertNewLineAtCursor();
        } else {
          this.sendMessage();
        }
      }
    },
    async replyToMessage(roomId, messages, targetMessage) {
      if (this.room?.id !== roomId) {
        return;
      }
      this.messageToEdit = null;
      this.$refs.messageComposerArea.innerHTML = '';
      this.targetReplyMessage = {
        ...targetMessage,
        replyTo: await this.$matrixService.buildReplyToObject(messages, targetMessage.event_id)
      };
      this.$nextTick(() => {
        this.$refs?.messageComposerArea?.focus();
      });
    },
    editMessage(roomId, message) {
      if (this.room?.id !== roomId) {
        return;
      }
      const composerArea = this.$refs.messageComposerArea;
      this.targetReplyMessage = null;
      this.messageToEdit = message;
      const messageContent = message.formattedMessage || message.content.formatted_body
                                                        || message.content.body ;
      composerArea.innerHTML = this.$matrixUtils.restoreMentions(messageContent);
      composerArea.focus();
      // Move the cursor to the end of the message
      const range = document.createRange();
      const selection = window.getSelection();
      range.setStart(composerArea, composerArea.childNodes.length);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    async sendMessage() {
      if (this.isSending) {
        return;
      }

      const composer = this.$refs.messageComposerArea;
      const messageText = composer.innerText.trim();

      if (!messageText) {
        return;
      }

      // lock send action
      this.isSending = true;

      try {
        const message = {
          body: messageText,
          msgtype: 'm.text'
        };

        this.parseMentions(composer, message);

        if (this.targetReplyMessage) {
          message['m.relates_to'] = {
            'm.in_reply_to': { event_id: this.targetReplyMessage.event_id }
          };
        }

        if (this.messageToEdit) {
          message['m.new_content'] = {
            msgtype: 'm.text',
            body: message.body,
            'm.mentions': message['m.mentions'] || {}
          };
          if (message.formatted_body) {
            message['m.new_content'].formatted_body = message.formatted_body;
            message['m.new_content'].format = message.format;
          }
          message['m.relates_to'] = {
            rel_type: 'm.replace',
            event_id: this.messageToEdit.event_id
          };
        }

        const eventId = await this.$matrixService.sendMessage(message, this.room.id);
        this.$matrixService.markMessageAsRead(this.room.id, eventId)
          .catch(e => console.error('Failed to mark own message as read:', this.room.id, e));
        this.$matrixService.sendTyping(this.room.id, false);
        clearTimeout(this.sendingTypingTimeout);
        if (!this.messageToEdit) {
          this.$root.$emit('message-sent-statistics', message, this.room);
        }

        this.resetComposer();
        this.mentioningInProgress = false;
        this.messageToEdit = null;
        this.$emit('scroll-to-end');
      } catch (error) {
        console.error('Failed to send message:', error);
      } finally {
        this.isSending = false;
      }
    },
    parseMentions(composer, message) {
      const mentionsArray = Array.from(composer.querySelectorAll('span[data-user-id]'))
        .map(span => `@${span.getAttribute('data-user-id')}:${matrixServerName}`)
        .filter((v, i, a) => a.indexOf(v) === i);

      if (mentionsArray.length) {
        Array.from(composer.querySelectorAll('span.exo-mention')).forEach(span => {
          const userId = span.getAttribute('data-user-id');
          const userName = span.getAttribute('data-user-name');
          if (!userId || !userName) {
            return;
          }
          const parent = span.closest('span.atwho-inserted') || span;
          const a = document.createElement('a');
          a.href = `https://matrix.to/#/@${userId}:${matrixServerName}`;
          a.textContent = userName;
          parent.replaceWith(a);
        });

        const messageHTML = composer.innerHTML;

        message.format = 'org.matrix.custom.html';
        message.formatted_body = messageHTML;
        message['m.mentions'] = { user_ids: mentionsArray };
      }
    },
    resetComposer() {
      if (!this.$refs.messageComposerArea) {
        return;
      }
      this.resetComposerHeight();
      this.$refs.messageComposerArea.innerHTML = '';
      this.messageContent = null;
      this.insertedNewLine = false;
      this.targetReplyMessage = null;
      this.messageToEdit = null;
      this.setInputFocus();
    },
    resetComposerHeight() {
      this.$refs.messageComposerArea.style.height = `${this.composerDefaultHeight}px`;
    },
    insertEmojiIntoComposer(emoji, range = null) {
      const composer = this.$refs.messageComposerArea;
      composer.focus();
      const selection = window.getSelection();
      let insertRange = range;
      if (!insertRange) {
        if (!selection || selection.rangeCount === 0) {
          composer.innerHTML += emoji;
          this.$matrixUtils.placeCaretAtEnd(composer);
          return;
        }
        insertRange = selection.getRangeAt(0).cloneRange();
      }
      selection.removeAllRanges();

      const emojiNode = document.createTextNode(emoji);
      insertRange.deleteContents();
      insertRange.insertNode(emojiNode);

      insertRange.setStartAfter(emojiNode);
      insertRange.setEndAfter(emojiNode);
      selection.addRange(insertRange);

      // Notify Vue it's updated
      const event = new Event('input', { bubbles: true });
      composer.dispatchEvent(event);
    },
    initSuggester() {
      const $messageSuggestor = $('#messageComposerArea');
      const component = this;
      const peopleSearchCached = {};
      let lastNoResultQuery = false;
      let space = null;

      const getSpace = async (spaceId) => {
        if (!spaceId || space) {
          return space;
        }
        const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${spaceId}`;
        const resp = await fetch(url, { credentials: 'include' });
        return resp.ok ? (space = await resp.json()) : null;
      };

      const retrievePeople = async (url) => {
        const resp = await fetch(url, { credentials: 'include' });
        return resp.ok ? resp.json() : [];
      };

      const cacheAndCallback = (key, query, results, callback) => {
        peopleSearchCached[key] = results;
        lastNoResultQuery = results.length ? false : query;
        callback(results);
      };

      const suggesterData = {
        type: 'mix',
        suffix: '\u00A0',
        create: false,
        createOnBlur: false,
        highlight: false,
        openOnFocus: false,
        closeAfterSelect: true,
        dropdownParent: 'body',
        hideSelected: true,
        renderMenuItem(item, parent) {
          parent.data('value', item.uid);
          return `<div class="avatarSmall" style="display: inline-block;"><img src="${item.avatar}"></div> ${item.name}`;
        },
        renderItem(item) {
          return `<span class="exo-mention" data-user-id="${item.uid}" data-user-name="${item.name}"><i aria-hidden="true" class="v-icon fa" style="font-size: 14px;"></i> ${item.name}<a href="#" class="remove"><i class="uiIconClose uiIconLightGray"></i></a></span>`;
        },
        sourceProviders: ['chat:users'],
        providers: {
          'chat:users': function (query, callback) {
            (async () => {
              const cleanQuery = query?.trim().toLowerCase();
              if (!cleanQuery) {
                return callback([]);
              }

              if (
                lastNoResultQuery &&
                  cleanQuery.startsWith(lastNoResultQuery.toLowerCase()) &&
                  cleanQuery.length > lastNoResultQuery.length
              ) {
                return callback([]);
              }

              const room = component.room;
              const spaceId = room?.spaceId;
              const cacheKey = `${cleanQuery}#${spaceId || room.id}`;

              if (peopleSearchCached[cacheKey]) {
                return callback(peopleSearchCached[cacheKey]);
              }

              peopleSearchCached[cacheKey] = [];

              try {
                if (spaceId) {
                  const spaceData = await getSpace(spaceId);
                  const userName = eXo.env.portal.userName;

                  let url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/social/people/suggest.json`;
                  url += `?nameToSearch=${encodeURIComponent(cleanQuery)}&typeOfRelation=member_of_space&currentUser=${encodeURIComponent(userName)}`;
                  if (spaceData?.prettyName) {
                    url += `&spaceURL=${encodeURIComponent(spaceData.prettyName)}`;
                  }

                  const users = await retrievePeople(url);
                  const results = users?.options?.map(user => ({
                    uid: user.value,
                    name: user.text,
                    avatar: user.avatarUrl,
                  })) || [];

                  return cacheAndCallback(cacheKey, cleanQuery, results, callback);

                } else {
                  // fallback when no spaceId (direct chat)
                  const participant = room.members?.find(member => member.id !== matrixUserId);
                  const memberId = participant?.id || room.dmMemberId;
                  const user = await component.$matrixService.getUserByMatrixId(memberId, room);
                  const matchedUser = user?.profile.fullname.toLowerCase().includes(cleanQuery.toLowerCase()) && user || null;
                  const results = matchedUser ? [{
                    uid: matchedUser.remoteId,
                    name: matchedUser.profile.fullname,
                    avatar: matchedUser.profile.avatar,
                  }] : [];

                  return cacheAndCallback(cacheKey, cleanQuery, results, callback);
                }
              } catch (err) {
                console.error('Suggester error:', err);
                return callback([]);
              }
            })();
          }
        }
      };
      $messageSuggestor.suggester(suggesterData);
    },
    resizeComposerArea() {
      if (this.room) {
        this.initSuggester();
      }

      const composerElement = this.$refs.messageComposerArea;
      const minHeight = this.composerDefaultHeight;
      const maxHeight = 300;

      composerElement.style.height = 'auto';
      const newHeight = composerElement.scrollHeight;

      if (newHeight < minHeight) {
        composerElement.style.height = `${minHeight}px`;
        composerElement.style.overflowY = 'hidden';
      } else if (newHeight <= maxHeight) {
        composerElement.style.height = `${newHeight}px`;
        composerElement.style.overflowY = 'hidden';
      } else {
        composerElement.style.height = `${maxHeight}px`;
        composerElement.style.overflowY = 'auto';
      }
      this.$emit('composer-resize', newHeight);
    },
    insertNewLineAtCursor() {
      const selection = window.getSelection();
      if (!selection.rangeCount) {
        return;
      }
      const range = selection.getRangeAt(0);
      const br = document.createElement('br');
      range.insertNode(br);
      range.setStartAfter(br);
      if (!this.insertedNewLine) {
        const textNode = document.createTextNode('\u00a0');
        range.insertNode(textNode);
        this.insertedNewLine = true;
      }
      range.setStartAfter(br);
      range.setEndAfter(br);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    checkIfMentioning() {
      const composer = this.$refs.messageComposerArea;
      const lastChild = composer?.lastElementChild;
      const lastText = composer?.lastChild?.wholeText || '';

      this.mentioningInProgress = lastChild?.classList.contains('atwho-query') && !lastText.trim();
    },
    onComposerInput(event) {
      this.messageContent = event.target?.innerText;
      this.resizeComposerArea(event);
      if (this.sendingTypingTimeout) {
        clearTimeout(this.sendingTypingTimeout);
      }
      this.sendingTypingTimeout = setTimeout( () => {
        this.$matrixService.sendTyping(this.room.id, true);
      }, 400);

      if (this.typingTimeout) {
        clearTimeout(this.typingTimeout);
      }
      this.typingTimeout = setTimeout(() => {
        this.$matrixService.sendTyping(this.room.id, false);
      }, 3000);
    },
    setInputFocus() {
      this.$nextTick(() => {
        this.$refs?.messageComposerArea?.focus();
      });
    },
    onInputBlur() {
      this.isInputFocused = false;
      this.$emit('input-focus', this.isInputFocused);
    },
    onInputFocus(event) {
      setTimeout(() => {
        this.markRoomAsRead();
      }, 500);
      this.isInputFocused = true;
      this.$emit('input-focus', this.isInputFocused);
      this.resizeComposerArea(event);
    },
    markRoomAsRead() {
      this.$emit('mark-room-as-read', this.room?.id);
    },
    cancelReply() {
      this.targetReplyMessage = null;
      this.$refs?.messageComposerArea?.focus();
    },
    cancelEditMessage() {
      this.messageToEdit = null;
      this.$refs.messageComposerArea.innerHTML = '';
      this.$refs?.messageComposerArea?.focus();
    },
    cancelRecording() {
      this.isRecording = false;
      this.$nextTick(() => {
        this.$refs?.messageComposerArea?.focus();
      });
    }
  }
};
</script>
