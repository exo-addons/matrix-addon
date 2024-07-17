import MatrixComponent from './components/component.vue';
import MatrixChatButton from './components/chatButton.vue';

const components = {
  'matrix-component': MatrixComponent,
  'matrix-chat-button': MatrixChatButton
};

for (const key in components) {
  Vue.component(key, components[key]);
}

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
