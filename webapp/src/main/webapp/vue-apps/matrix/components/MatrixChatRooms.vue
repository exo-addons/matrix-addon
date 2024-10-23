<template>
  <div class="room-list">
    <div
        v-if="roomsIds"
        v-for="(room, i) in roomsIds"
        :key="i">
      <matrix-chat-room
          :id="'room-'+i"
          :room-id="room" />
    </div>
    <div v-else>
      {{ $t('chat.no.discussions') }}
    </div>

  </div>
</template>
<script>
  export default {
    data: () => ({
      roomsIds: Array
    }),
    watch : {
    },
    created() {
      this.loadRooms();
    },
    methods: {
      loadRooms () {
        this.$matrixService.loadChatRooms().then(rooms => this.roomsIds = rooms.joined_rooms);
      }
    }
  }
</script>
