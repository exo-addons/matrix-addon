/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaDirectNotificationBuilder;
import io.meeds.pwa.model.PwaNotificationAction;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.space.plugin.SpacePermanentLinkPlugin;
import io.meeds.social.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static io.meeds.pwa.service.PwaNotificationService.*;

/**
 * This service creates and manages all different notifications related to the
 * chat application
 */
@Service
public class ChatNotificationService {
  private static final Log          LOG                          = ExoLogger.getLogger(ChatNotificationService.class);
  @Autowired
  private MatrixService             matrixService;

  @Autowired
  private IdentityManager           identityManager;

  @Autowired
  private SpaceService              spaceService;

  @Autowired
  ResourceBundleService             resourceBundleService;

  @Autowired
  private PwaNotificationService    pwaNotificationService;

  @Autowired
  private PermanentLinkService      permanentLinkService;

  @Autowired
  private UserPortalConfigService   portalConfigService;

  @Autowired
  private SettingService            settingService;

  private static UserStateService   userStateService;

  private static UserSettingService userSettingService;

  public static String              IN_KEY                       = "matrix.words.in";

  private static final String       USER_STATUS_DO_NOT_DISTURB   = "donotdisturb";

  public static final Scope         USER_CHAT_NOTIFICATION_SCOPE = Scope.APPLICATION.id("ChatNotificationSettings");

  public static final String        MUTED_ROOMS                  = "mutedRooms";

    /**
   * Default delay a message must stay unread before a device pops it, until
   * the per-device setting story provides a per-subscription value.
   */
  public static final long          DEFAULT_UNREAD_DELAY_SECONDS = 300;

  public static final String        CHAT_NOTIFICATION_KIND       = "chat";

  public static final String        SENDS_YOU_A_CHAT_KEY         = "matrix.notification.sendsYouAChat";

  public static final String        MORE_MESSAGES_KEY            = "matrix.notification.moreMessages";

  public static final String        MARK_READ_ACTION             = "markRead";

  public static final String        MARK_READ_LABEL_KEY          = "pwa.notification.action.markAsRead";

  /**
   * In-page action of a room popup click (DOM event name the chat quick-actions
   * bundle listens to, see {@code Constants.js ACTION_OPEN_CHAT_ROOM_FROM_PUSH})
   */
  public static final String        OPEN_ROOM_CLIENT_ACTION      = "meeds-chat-open-room-from-push";

  public static final String        READ_WATERMARK_KEY_PREFIX    = "readWatermark:";

  private static final int          POPUP_BODY_MAX_LENGTH        = 150;

  private static final int          MAX_PENDING_PER_ROOM         = 100;

  private static final Map<String, String> DEFAULT_LABELS        =
                                                          Map.of(SENDS_YOU_A_CHAT_KEY, "{0} sends you a chat",
                                                                 MORE_MESSAGES_KEY, "And {0} more messages",
                                                                   IN_KEY, "in",
                                                                   MARK_READ_LABEL_KEY, "Mark as read");

  /**
   * Unread chat messages already handed to the push scheduler, per
   * user|roomId. Node-local by design: the fire-time guard re-reads it, and a
   * node restart only loses the pending popups of one delay window — the
   * in-app experience is unaffected.
   */
  private final Map<String, PendingRoomMessages> pendingMessages = new ConcurrentHashMap<>();

