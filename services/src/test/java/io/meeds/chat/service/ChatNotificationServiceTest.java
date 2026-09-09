package io.meeds.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaDirectNotificationBuilder;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.util.JsonUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.command.NotificationCommand;
import org.exoplatform.commons.api.notification.command.NotificationExecutor;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.model.WebNotificationFilter;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.Orientation;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.resources.impl.LocaleConfigImpl;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static io.meeds.chat.service.ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ROOM_ID;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ROOM_MEMBER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatNotificationServiceTest extends MatrixBaseTest {

  @Autowired
  MatrixService                      matrixService;

  @Autowired
  IdentityManager                    identityManager;

  @Autowired
  ChatNotificationService            chatNotificationService;

  @Autowired
  PwaNotificationService             pwaNotificationService;

  @Autowired
  WebNotificationService             webNotificationService;

  @Autowired
  UserPortalConfigService            portalConfigService;

  @Autowired
  PermanentLinkService               permanentLinkService;

  @Mock
  private UserStateService           userStateService;

  @Mock
  private UserStateModel             userStateModel;

  @Mock
  private UserSetting                userSetting;

  @Mock
  private UserSettingService         userSettingService;

  @Mock
  private SettingService             settingService;

  @Mock
  private ResourceBundleService      resourceBundleService;

  private MockedStatic<CommonsUtils> commonsUtils;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    commonsUtils = mockStatic(CommonsUtils.class, CALLS_REAL_METHODS);
    commonsUtils.when(() -> CommonsUtils.getService(UserStateService.class)).thenReturn(userStateService);
    commonsUtils.when(() -> CommonsUtils.getService(UserSettingService.class)).thenReturn(userSettingService);
    lenient().when(userStateService.getUserState(anyString())).thenReturn(userStateModel);
    lenient().when(userSettingService.get(anyString())).thenReturn(userSetting);
    ReflectionTestUtils.setField(chatNotificationService, "settingService", settingService);
    ReflectionTestUtils.setField(ChatNotificationService.class, "userStateService", null);
    ReflectionTestUtils.setField(ChatNotificationService.class, "userSettingService", null);
  }

  @AfterEach
  @Override
  public void tearDown() {
    ReflectionTestUtils.setField(chatNotificationService, "pwaNotificationService", pwaNotificationService);
    ReflectionTestUtils.setField(chatNotificationService, "portalConfigService", portalConfigService);
    ReflectionTestUtils.setField(chatNotificationService, "permanentLinkService", permanentLinkService);
    super.tearDown();
    if (commonsUtils != null) {
      commonsUtils.close();
    }
  }

  @Test
  void onMatrixPushReceivedGuards() throws Exception {
    Space space = getSpaceInstance(2);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    PwaNotificationService mockedPwaNotificationService = mock(PwaNotificationService.class);
    lenient().when(mockedPwaNotificationService.canReceiveDirectNotifications(anyString(), anyString())).thenReturn(true);
    ReflectionTestUtils.setField(chatNotificationService, "pwaNotificationService", mockedPwaNotificationService);
    String eventId = "eventIDOnMatrix";

    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    roomId,
                                                    "m.room.message",
                                                    "Message content",
                                                    "m.text",
                                                    "@sender:matrix.meeds.tn",
                                                    new ArrayList<>(),
                                                    123456789);
    when(matrixHttpClient.getEventById(eventId, roomId, accessToken)).thenReturn(matrixMessage);

    when(userStateModel.getStatus()).thenReturn("available");
    chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    // a deferred popup is scheduled for the recipient device(s)
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(eq("demo"),
                                                                              eq(ChatNotificationService.CHAT_NOTIFICATION_KIND),
                                                                              eq(ChatNotificationService.DEFAULT_UNREAD_DELAY_SECONDS),
                                                                              any());

    when(userStateModel.getStatus()).thenReturn("donotdisturb");
    chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    // user status is Do not disturb: nothing scheduled
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(anyString(), anyString(), anyLong(), any());

    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(true);
    chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    // space is muted: nothing scheduled
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(anyString(), anyString(), anyLong(), any());

    lenient().when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    // a reaction or state event is never notified: the event resolver only
    // returns m.room.message events
    when(matrixHttpClient.getEventById(eventId, roomId, accessToken)).thenReturn(new MatrixMessage(eventId,
                                                                                                   roomId,
                                                                                                   "m.reaction",
                                                                                                   null,
                                                                                                   null,
                                                                                                   "@sender:matrix.meeds.tn",
                                                                                                   new ArrayList<>(),
                                                                                                   123456790));
    chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(anyString(), anyString(), anyLong(), any());

    // nothing can fire (PWA disabled or no subscribed device): nothing is
    // buffered nor scheduled
    when(matrixHttpClient.getEventById(eventId, roomId, accessToken)).thenReturn(matrixMessage);
    when(mockedPwaNotificationService.canReceiveDirectNotifications(eq("demo"), anyString())).thenReturn(false);
    chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(anyString(), anyString(), anyLong(), any());
  }

  @Test
  void buildRoomPopupAggregatesPerRoom() throws Exception {
    lenient().when(userStateModel.getStatus()).thenReturn("available");
    lenient().when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    PwaNotificationService mockedPwaNotificationService = mock(PwaNotificationService.class);
    lenient().when(mockedPwaNotificationService.canReceiveDirectNotifications(anyString(), anyString())).thenReturn(true);
    ReflectionTestUtils.setField(chatNotificationService, "pwaNotificationService", mockedPwaNotificationService);
    // the popup url is the recipient's landing page, never the message permalink:
    // give both a distinguishable answer so the assertion below is a real pin
    UserPortalConfigService mockedPortalConfigService = mock(UserPortalConfigService.class);
    lenient().when(mockedPortalConfigService.getDefaultPath("john")).thenReturn("/portal/dw/stream");
    lenient().when(mockedPortalConfigService.getMetaPortal()).thenReturn("dw");
    ReflectionTestUtils.setField(chatNotificationService, "portalConfigService", mockedPortalConfigService);
    PermanentLinkService mockedPermanentLinkService = mock(PermanentLinkService.class);
    lenient().when(mockedPermanentLinkService.getLink(any())).thenReturn("/portal/g/:spaces:space4/space4/");
    ReflectionTestUtils.setField(chatNotificationService, "permanentLinkService", mockedPermanentLinkService);
    LocaleConfigImpl localeConfig = new LocaleConfigImpl();
    localeConfig.setLocale(Locale.ENGLISH);
    localeConfig.setOrientation(Orientation.LT);
    when(mockedPwaNotificationService.getLocaleConfig(anyString())).thenReturn(localeConfig);

    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    String senderIdOnMatrix = matrixService.saveUserAccount(demoIdentity, true);
    Space space = getSpaceInstance(4);
    spacesToDelete.add(space);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    when(matrixHttpClient.getEventById("evt1", roomId, accessToken))
                                                                    .thenReturn(new MatrixMessage("evt1",
                                                                                                  roomId,
                                                                                                  "m.room.message",
                                                                                                  "first message",
                                                                                                  "m.text",
                                                                                                  senderIdOnMatrix,
                                                                                                  new ArrayList<>(),
                                                                                                  1000L));
    when(matrixHttpClient.getEventById("evt2", roomId, accessToken))
                                                                    .thenReturn(new MatrixMessage("evt2",
                                                                                                  roomId,
                                                                                                  "m.room.message",
                                                                                                  "second message",
                                                                                                  "m.text",
                                                                                                  senderIdOnMatrix,
                                                                                                  new ArrayList<>(),
                                                                                                  2000L));

    chatNotificationService.onMatrixPushReceived("evt1", roomId, "john", "pushKey");
    chatNotificationService.onMatrixPushReceived("evt2", roomId, "john", "pushKey");

    ArgumentCaptor<PwaDirectNotificationBuilder> builders = ArgumentCaptor.forClass(PwaDirectNotificationBuilder.class);
    verify(mockedPwaNotificationService, times(2)).scheduleDirectNotification(eq("john"),
                                                                              eq(ChatNotificationService.CHAT_NOTIFICATION_KIND),
                                                                              eq(ChatNotificationService.DEFAULT_UNREAD_DELAY_SECONDS),
                                                                              builders.capture());

    // first fire: one popup per room — latest unread message + count of others
    PwaNotificationMessage popup = builders.getAllValues().get(0).build("device1");
    assertNotNull(popup);
    assertNotNull(popup.getTitle());
    assertEquals(roomId, popup.getTag());
    assertTrue(popup.isRenotify());
    assertTrue(popup.getBody().startsWith("second message"));
    assertTrue(popup.getBody().contains("1 more"));
    assertEquals("evt2", popup.getData().get("eventId"));
    // with no app page open, the click lands on the recipient's landing page,
    // without opening the room — not on the space permalink, which is not resolved
    assertEquals("/portal/dw/stream?message=evt2", popup.getUrl());
    verify(mockedPermanentLinkService, never()).getLink(any());
    assertEquals(roomId, popup.getData().get("roomId"));
    // click opens the room in the already-open page instead of reloading it
    assertEquals(ChatNotificationService.OPEN_ROOM_CLIENT_ACTION,
                 popup.getData().get(PwaNotificationService.DIRECT_CLIENT_ACTION_DATA));

    // second fire on the SAME device: already covered by its displayed popup
    assertNull(builders.getAllValues().get(1).build("device1"));

    // another device pops its own copy: coverage is per device
    PwaNotificationMessage secondDevicePopup = builders.getAllValues().get(0).build("device2");
    assertNotNull(secondDevicePopup);
    assertEquals(roomId, secondDevicePopup.getTag());

    // a failed send re-arms the device: the batch pops again on a later fire
    builders.getAllValues().get(0).onSendFailure("device1", popup);
    assertNotNull(builders.getAllValues().get(1).build("device1"));

    // a build failure must not consume the device's popup either
    when(mockedPwaNotificationService.getLocaleConfig(anyString())).thenThrow(new IllegalStateException("boom"))
                                                                   .thenReturn(localeConfig);
    assertThrows(IllegalStateException.class, () -> builders.getAllValues().get(0).build("device3"));
    assertNotNull(builders.getAllValues().get(0).build("device3"));

    // the landing page is resolved at fire time: a failed lookup falls back to
    // the default site, a home carrying a query keeps it, and a home that is not
    // a page of this portal (external link page, fragment, protocol-relative)
    // is replaced by the default site
    when(mockedPortalConfigService.getDefaultPath("john")).thenThrow(new IllegalStateException("no navigation"))
                                                          .thenReturn("/portal/dw/stream?tab=1")
                                                          .thenReturn("https://example.org/home")
                                                          .thenReturn("/portal/dw/stream#top")
                                                          .thenReturn("//example.org/home");
    String defaultSiteUrl = "/portal/dw?message=evt2";
    assertEquals(defaultSiteUrl, builders.getAllValues().get(0).build("device4").getUrl());
    assertEquals("/portal/dw/stream?tab=1&message=evt2",
                 builders.getAllValues().get(0).build("device5").getUrl());
    assertEquals(defaultSiteUrl, builders.getAllValues().get(0).build("device6").getUrl());
    assertEquals(defaultSiteUrl, builders.getAllValues().get(0).build("device7").getUrl());
    assertEquals(defaultSiteUrl, builders.getAllValues().get(0).build("device8").getUrl());

    // a read watermark cancels the pending fires it covers
    when(matrixHttpClient.getEventById("evt3", roomId, accessToken))
                                                                    .thenReturn(new MatrixMessage("evt3",
                                                                                                  roomId,
                                                                                                  "m.room.message",
                                                                                                  "third message",
                                                                                                  "m.text",
                                                                                                  senderIdOnMatrix,
                                                                                                  new ArrayList<>(),
                                                                                                  3000L));
    chatNotificationService.onMatrixPushReceived("evt3", roomId, "john", "pushKey");
    chatNotificationService.clearPendingMessages("john", roomId, 3000L);
    ArgumentCaptor<PwaDirectNotificationBuilder> allBuilders = ArgumentCaptor.forClass(PwaDirectNotificationBuilder.class);
    verify(mockedPwaNotificationService, times(3)).scheduleDirectNotification(eq("john"), anyString(), anyLong(), allBuilders.capture());
    assertNull(allBuilders.getAllValues().get(2).build("device1"));
    assertNull(allBuilders.getAllValues().get(2).build("device2"));
  }

  @Test
  void onMatrixPushReceivedDispatchesMention() throws Exception {
    lenient().when(userStateModel.getStatus()).thenReturn("available");
    lenient().when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    PwaNotificationService mockedPwaNotificationService = mock(PwaNotificationService.class);
    lenient().when(mockedPwaNotificationService.canReceiveDirectNotifications(anyString(), anyString())).thenReturn(true);
    ReflectionTestUtils.setField(chatNotificationService, "pwaNotificationService", mockedPwaNotificationService);
    String eventId = "eventIDOnMatrix";
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    matrixService.saveUserAccount(demoIdentity, true);
    Identity tomIdentity = identityManager.getOrCreateUserIdentity("tom");
    String tomIdOnMatrix = matrixService.saveUserAccount(tomIdentity, true);

    Space space = getSpaceInstance(1);
    spacesToDelete.add(space);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    roomId,
                                                    "m.room.message",
                                                    "This is a chat message",
                                                    "m.text",
                                                    tomIdOnMatrix,
                                                    Collections.singletonList("@demo:matrix.meeds.tn"),
                                                    123456789);
    when(matrixHttpClient.getEventById(eventId, roomId, accessToken)).thenReturn(matrixMessage);

    NotificationContext notificationContext = mock(NotificationContext.class);
    NotificationExecutor notificationExecutor = mock(NotificationExecutor.class);
    NotificationCommand notificationCommand = mock(NotificationCommand.class);
    when(notificationContext.getNotificationExecutor()).thenReturn(notificationExecutor);
    when(notificationContext.makeCommand(any(PluginKey.class))).thenReturn(notificationCommand);
    when(notificationExecutor.with(notificationCommand)).thenReturn(notificationExecutor);
    when(notificationExecutor.execute(notificationContext)).thenReturn(true);
    try (MockedStatic<NotificationContextImpl> notificationContextImpl = mockStatic(NotificationContextImpl.class)) {
      notificationContextImpl.when(NotificationContextImpl::cloneInstance).thenReturn(notificationContext);
      chatNotificationService.onMatrixPushReceived(eventId, roomId, "demo", "pushKey");
    }

    // the mention was dispatched to the standard notification executor
    // targeting the mention plugin (on-site and mail channels)
    ArgumentCaptor<PluginKey> pluginKey = ArgumentCaptor.forClass(PluginKey.class);
    verify(notificationContext, times(1)).makeCommand(pluginKey.capture());
    assertEquals(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN, pluginKey.getValue().getId());
    verify(notificationContext).append(MATRIX_ROOM_MEMBER, "demo");
    verify(notificationContext).append(MATRIX_ROOM_ID, roomId);
    verify(notificationExecutor, times(1)).execute(notificationContext);
    // and the message still schedules the room's deferred popup
    verify(mockedPwaNotificationService, times(1)).scheduleDirectNotification(eq("demo"), anyString(), anyLong(), any());
  }

  @Test
  void markRoomAsReadPostsReceiptRecordsWatermarkAndCancelsPopups() throws Exception {
    lenient().when(userStateModel.getStatus()).thenReturn("available");
    lenient().when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    PwaNotificationService mockedPwaNotificationService = mock(PwaNotificationService.class);
    lenient().when(mockedPwaNotificationService.canReceiveDirectNotifications(anyString(), anyString())).thenReturn(true);
    ReflectionTestUtils.setField(chatNotificationService, "pwaNotificationService", mockedPwaNotificationService);
    LocaleConfigImpl localeConfig = new LocaleConfigImpl();
    localeConfig.setLocale(Locale.ENGLISH);
    localeConfig.setOrientation(Orientation.LT);
    lenient().when(mockedPwaNotificationService.getLocaleConfig(anyString())).thenReturn(localeConfig);

    // the buffer is service state shared by the tests of this class: start clean
    ((java.util.Map<?, ?>) ReflectionTestUtils.getField(chatNotificationService, "pendingMessages")).clear();

    // tom (manager) writes, demo (member) reads; john is not a member
    Identity tomIdentity = identityManager.getOrCreateUserIdentity("tom");
    String senderIdOnMatrix = matrixService.saveUserAccount(tomIdentity, true);
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    matrixService.saveUserAccount(demoIdentity, true);
    Space space = getSpaceInstance(5);
    spacesToDelete.add(space);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    when(matrixHttpClient.getEventById("evtRead", roomId, accessToken))
                                                                       .thenReturn(new MatrixMessage("evtRead",
                                                                                                     roomId,
                                                                                                     "m.room.message",
                                                                                                     "to be read",
                                                                                                     "m.text",
                                                                                                     senderIdOnMatrix,
                                                                                                     new ArrayList<>(),
                                                                                                     5000L));
    chatNotificationService.onMatrixPushReceived("evtRead", roomId, "demo", "pushKey");
    ArgumentCaptor<PwaDirectNotificationBuilder> builders = ArgumentCaptor.forClass(PwaDirectNotificationBuilder.class);
    verify(mockedPwaNotificationService).scheduleDirectNotification(eq("demo"), anyString(), anyLong(), builders.capture());

    // the popup carries the mark-as-read quick action
    PwaNotificationMessage popup = builders.getValue().build("deviceA");
    assertNotNull(popup);
    assertEquals(1, popup.getActions().size());
    assertEquals(ChatNotificationService.MARK_READ_ACTION, popup.getActions().get(0).getAction());
    assertEquals("Mark as read", popup.getActions().get(0).getTitle());

    // demo reads the room up to that event: receipt posted with demo's own token,
    // watermark recorded, the room's pending popups cancelled for every device
    when(matrixHttpClient.getAccessToken(anyString())).thenReturn("sys_demoUserAccessToken");
    chatNotificationService.markRoomAsRead("demo", roomId, "evtRead", 5000L);
    verify(matrixHttpClient).sendReadReceipt(eq(roomId), eq("evtRead"), eq("sys_demoUserAccessToken"));
    verify(settingService).set(eq(Context.USER.id("demo")),
                               eq(USER_CHAT_NOTIFICATION_SCOPE),
                               eq(ChatNotificationService.READ_WATERMARK_KEY_PREFIX + roomId),
                               any());
    assertNull(builders.getValue().build("deviceB"));
    // no push follows a read: a popup on another device closes from the receipt
    // that device gets through sync (a push showing nothing is a silent push)
    // the emptied room buffer leaves the map
    assertFalse(((java.util.Map<?, ?>) ReflectionTestUtils.getField(chatNotificationService, "pendingMessages")).containsKey("demo|"
        + roomId));

    // the durable watermark alone cancels a fire (cluster / restart): a fresh
    // buffer for the room, watermark stored past the message
    chatNotificationService.onMatrixPushReceived("evtRead", roomId, "demo", "pushKey");
    doReturn(SettingValue.create("9999")).when(settingService)
                                          .get(Context.USER.id("demo"),
                                               USER_CHAT_NOTIFICATION_SCOPE,
                                               ChatNotificationService.READ_WATERMARK_KEY_PREFIX + roomId);
    verify(mockedPwaNotificationService, times(2)).scheduleDirectNotification(eq("demo"), anyString(), anyLong(), builders.capture());
    assertNull(builders.getValue().build("deviceC"));

    // a client timestamp in the future is clamped to now
    doReturn(null).when(settingService)
                  .get(Context.USER.id("demo"),
                       USER_CHAT_NOTIFICATION_SCOPE,
                       ChatNotificationService.READ_WATERMARK_KEY_PREFIX + roomId);
    chatNotificationService.markRoomAsRead("demo", roomId, "evtRead", Long.MAX_VALUE);
    @SuppressWarnings({ "unchecked", "rawtypes" })
    ArgumentCaptor<SettingValue<?>> saved = ArgumentCaptor.forClass((Class) SettingValue.class);
    verify(settingService, times(2)).set(eq(Context.USER.id("demo")),
                                         eq(USER_CHAT_NOTIFICATION_SCOPE),
                                         eq(ChatNotificationService.READ_WATERMARK_KEY_PREFIX + roomId),
                                         saved.capture());
    assertTrue(Long.parseLong(String.valueOf(saved.getValue().getValue())) <= System.currentTimeMillis());

    // no receipt posted (user without Matrix account): nothing is marked read
    when(matrixHttpClient.getAccessToken(anyString())).thenReturn(null);
    ((org.exoplatform.services.cache.ExoCache<?, ?>) ReflectionTestUtils.getField(matrixService, "userAccessTokensCache")).clearCache();
    assertThrows(IllegalStateException.class,
                 () -> chatNotificationService.markRoomAsRead("demo", roomId, "evtRead", null));

    // guards of the read anchor
    assertThrows(ObjectNotFoundException.class,
                 () -> chatNotificationService.markRoomAsRead("demo", "!unknown:matrix.meeds.tn", "evt", null));
    assertThrows(IllegalAccessException.class,
                 () -> chatNotificationService.markRoomAsRead("john", roomId, "evtRead", null));
    assertThrows(IllegalArgumentException.class, () -> chatNotificationService.markRoomAsRead("demo", roomId, "", null));
  }

  @Test
  void mentionNotificationsAreExcludedFromPush() {
    // ChatPushNotificationIntegration registers the mention plugin as excluded
    // from the generic PWA push pipeline: mentions keep on-site and mail
    // channels, the push popup stays the room's deferred one
    assertTrue(pwaNotificationService.isPluginExcludedFromPush(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN));
  }

  @Test
  void testIsRoomMutedForUser() {
    String userName = "demo";
    String mutedRoomId = "!mutedRoom:matrix.meeds.tn";
    String otherRoomId = "!otherRoom:matrix.meeds.tn";
    Scope scope = ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
    String key = ChatNotificationService.MUTED_ROOMS;

    // Case: room is muted
    SettingValue settingValue = SettingValue.create(JsonUtils.toJsonString(Set.of(mutedRoomId)));
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(settingValue);

    assertTrue(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));

    // Case: room is NOT muted
    SettingValue otherSettingValue = SettingValue.create(JsonUtils.toJsonString(Set.of(otherRoomId)));
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(otherSettingValue);
    assertFalse(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));

    // Case: no setting found (null)
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(null);
    assertFalse(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));
  }

  @Test
  void testToggleMutePrivateRoom() {
    String userName = "demo";
    String roomId = "!newRoom:matrix.meeds.tn";
    Scope scope = ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
    String key = ChatNotificationService.MUTED_ROOMS;

    final Set<String>[] currentMutedRooms = new Set[] { new HashSet<>() };
    when(settingService.get(eq(Context.USER.id(userName)),
                            eq(scope),
                            eq(key))).thenAnswer(invocation -> SettingValue.create(JsonUtils.toJsonString(currentMutedRooms[0])));

    doAnswer(invocation -> {
      String json = invocation.getArgument(3, SettingValue.class).getValue().toString();
      currentMutedRooms[0] = JsonUtils.OBJECT_MAPPER.readValue(json, new TypeReference<>() {
      });
      return null;
    }).when(settingService).set(any(), any(), any(), any());

    // 1. Mute
    chatNotificationService.toggleMutePrivateRoom(userName, roomId);
    assertTrue(currentMutedRooms[0].contains(roomId));

    // 2. Unmute
    chatNotificationService.toggleMutePrivateRoom(userName, roomId);
    assertFalse(currentMutedRooms[0].contains(roomId));

    verify(settingService, times(2)).set(eq(Context.USER.id(userName)), eq(scope), eq(key), any());
  }
}
