<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
  <v-list class="pa-0">
    <v-list-item
      class="ps-2 pe-3 height-auto"
      @click.stop="toggleFavorite">
      <v-sheet
        class="d-flex"
        width="28"
        height="36">
        <v-icon
          class="icon-default-color mx-auto"
          size="16">
          {{ isFavorite ? 'fas fa-star' : 'far fa-star' }}
        </v-icon>
      </v-sheet>
      <span v-if="!isFavorite">
        {{ $t('matrix.room.favorite.add.label') }}
      </span>
      <span v-else>
        {{ $t('matrix.room.favorite.remove.label') }}
      </span>
    </v-list-item>
    <v-list-item
      class="ps-2 pe-3 height-auto"
      @click.stop="muteRoom">
      <v-sheet
        class="d-flex"
        width="28"
        height="36">
        <v-icon
          class="icon-default-color mx-auto"
          size="16">
          {{ isMuted ? 'fas fa-bell' : 'fas fa-bell-slash' }}
        </v-icon>
      </v-sheet>
      <span v-if="!isMuted">
        {{ $t('matrix.room.mute.label') }}
      </span>
      <span v-else>
        {{ $t('matrix.room.unmute.label') }}
      </span>
    </v-list-item>
    <v-list-item
      class="ps-2 pe-3 height-auto"
      :disabled="!hasUnread"
      @click.stop="markAsRead">
      <v-sheet
        class="d-flex"
        width="28"
        height="36">
        <v-icon
          class="icon-default-color mx-auto"
          size="16">
          fa-envelope-open-text
        </v-icon>
      </v-sheet>
      <span>
        {{ $t('matrix.room.markRead.label') }}
      </span>
    </v-list-item>
  </v-list>
</template>

<script>

export default {
  props: {
    room: {
      type: Object,
      default: null
    }
  },
  computed: {
    isMuted() {
      return this.room?.muted;
    },
    isFavorite() {
      return this.room?.favorite;
    },
    hasUnread() {
      return (this.room?.unreadMessages || 0) > 0;
    },
    spaceId() {
      return this.room?.spaceId;
    },
  },
  methods: {
    toggleFavorite() {
      const willBeFavorite = !this.isFavorite;
      const request = willBeFavorite
        ? this.$matrixService.favoriteRoom(this.room.id, this.spaceId)
        : this.$matrixService.unfavoriteRoom(this.room.id);
      request.then(() => {
        // Optimistically reflect the new state so the favorite filter reacts immediately.
        this.$set(this.room, 'favorite', willBeFavorite);
        this.$root.$emit('alert-message',
          this.$t(`matrix.room.favorite.${willBeFavorite ? 'add' : 'remove'}.success`),
          'success');
        this.$emit('close');
        this.$root.$emit('room-favorite-updated', {
          roomId: this.room.id,
          favorite: willBeFavorite
        });
      }).catch(() => {
        this.$root.$emit('alert-message', this.$t('matrix.room.favorite.error'), 'error');
      });
    },
    markAsRead() {
      this.$emit('close');
      this.$matrixService.getRoomLastMessageEventId(this.room.id).then(eventId => {
        if (!eventId) {
          return;
        }
        this.$matrixService.markRoomAsFullyRead(this.room.id, eventId).then(() => {
          document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', {
            detail: {roomId: this.room.id}
          }));
          this.$root.$emit('alert-message', this.$t('matrix.room.markRead.success'), 'success');
        }).catch(() => {
          this.$root.$emit('alert-message', this.$t('matrix.room.markRead.error'), 'error');
        });
      });
    },
    muteRoom() {
      this.$matrixService.muteRoom(this.room.id, this.spaceId, this.isMuted).then(() => {
        this.$root.$emit('alert-message',
          this.$t(`matrix.room.${!this.isMuted ? 'mute' : 'unmute'}.success`),
          'success');
        this.$emit('close');
        setTimeout(() => {
          this.$root.$emit('room-muted-updated', {
            roomId: this.room.id,
            muted: !this.isMuted
          });
        }, 100);
      });
    }
  }
};
</script>
