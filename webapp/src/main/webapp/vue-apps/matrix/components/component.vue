<template>
  <span>
      {{ $t('exo.matrix.description') }}
  </span>
</template>
<script>
  export default {
    props: {
      
    },
    data: () => ({
    }),
    created() {
      this.initMatrixRoom();
    },
    methods: {
      initMatrixRoom() {
        return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/matrix?spaceId=${this.identityId}`, {
          method: 'GET',
          credentials: 'include',
        }).then(resp => {
          if (!resp || !resp.ok) {
            console.warn('Could not get the URL of the team !');
          } else {
            return resp.text();
          }
        }).then(data => {
          if(!data) {
            return this.createRoom();
          } else {
            this.mattermostTeamUrl = data || '';
          }
        });
      }
    }
  };
</script>