  /**
   * Single entry point of the Synapse push-gateway callback for one recipient:
   * resolves the event once server-side, then dispatches — a mention
   * additionally produces the standard on-site/mail notification (its push is
   * opted out), and every message schedules a deferred self-contained popup
   * per subscribed device, cancelled at fire time if read meanwhile. One popup
   * per chatroom: it shows the latest unread message and how many more are
   * unread, replacing the room's previous popup on the device.
   *
   * @param eventId the Matrix event ID
   * @param roomId the Matrix room ID
   * @param userName the recipient platform username
   * @param pushKey the recipient device pushkey (JWT), used to resolve private
   *          room events
   */
  public void onMatrixPushReceived(String eventId, String roomId, String userName, String pushKey) {
    Room room = matrixService.getById(roomId);
    if (room == null) {
      LOG.debug("Chat push for {} in {}: unknown room, ignored", userName, roomId);
      return;
    }
    MatrixMessage message = resolveMessage(room, eventId, roomId, pushKey);
    if (message == null) {
      // not an m.room.message event (reaction, state event...) or unresolvable
      LOG.debug("Chat push for {} in {}: event {} not resolvable as a message, ignored", userName, roomId, eventId);
      return;
    }
    String senderUserName = matrixService.findUserByMatrixId(message.getSender());
    if (StringUtils.equals(senderUserName, userName)) {
      LOG.debug("Chat push for {} in {}: own message, ignored", userName, roomId);
      return;
    }
    if (isMentioned(message, userName)) {
      sendMentionNotification(message, room, userName, senderUserName);
    }
    if (!canSendPushNotificationToUser(userName, room)) {
      LOG.debug("Chat push for {} in {}: muted or do-not-disturb, ignored", userName, roomId);
      return;
    }
    if (!pwaNotificationService.canReceiveDirectNotifications(userName, CHAT_NOTIFICATION_KIND)) {
      // nothing can fire (PWA disabled or no subscribed device): don't buffer
      LOG.debug("Chat push for {} in {}: no device can receive chat notifications, ignored", userName, roomId);
      return;
    }
    PendingMessage pending = buildPendingMessage(message, room, senderUserName);
    if (pending == null) {
      LOG.debug("Chat push for {} in {}: pending message could not be built, ignored", userName, roomId);
      return;
    }
    LOG.debug("Chat push for {} in {}: event {} buffered, deferred popup scheduled", userName, roomId, eventId);
    // one atomic step: a concurrent read may evict the room buffer between a
    // lookup and an add, which would orphan the message and lose its popup
    pendingMessages.compute(pendingKey(userName, roomId), (key, buffer) -> {
      PendingRoomMessages roomBuffer = buffer == null ? new PendingRoomMessages() : buffer;
      roomBuffer.add(pending);
      return roomBuffer;
    });
    pwaNotificationService.scheduleDirectNotification(userName,
                                                      CHAT_NOTIFICATION_KIND,
                                                      DEFAULT_UNREAD_DELAY_SECONDS,
                                                      new PwaDirectNotificationBuilder() {
                                                        @Override
                                                        public PwaNotificationMessage build(String subscriptionId) {
                                                          return buildRoomPopup(userName, roomId, subscriptionId, pending.timestamp());
                                                        }

                                                        @Override
                                                        public void onSendFailure(String subscriptionId, PwaNotificationMessage popup) {
                                                          reopenRoomPopup(userName, roomId, subscriptionId, popup);
                                                        }
                                                      });
  }

  /**
   * Marks a room as read for a user up to an event — the server-side read
   * anchor: (1) the {@code m.read} receipt is posted with the user's own Matrix
   * identity so every client converges through sync, (2) the read watermark is
   * recorded durably, (3) the pending popups covered by it are discarded.
   * Idempotent; the watermark only moves forward.
   *
   * @param userName the platform user
   * @param roomId the Matrix room id
   * @param eventId the event read up to (high-water mark)
   * @param readTimestamp the timestamp of that event when known, else now
   * @throws ObjectNotFoundException when the room is unknown
   * @throws IllegalAccessException when the user is not a member of the room
   */
  public void markRoomAsRead(String userName, String roomId, String eventId, Long readTimestamp) throws ObjectNotFoundException,
                                                                                                 IllegalAccessException {
    if (StringUtils.isAnyBlank(userName, roomId, eventId)) {
      throw new IllegalArgumentException("matrix.markRoomAsRead.invalidParameters");
    }
    Room room = matrixService.getById(roomId);
    if (room == null) {
      throw new ObjectNotFoundException("Room " + roomId + " not found");
    }
    if (!isRoomMember(userName, room)) {
      throw new IllegalAccessException("User " + userName + " is not a member of room " + roomId);
    }
    // a client-supplied timestamp never moves the watermark past "now": a
    // future value would silence the room for good
    long now = System.currentTimeMillis();
    long watermark = readTimestamp == null || readTimestamp <= 0 ? now : Math.min(readTimestamp, now);
    if (!matrixService.markRoomAsRead(userName, room.getRoomId(), eventId)) {
      // the receipt is the read anchor: without it nothing is marked read
      throw new IllegalStateException("matrix.markRoomAsRead.receiptNotPosted");
    }
    saveReadWatermark(userName, roomId, watermark);
    clearPendingMessages(userName, roomId, watermark);
    // popups already displayed on other devices close from the receipt those
    // devices receive through sync — never through a push showing nothing
  }

