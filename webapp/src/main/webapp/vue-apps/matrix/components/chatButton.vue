<template>
  <div class="VuetifyApp">
    <div class="v-application v-application--is-ltr theme--light">
      <div class="v-application--wrap">
        <v-btn
            id="btnChatButton"
            :title="$t('exo.mattermost.chat.button.tooltip')"
            @click="openMatrixRoom($event)"
            :color="color"
            icon>
          <v-icon size="22" class="my-auto icon-default-color fas fa-comments" />
        </v-btn>
      </div>
    </div>
  </div>
</template>
<script>
  export default {
    props: {
      roomId: {
        type: String,
        default: '',
      },
      serverName: {
        type: String,
        default: '',
      },
    },
    data: () => ({
      color: 'grey',
    }),
    methods: {
      openMatrixRoom(event){
        if (event){
          event.preventDefault();
          event.stopPropagation();
        }
        const url = `https://matrix.to/#/${this.roomId}:${this.serverName}?via=${this.serverName}`;
        window.open(url, '_blank');
      }
    }
  };
</script>
