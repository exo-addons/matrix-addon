import MatrixComponent from './components/component.vue';
import MatrixChatButton from './components/chatButton.vue';
import MatrixChatDrawer from './components/MatrixChatDrawer.vue';
import MatrixChatRooms from './components/MatrixChatRooms.vue';
import MatrixChatRoom from './components/MatrixChatRoom.vue';
import MatrixChatMessages from './components/MatrixChatMessages.vue';
import * as matrixService from './js/MatrixService.js';

const components = {
  'matrix-component': MatrixComponent,
  'matrix-chat-button': MatrixChatButton,
  'matrix-chat-drawer': MatrixChatDrawer,
  'matrix-chat-rooms': MatrixChatRooms,
  'matrix-chat-room': MatrixChatRoom,
  'matrix-chat-messages': MatrixChatMessages,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

window.Object.defineProperty(Vue.prototype, '$matrixService', {
  value: matrixService,
});

const appId = 'matrixChatButton';
const lang = window?.eXo?.env?.portal?.language || 'fr';
const i18NUrl = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`;

export function init(roomId, serverName) {
  exoi18n.loadLanguageAsync(lang, i18NUrl).then(i18n => {
    Vue.createApp({
      template: `<matrix-chat-button id="matrixChatButton" roomId="${roomId}" serverName="${serverName}"/>`,
      vuetify: Vue.prototype.vuetifyOptions,
      i18n
    },
    `#${appId}`, 'Matrix');
  });
}
