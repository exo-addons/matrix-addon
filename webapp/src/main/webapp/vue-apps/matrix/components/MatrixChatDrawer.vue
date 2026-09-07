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
  <pinneable-drawer
    ref="meedsChatDrawer"
    app-name="chat"
    id="meedsChatDrawer"
    :loading="loading"
    :filter-placeholder="$t('matrix.rooms.filter.placeholder')"
    class="meeds-chat-drawer"
    :use-filter="!fullPageMode"
    :class="customHeaderClass"
    allow-expand
    right
    @filter-updated="handleFilterUpdate"
    @expand-updated="handleExpanded"
    @closed="$emit('closed')">
    <template slot="title">
      <matrix-filter-room-list-input
        v-if="showFilter && fullPageMode"
        ref="filter"
        :show-filter.sync="showFilter"
        :filter-text.sync="filterText"
        @update:filterText="handleFilterUpdate" />
      <div
        v-else
        class="d-flex">
        <matrix-chat-header-user-avatar :presence="presence" />
        <div class="ms-auto me-5">
          <v-btn
            v-if="fullPageMode && canCreateRooms"
            :title="$t('matrix.chat.quick.create.discussion')"
            icon
            @click="openQuickCreateChatDiscussionDrawer">
            <v-icon
              class="icon-default-color"
              size="20">
              fa-plus
            </v-icon>
          </v-btn>
          <v-btn
            v-if="fullPageMode"
            :title="$t('matrix.chat.markAllRead.label')"
            :disabled="!hasUnread"
            icon
            @click="markAllAsRead">
            <v-icon
              class="icon-default-color"
              size="20">
              fa-envelope-open-text
            </v-icon>
          </v-btn>
          <v-btn
            v-if="fullPageMode"
            icon
            @click="openFilter">
            <v-icon
              :class="{
                'primary--text': !!filterText,
                'icon-default-color': !filterText
              }"
              size="20">
              fa-filter
            </v-icon>
          </v-btn>
        </div>
      </div>
    </template>
    <template
      slot="titleIcons">
      <v-btn
        v-if="!fullPageMode && canCreateRooms"
        :title="$t('matrix.chat.quick.create.discussion')"
        icon
        @click="openQuickCreateChatDiscussionDrawer">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-plus
        </v-icon>
      </v-btn>
      <v-btn
        v-if="!fullPageMode && !selectedRoom"
        :title="$t('matrix.chat.markAllRead.label')"
        :disabled="!hasUnread"
        icon
        @click="markAllAsRead">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-envelope-open-text
        </v-icon>
      </v-btn>
      <matrix-ask-ai-list-action
        v-if="aiConciergeEnabled && !(selectedRoom && fullPageMode)" />
      <div
        v-if="selectedRoom && fullPageMode"
        class="text-truncate">
        <matrix-room-avatar :room="selectedRoom" />
      </div>
      <matrix-room-header-actions
        v-if="selectedRoom && fullPageMode"
        ref="roomHeaderActions"
        :room="selectedRoom"
        class="ms-auto" />
    </template>
    <template slot="content">
      <matrix-chat-body
        ref="chatBody"
        :loading="loading"
        :rooms="rooms"
        :selected-room="selectedRoom"
        :parent-expanded="expanded"
        :from-room-list="true"
        :search-term="searchTerm"
        :message-results="messageResults"
        @room-active-changed="handleRoomActiveState"
        @loading="loading = $event" />
    </template>
  </pinneable-drawer>
</template>
<script>
export default {
  data() {
    return {
      loading: false,
      filterText: '',
      showFilter: false,
      expanded: false,
      selectedRoom: null,
      componentKey: 0,
      previousRoomId: null
    };
  },
  props: {
    rooms: {
      type: Array,
      default: null
    },
    loadingRooms: {
      type: Boolean,
      default: false
    },
    presence: {
      type: String,
      default: 'available'
    },
    searchTerm: {
      type: String,
      default: null
    },
    messageResults: {
      type: Array,
      default: () => []
    }
  },
  created() {
    this.$root.$on('open-chat-discussion', this.openDiscussion);
    window.addEventListener('resize', this.handleResize);
  },
  beforeDestroy() {
    this.$root.$off('open-chat-discussion', this.openDiscussion);
    window.removeEventListener('resize', this.handleResize);
  },
  computed: {
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    customHeaderClass() {
      return this.fullPageMode ? this.selectedRoom ? 'fullPageHeader' : 'fullPageHeader_no_room' : '';
    },
    defaultRoomListContainerWidth() {
      return this.$root.defaultRoomListContainerWidth;
    },
    canCreateRooms() {
      return this.$root.canCreateRooms;
    },
    hasUnread() {
      return (this.rooms || []).some(room => (room.unreadMessages || 0) > 0);
    },
    aiConciergeEnabled() {
      return eXo.env.portal.aiConciergeEnabled;
    }
  },
  watch: {
    loading() {
      this.checkLoading();
    },
    loadingRooms() {
      this.checkLoading();
    },
    searchTerm(term) {
      // The list filter was cleared elsewhere (e.g. a conversation was opened) —
      // reset this drawer's filter input so it matches the now-unfiltered list.
      if (!term) {
        this.filterText = '';
        this.showFilter = false;
        this.$refs.meedsChatDrawer?.resetFilter?.();
      }
    }
  },
  mounted() {
    this.checkLoading();
  },
  methods: {
    handleRoomActiveState(id, isActive) {
      this.$emit('room-active-changed', id, isActive);
    },
    openFilter() {
      this.showFilter = !this.showFilter;
      this.$nextTick(() => {
        this.$refs.filter?.openFilter?.();
      });
    },
    handleResize() {
      this.computeMessagesContainerWidth();
    },
    computeMessagesContainerWidth() {
      this.$root.fullPageMessagesContainerWidth = this.$refs?.meedsChatDrawer?.$el?.clientWidth
        - this.defaultRoomListContainerWidth;
    },
    handleExpanded(expanded) {
      setTimeout(async () => {
        this.expanded = expanded;
        this.$root.fullPageMode = expanded;
        this.computeMessagesContainerWidth();
        this.selectedRoom = this.selectedRoom || this.getLastOpenedRoom();
        await this.openDiscussion(this.selectedRoom || this.rooms?.[0]);
      }, 300);
    },
    getLastOpenedRoom() {
      const lastOpenedRoomId = localStorage.getItem('lastOpenedRoomId');
      if (lastOpenedRoomId) {
        return this.rooms.find(room => room.id === lastOpenedRoomId);
      }
      return null;
    },
    async openDiscussion(room) {
      this.selectedRoom = room;
      await this.$refs?.chatBody?.openDiscussion?.(room);
      this.$root.$emit('room-discussion-opened', room?.id);

      setTimeout(() => {
        this.$refs?.chatBody?.scrollToEnd();
      }, 50);
    },
    handleFilterUpdate(text) {
      this.filterText = text;
      this.$emit('filter-updated', text);
    },
    checkLoading() {
      if (this.loading || this.loadingRooms) {
        this.$refs.meedsChatDrawer.startLoading();
      } else {
        this.$refs.meedsChatDrawer.endLoading();
      }
    },
    open() {
      if (!this.$refs.meedsChatDrawer.drawer) {
        this.$refs.meedsChatDrawer.open();
      }
    },
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    },
    markAllAsRead() {
      this.$root.$emit('chat-mark-all-read');
    },
  },
};
</script>
