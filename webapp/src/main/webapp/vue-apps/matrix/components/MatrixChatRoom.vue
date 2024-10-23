<template>
  <div>
    <div>
      {{ roomId }}
    </div>
  </div>
</template>
<script>
  export default {
    props: {
      roomId: {
        type: String,
        default: null,
      }
    },
    data: () => ({
      color: 'green',
      open: false,
      room: null
    }),
    created() {
      this.$matrixService.loadRoom(this.roomId).then(room => {
        this.room = room;
      });
    }

  }
</script>
