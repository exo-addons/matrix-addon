<div class="VuetifyApp">
  <div data-app="true"
    class="v-application v-application--is-ltr theme--light"
    id="matrixChatButton">
    <script type="text/javascript">
      require(['PORTLET/matrix/Matrix'], app => {
        if(eXo.env.portal.spaceId) {
          fetch('/matrix/rest/matrix?spaceId='+eXo.env.portal.spaceId, {
            method: 'GET',
            credentials: 'include',
          }).then(respMatrix => {
            if (!respMatrix || !respMatrix.ok) {
              console.warn('Could not link the space to a Matrix room !');
            } else {
              return respMatrix.text();
            }
          }).then(respMatrixText => {
        	  if(respMatrixText) {
        		  console.log(respMatrixText);
        		  if(respMatrixText) {
        		    app.init(respMatrixText);
			        }
            }
          });
        }
      });
    </script>
  </div>
</div>
