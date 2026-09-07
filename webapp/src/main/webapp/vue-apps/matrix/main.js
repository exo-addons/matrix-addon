import MatrixChatButton from './components/ChatButton.vue';
import MatrixChatDrawer from './components/MatrixChatDrawer.vue';
import MatrixChatRooms from './components/MatrixChatRooms.vue';
import MatrixChatRoom from './components/MatrixChatRoom.vue';
import MeedsChatMessage from './components/ChatMessage.vue';
import MeedsChatMessageContent from './components/ChatMessageContent.vue';
import MeedsChatQuickCreateDiscussionDrawer from './components/MeedsChatQuickCreateDiscussionDrawer.vue';
import MeedsChatShareFileDrawer from './components/MeedsChatShareFileDrawer.vue';
import MeedsChatDiscussionDrawer from './components/MeedsChatDiscussionDrawer.vue';
import MeedsChatParentSpaceSelector from './components/MeedsChatParentSpaceSelector.vue';
import PopoverChatButton from './components/PopoverChatButton.vue';
import AudioMessage from './components/message/AudioMessage.vue';
import AudioMessageTranscription from './components/message/AudioMessageTranscription.vue';
import MessageReplyQuote from './components/message/MessageReplyQuote.vue';
import MessageEditBanner from './components/message/MessageEditBanner.vue';
import MessageUploadFileInput from './components/message/MessageUploadFileInput.vue';
import MessageUser from './components/message/MessageUser.vue';
import MessageActionList from './components/message/action/MessageActionList.vue'
import MessageReactionItem from './components/message/MessageReactionItem.vue';
import MessageSenderName from './components/message/MessageSenderName.vue';
import RoomLastMessage from './components/room/RoomLastMessage.vue';
import SpaceSettingsAdministration from './components/space-settings/SpaceSettingsAdministration.vue';
import RoomActionMenu from './components/room/RoomActionMenu.vue';
import RoomActionMenuDrawer from './components/room/RoomActionMenuDrawer.vue';
import RoomAttachmentsDrawer from './components/room/RoomAttachmentsDrawer.vue';
import RoomActionListItems from './components/room/RoomActionListItems.vue';
import MessageReceiptList from './components/message/receipt/MessageReceiptList.vue';
import MessageReceipt from './components/message/receipt/MessageReceipt.vue';
import MessageReadReceiptListDrawer from './components/message/read/MessageReadReceiptListDrawer.vue';
import MessageTypingIndicator from './components/message/write/MessageTypingIndicator.vue';
import VoiceMessageRecorder from './components/message/VoiceMessageRecorder.vue';
import RoomMessages from './components/room/RoomMessages.vue';
import MessageComposer from './components/message/MessageComposer.vue';
import RoomAvatar from './components/room/RoomAvatar.vue';
import RoomHeaderActions from './components/room/RoomHeaderActions.vue';
import AskAIChatRoomAction from './components/room/AskAIChatRoomAction.vue';
import AskAIChatListAction from './components/room/AskAIChatListAction.vue';
import AskAIChatMessageAction from './components/message/action/AskAIChatMessageAction.vue';
import MatrixChatBody from './components/MatrixChatBody.vue';
import FilterRoomListInput from './components/room/FilterRoomListInput.vue';
import RoomFilterChips from './components/room/RoomFilterChips.vue';
import ChatHeaderUserAvatar from './components/ChatHeaderUserAvatar.vue';
import FileMessage from './components/message/FileMessage.vue';

import {chatConstants} from './js/Constants.js';
import {chatRootOptions} from './js/chatRootOptions.js';
import * as matrixService from './js/MatrixService.js';
import {registerChatExtensions} from './extension.js';
import * as timeUtils from './js/timeUtils.js';
import * as matrixUtils from './js/matrixUtils';
import './icons-extensions.js';
import TouchHold from './js/directives/touchHold.js';

