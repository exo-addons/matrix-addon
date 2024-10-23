<template>

</template>
<script>
  export default {
    props: {
    },
    created() {
      this.loadMessages();
    },
    methods: {
      loadMessages () {
        this.$matrixService.loadChatMessages().then(messages => this.messages = messages);
      }
    }
  }
</script>