  private boolean isRoomMember(String userName, Room room) {
    if (room.getSpaceId() != null) {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      return space != null && spaceService.isMember(space, userName);
    }
    return StringUtils.equals(userName, room.getFirstParticipant()) || StringUtils.equals(userName, room.getSecondParticipant());
  }

  private long getReadWatermark(String userName, String roomId) {
    SettingValue<?> value = settingService.get(Context.USER.id(userName),
                                               USER_CHAT_NOTIFICATION_SCOPE,
                                               READ_WATERMARK_KEY_PREFIX + roomId);
    if (value == null || value.getValue() == null) {
      return 0;
    }
    try {
      return Long.parseLong(String.valueOf(value.getValue()));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void saveReadWatermark(String userName, String roomId, long timestamp) {
    // monotonic: a late or concurrent read never moves the watermark back
    if (timestamp > getReadWatermark(userName, roomId)) {
      settingService.set(Context.USER.id(userName),
                         USER_CHAT_NOTIFICATION_SCOPE,
                         READ_WATERMARK_KEY_PREFIX + roomId,
                         SettingValue.create(String.valueOf(timestamp)));
    }
  }

  /**
   * Discards the pending popups of a room up to a timestamp: their deferred
   * sends are cancelled at fire time. Called when the room is read up to that
   * point (read-anchor story).
   *
   * @param userName the recipient platform username
   * @param roomId the Matrix room ID
   * @param upToTimestamp read watermark (inclusive)
   */
  public void clearPendingMessages(String userName, String roomId, long upToTimestamp) {
    // an emptied buffer leaves the map: read rooms cost nothing, and the next
    // message starts a fresh batch on every device
    pendingMessages.computeIfPresent(pendingKey(userName, roomId), (key, pending) -> {
      pending.removeUpTo(upToTimestamp);
      return pending.isEmpty() ? null : pending;
    });
  }

  private static String pendingKey(String userName, String roomId) {
    return userName + "|" + roomId;
  }

  private MatrixMessage resolveMessage(Room room, String eventId, String roomId, String pushKey) {
    if (room.getSpaceId() != null) {
      return matrixService.getRoomEvent(eventId, roomId, null);
    }
    String accessToken = null;
    try {
      accessToken = matrixService.getAccessToken(pushKey);
    } catch (JsonException | IOException e) {
      LOG.error("Could not get Matrix Access token for the administrator account !", e);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.error("Could not get Matrix Access token for the administrator account !", interruptedException);
    }
    if (StringUtils.isBlank(accessToken)) {
      return null;
    }
    try {
      return matrixService.getRoomEvent(eventId, roomId, accessToken);
    } finally {
      matrixService.invalidateAccessToken(accessToken);
    }
  }

  private PendingMessage buildPendingMessage(MatrixMessage message, Room room, String senderUserName) {
    boolean spaceRoom = room.getSpaceId() != null;
    String senderFullName;
    String roomName = null;
    String icon;
    if (spaceRoom) {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      if (space == null) {
        return null;
      }
      Identity senderIdentity = matrixService.findSpaceMemberByMatrixId(message.getSender(), space);
      senderFullName = senderIdentity != null ? senderIdentity.getProfile().getFullName() : message.getSender();
      roomName = space.getDisplayName();
      icon = space.getAvatarUrl();
    } else {
      Identity senderIdentity = identityManager.getOrCreateUserIdentity(senderUserName);
      senderFullName = senderIdentity != null ? senderIdentity.getProfile().getFullName() : message.getSender();
      icon = senderIdentity != null ? senderIdentity.getProfile().getAvatarUrl() : null;
    }
    return new PendingMessage(message.getEventId(),
                              message.getTimeStamp(),
                              senderFullName,
                              roomName,
                              spaceRoom,
                              StringUtils.abbreviate(message.getMessageContent(), POPUP_BODY_MAX_LENGTH),
                              icon);
  }

  /**
   * Where a popup click lands when no app page is open: the page the user
   * lands on when opening the app (their home page, else the default site
   * node — what a bare /portal redirects to). The room is not opened there,
   * which is why the url carries no {@code roomId}: with the app closed the
   * click opens the app, opening the room is the in-page client action. A
   * home that is not an absolute path of this origin (an external link page,
   * a fragment) is replaced by the default site, since the service worker
   * prefixes the url with the origin. {@code message} carries the notified
   * event for a future scroll-to-message; no client reads it today, and such
   * a client would need the room back in the url to fetch the event.
   */
  private String getHomeLink(String userName, String eventId) {
    String home = null;
    try {
      home = portalConfigService.getDefaultPath(userName);
    } catch (Exception e) {
      LOG.warn("Default path of {} unavailable ({}), the chat popup opens the default site instead",
               userName,
               e.getMessage());
    }
    if (!isPortalPagePath(home)) {
      home = "/portal/" + portalConfigService.getMetaPortal();
    }
    return home + (home.contains("?") ? "&" : "?") + "message=" + eventId;
  }

  private static boolean isPortalPagePath(String path) {
    return StringUtils.isNotBlank(path)
           && path.startsWith("/")
           && !path.startsWith("//")
           && !path.contains("#");
  }

  private PwaNotificationMessage buildRoomPopup(String userName, String roomId, String subscriptionId, long messageTimestamp) {
    PendingRoomMessages pending = pendingMessages.get(pendingKey(userName, roomId));
    if (pending == null) {
      LOG.debug("Chat popup for {} in {} on device {}: no pending messages, cancelled", userName, roomId, subscriptionId);
      return null;
    }
    // the durable read watermark is the fire-time guard's authoritative input
    // (a read recorded on another node or before a restart is seen here)
    long readWatermark = getReadWatermark(userName, roomId);
    if (readWatermark > 0) {
      pending.removeUpTo(readWatermark);
      if (pending.isEmpty()) {
        pendingMessages.computeIfPresent(pendingKey(userName, roomId), (key, buffer) -> buffer.isEmpty() ? null : buffer);
      }
    }
    PendingRoomMessages.Snapshot snapshot = pending.coverIfNotifiable(subscriptionId, messageTimestamp);
    if (snapshot == null) {
      LOG.debug("Chat popup for {} in {} on device {}: read or already covered, cancelled", userName, roomId, subscriptionId);
      // read meanwhile, or already covered by this device's room popup of a
      // newer fire (coverage is per device: each subscribed device pops once)
      return null;
    }
    PendingMessage latest = snapshot.latest();
    try {
      LocaleConfig localeConfig = pwaNotificationService.getLocaleConfig(userName);
      Locale locale = localeConfig.getLocale();
      String title = latest.spaceRoom() ? latest.senderFullName() + " " + formatLabel(IN_KEY, locale, "")
          + " " + latest.roomName() : formatLabel(SENDS_YOU_A_CHAT_KEY, locale, latest.senderFullName());
      String body = latest.body();
      if (snapshot.moreCount() > 0) {
        body += "\n" + formatLabel(MORE_MESSAGES_KEY, locale, String.valueOf(snapshot.moreCount()));
      }
      PwaNotificationMessage popup = new PwaNotificationMessage();
      popup.setTitle(title);
      popup.setBody(body);
      popup.setIcon(latest.icon());
      popup.setUrl(getHomeLink(userName, latest.eventId()));
      // one popup per room; the tag is also the object the "mark as read" action
      // token is scoped to (pwa hands it back as the trusted room id)
      popup.setTag(roomId);
      popup.setRenotify(true);
      popup.setLang(locale.toLanguageTag());
      // quick action: shown where the OS supports notification actions
      popup.setActions(List.of(new PwaNotificationAction(formatLabel(MARK_READ_LABEL_KEY, locale, ""), MARK_READ_ACTION)));
      popup.setData(Map.of("roomId", roomId,
                           "eventId", latest.eventId(),
                           "ts", String.valueOf(latest.timestamp()),
                           // click: open the room in the page already open, or
                           // follow the url (which opens the room on load)
                           PwaNotificationService.DIRECT_CLIENT_ACTION_DATA, OPEN_ROOM_CLIENT_ACTION));
      return popup;
    } catch (Exception e) {
      // a failed build must not consume the device's popup: re-arm and rethrow
      pending.reopenCover(subscriptionId, latest.timestamp());
      throw e;
    }
  }

  private void reopenRoomPopup(String userName, String roomId, String subscriptionId, PwaNotificationMessage popup) {
    PendingRoomMessages pending = pendingMessages.get(pendingKey(userName, roomId));
    if (pending == null || popup == null || popup.getData() == null) {
      return;
    }
    String coveredTimestamp = popup.getData().get("ts");
    if (coveredTimestamp != null) {
      // the push never reached the device's push service: make the batch
      // notifiable again for that device unless a newer fire covered further
      pending.reopenCover(subscriptionId, Long.parseLong(coveredTimestamp));
    }
  }

  private String formatLabel(String key, Locale locale, String param) {
    String pattern = resourceBundleService.getSharedString(key, locale);
    if (StringUtils.isBlank(pattern) || StringUtils.equals(pattern, key)) {
      pattern = DEFAULT_LABELS.get(key);
    }
    return pattern.replace("{0}", param);
  }

  private record PendingMessage(String eventId,
                                long timestamp,
                                String senderFullName,
                                String roomName,
                                boolean spaceRoom,
                                String body,
                                String icon) {
  }

  private static final class PendingRoomMessages {

    private final NavigableMap<Long, PendingMessage> messages               = new TreeMap<>();

    /**
     * Per-device "popped up to" watermark: each subscribed device shows the
     * room popup once per message batch, whatever the other devices did.
     */
    private final Map<String, Long>                  coveredBySubscription  = new HashMap<>();

    synchronized void add(PendingMessage message) {
      messages.put(message.timestamp(), message);
      while (messages.size() > MAX_PENDING_PER_ROOM) {
        messages.pollFirstEntry();
      }
    }

    synchronized void removeUpTo(long timestamp) {
      messages.headMap(timestamp, true).clear();
    }

    synchronized boolean isEmpty() {
      return messages.isEmpty();
    }

    synchronized Snapshot coverIfNotifiable(String subscriptionId, long timestamp) {
      long covered = coveredBySubscription.getOrDefault(subscriptionId, 0l);
      if (timestamp <= covered || !messages.containsKey(timestamp)) {
        return null;
      }
      Map.Entry<Long, PendingMessage> last = messages.lastEntry();
      coveredBySubscription.put(subscriptionId, last.getKey());
      return new Snapshot(last.getValue(), messages.size() - 1);
    }

    synchronized void reopenCover(String subscriptionId, long expectedCovered) {
      Long covered = coveredBySubscription.get(subscriptionId);
      if (covered != null && covered == expectedCovered) {
        coveredBySubscription.remove(subscriptionId);
      }
    }

    record Snapshot(PendingMessage latest, int moreCount) {
    }
  }

  private boolean isMentioned(MatrixMessage message, String userName) {
    Identity receiverIdentity = identityManager.getOrCreateUserIdentity(userName);
    String matrixReceiverId = userName;
    if (receiverIdentity != null
        && StringUtils.isNotBlank((String) receiverIdentity.getProfile().getProperties().get(USER_MATRIX_ID))) {
      matrixReceiverId = matrixService.getUserFullMatrixID((String) receiverIdentity.getProfile()
                                                                                    .getProperties()
                                                                                    .get(USER_MATRIX_ID));
    }
    return isUserIncludedInMentions(message, matrixReceiverId);
  }

  /**
   * Creates the standard mention notification (on-site and mail channels; its
   * push delivery is opted out so the popup stays the room's deferred one).
   */
  private boolean sendMentionNotification(MatrixMessage message, Room room, String userName, String senderUserName) {
    Identity senderIdentity = identityManager.getOrCreateUserIdentity(senderUserName);
    String senderFullName = senderIdentity != null ? senderIdentity.getProfile().getFullName() : "";
    String roomName;
    String roomAvatarUrl = "";
    if (room.getSpaceId() != null) {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      roomName = space.getDisplayName();
      roomAvatarUrl = space.getAvatarUrl();
    } else if (senderIdentity != null) {
      roomName = senderIdentity.getProfile().getFullName();
      roomAvatarUrl = senderIdentity.getProfile().getAvatarUrl();
    } else {
      roomName = message.getSender();
    }

    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(MATRIX_ROOM_ID, message.getRoomId());
    ctx.append(MATRIX_MESSAGE_SENDER, senderUserName);
    ctx.append(MATRIX_ROOM_NAME, roomName);
    ctx.append(MATRIX_ROOM_TYPE, room.getSpaceId() != null ? "SPACE" : "ONE_TO_ONE");
    ctx.append(MATRIX_ROOM_AVATAR, roomAvatarUrl);
    ctx.append(MATRIX_MESSAGE_CONTENT, message.getMessageContent());
    ctx.append(MATRIX_ROOM_MEMBER, userName);
    ctx.append(MATRIX_MESSAGE_SENDER_FULLNAME, senderFullName);
    String permalink = getMessageLink(message);
    ctx.append(MATRIX_MESSAGE_URL, StringUtils.isNotBlank(permalink) ? permalink : "");
    return ctx.getNotificationExecutor()
              .with(ctx.makeCommand(PluginKey.key(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN)))
              .execute(ctx);
  }

  private String getMessageLink(MatrixMessage message) {
    String urlFormat = "%s?roomId=%s&message=%s";
    Room room = matrixService.getById(message.getRoomId());
    String link = "";
    try {
      if (room.getSpaceId() != null) {
        link = permanentLinkService.getLink(new PermanentLinkObject(SpacePermanentLinkPlugin.OBJECT_TYPE, String.valueOf(room.getSpaceId())));
        return urlFormat.formatted(link, room.getRoomId(), message.getEventId());
      } else {
        String sender;
        sender = matrixService.findUserByMatrixId(message.getSender());
        link = String.format("/portal/%s/profile/%s", this.portalConfigService.getMetaPortal(), sender);
      }
    } catch (Exception e) {
      link = String.format("/portal/%s", portalConfigService.getMetaPortal());
    }
      return urlFormat.formatted(link, room.getRoomId(), message.getEventId());
  }
  
  private boolean isUserIncludedInMentions(MatrixMessage message, String matrixReceiverId) {
      if (message.getMentionedUsers() == null || message.getMentionedUsers().isEmpty()) {
        return false;
      }

      boolean isUserMentioned = false;
      for (String mentionedMatrixId : message.getMentionedUsers()) {
        mentionedMatrixId = "@" + mentionedMatrixId.substring(1).replace("@", "-");// In case the username is the user email
        isUserMentioned = mentionedMatrixId.equals(matrixReceiverId);
      }
      return isUserMentioned;
    }

  public boolean isPrivateRoomMutedForUser(String userName, String roomId) {
    return getMutedRooms(userName).contains(roomId);
  }

  public void toggleMutePrivateRoom(String userName, String roomId) {
    Set<String> mutedRoomIds = new HashSet<>(getMutedRooms(userName));
    boolean changed;
    changed = mutedRoomIds.remove(roomId);
    if (!changed) {
      changed = mutedRoomIds.add(roomId);
    }
    if (changed) {
      settingService.set(Context.USER.id(userName),
                         USER_CHAT_NOTIFICATION_SCOPE,
                         MUTED_ROOMS,
                         SettingValue.create(JsonUtils.toJsonString(mutedRoomIds)));
    }
  }

  private Set<String> getMutedRooms(String userName) {
    try {
      SettingValue<?> settingValue = settingService.get(Context.USER.id(userName), USER_CHAT_NOTIFICATION_SCOPE, MUTED_ROOMS);
      if (settingValue == null || settingValue.getValue() == null) {
        return Collections.emptySet();
      }
      return JsonUtils.OBJECT_MAPPER.readValue(settingValue.getValue().toString(), new TypeReference<>() {
      });
    } catch (Exception e) {
      LOG.error("Error reading muted rooms setting value for user {}", userName, e);
      return Collections.emptySet();
    }
  }

  private boolean canSendPushNotificationToUser(String userName, Room room) {
    if (room == null) {
      return false;
    }
    boolean roomMuted;
    if (room.getSpaceId() != null) {
      UserSetting userSetting = getUserSettingService().get(userName);
      roomMuted = userSetting != null && userSetting.isSpaceMuted(room.getSpaceId());
    } else {
      roomMuted = isPrivateRoomMutedForUser(userName, room.getRoomId());
    }
    UserStateModel userStatus = getUserStateService().getUserState(userName);
    return !userStatus.getStatus().equals(USER_STATUS_DO_NOT_DISTURB) && !roomMuted;
  }

  private static UserStateService getUserStateService() {
    if (userStateService == null) {
      userStateService = CommonsUtils.getService(UserStateService.class);
    }
    return userStateService;
  }

  private static UserSettingService getUserSettingService() {
    if (userSettingService == null) {
      userSettingService = CommonsUtils.getService(UserSettingService.class);
    }
    return userSettingService;
  }
}