const components = {
  'matrix-chat-button': MatrixChatButton,
  'matrix-chat-drawer': MatrixChatDrawer,
  'matrix-chat-rooms': MatrixChatRooms,
  'matrix-chat-room': MatrixChatRoom,
  'matrix-chat-message': MeedsChatMessage,
  'matrix-chat-message-content': MeedsChatMessageContent,
  'matrix-popover-chat-button': PopoverChatButton,
  'matrix-chat-quick-create-discussion-drawer': MeedsChatQuickCreateDiscussionDrawer,
  'matrix-chat-share-file-drawer': MeedsChatShareFileDrawer,
  'matrix-chat-discussion-drawer': MeedsChatDiscussionDrawer,
  'matrix-chat-parent-space-selector': MeedsChatParentSpaceSelector,
  'matrix-audio-message': AudioMessage,
  'matrix-audio-message-transcription': AudioMessageTranscription,
  'matrix-message-reply-quote': MessageReplyQuote,
  'matrix-message-edit-banner': MessageEditBanner,
  'matrix-message-upload-file-input': MessageUploadFileInput,
  'matrix-message-user': MessageUser,
  'matrix-message-action-list': MessageActionList,
  'matrix-message-reaction-item': MessageReactionItem,
  'matrix-message-sender-name': MessageSenderName,
  'matrix-room-last-message': RoomLastMessage,
  'matrix-chat-space-settings': SpaceSettingsAdministration,
  'matrix-room-action-menu': RoomActionMenu,
  'matrix-room-action-menu-drawer': RoomActionMenuDrawer,
  'matrix-room-attachments-drawer': RoomAttachmentsDrawer,
  'matrix-room-action-list-items': RoomActionListItems,
  'matrix-message-receipt-list': MessageReceiptList,
  'matrix-message-receipt': MessageReceipt,
  'matrix-message-read-receipt-list-drawer': MessageReadReceiptListDrawer,
  'matrix-message-typing-indicator': MessageTypingIndicator,
  'matrix-voice-message-recorder': VoiceMessageRecorder,
  'matrix-room-messages': RoomMessages,
  'matrix-message-composer': MessageComposer,
  'matrix-room-avatar': RoomAvatar,
  'matrix-room-header-actions': RoomHeaderActions,
  'matrix-ask-ai-room-action': AskAIChatRoomAction,
  'matrix-ask-ai-list-action': AskAIChatListAction,
  'matrix-ask-ai-message-action': AskAIChatMessageAction,
  'matrix-chat-body': MatrixChatBody,
  'matrix-filter-room-list-input': FilterRoomListInput,
  'matrix-room-filter-chips': RoomFilterChips,
  'matrix-chat-header-user-avatar': ChatHeaderUserAvatar,
  'matrix-file-message': FileMessage,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

Vue.directive('touch-hold', TouchHold);

window.Object.defineProperty(Vue.prototype, '$matrixService', {
  value: matrixService,
});
window.Object.defineProperty(Vue.prototype, '$chatConstants', {
  value: chatConstants,
});
window.Object.defineProperty(Vue.prototype, '$timeUtils', {
  value: timeUtils,
});
window.Object.defineProperty(Vue.prototype, '$matrixUtils', {
  value: matrixUtils,
});

Vue.prototype.$filesIconsExtension = extensionRegistry.loadExtensions('chat', 'files-icons-extension');


const appId = 'matrixChatButton';
const lang = window?.eXo?.env?.portal?.language || 'fr';
const i18NUrl = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`;
const channel = new BroadcastChannel(appId);

export function init(serverName) {
  exoi18n.loadLanguageAsync(lang, i18NUrl).then(i18n => {
    registerChatExtensions(i18n.messages[lang]['matrix.chat.open']);
    Vue.createApp({
      template: `<matrix-chat-button id="matrixChatButton" serverName="${serverName}"/>`,
      vuetify: Vue.prototype.vuetifyOptions,
      // Shared with the hidden instance the Application Center badge mounts, so
      // the drawers behave identically whichever one is on the page
      ...chatRootOptions(serverName, channel),
      i18n
    },
    `#${appId}`, 'Matrix');
  });
}
