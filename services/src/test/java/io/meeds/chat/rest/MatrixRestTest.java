/**
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
package io.meeds.chat.rest;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_JWT_SECRET;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.doThrow;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.rest.api.EntityBuilder;
import org.exoplatform.social.rest.api.RestUtils;

import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.ChatSearchResult;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.ChatNotificationService;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.MatrixSynchronizationService;
import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.service.model.LastMessage;
import io.meeds.chat.service.model.Member;
import io.meeds.chat.service.model.Presence;
import io.meeds.chat.service.model.RoomEntity;
import io.meeds.chat.service.model.RoomList;
import io.meeds.chat.service.model.SpaceTemplateSetting;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;

import jakarta.servlet.Filter;
import lombok.SneakyThrows;

@SpringBootTest(classes = { MatrixRest.class, PortalAuthenticationManager.class })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MatrixRestTest {

  private static final String          SIMPLE_USER   = "user";

  private static final String          ADMIN_USER    = "admin";

  private static final String          TEST_PASSWORD = "testPassword";

  private static final String          REST_PATH     = "/matrix";     // NOSONAR

  static final ObjectMapper            OBJECT_MAPPER;

  @Autowired
  private SecurityFilterChain          filterChain;

  @Autowired
  private WebApplicationContext        context;

  @MockitoBean
  private SpaceService                 spaceService;

  @MockitoBean
  private MatrixService                matrixService;

  @MockitoBean
  private MatrixSynchronizationService matrixSynchronizationService;

  @MockitoBean
  private IdentityManager              identityManager;

  @MockitoBean
  private ResourceBundleService        resourceBundleService;

  @MockitoBean
  private NotificationService          notificationService;

  @MockitoBean
  private ChatNotificationService      chatNotificationService;

  @MockitoBean
  PwaNotificationService               pwaNotificationService;

  MockedStatic<LinkProvider>           LINK_PROVIDER;

  MockedStatic<RestUtils>              REST_UTILS;

  MockedStatic<EntityBuilder>          ENTITY_BUILDER;

  private MockMvc                      mockMvc;

  static {
    // Workaround when Jackson is defined in shared library with different
    // version and without artifact jackson-datatype-jsr310
    OBJECT_MAPPER = JsonMapper.builder()
                              .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
                              .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                              .build();
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filterChain.getFilters().toArray(new Filter[0])).build();
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.meeds.tn");

    LINK_PROVIDER = mockStatic(LinkProvider.class);
    REST_UTILS = mockStatic(RestUtils.class);
    ENTITY_BUILDER = mockStatic(EntityBuilder.class);
  }

  @AfterEach
  void tearDown() {
    LINK_PROVIDER.close();
    REST_UTILS.close();
    ENTITY_BUILDER.close();
  }

  @Test
  public void testProcessRooms() throws Exception {
    RoomEntity roomEntity1 = createRoomEntity(1);
    roomEntity1.setSpaceId(1L);
    RoomEntity roomEntity2 = createRoomEntity(2);
    roomEntity2.setSpaceId(2L);
    RoomEntity roomEntity3 = createRoomEntity(3);
    roomEntity3.setSpaceId(3L);
    Room room1 = new Room();
    room1.setRoomId("!testRoom1:matrix.meeds.tn");
    room1.setSpaceId(1L);
    room1.setStatus(RoomStatus.ENABLED.name());
    Room room2 = new Room();
    room2.setRoomId("!testRoom2:matrix.meeds.tn");
    room2.setSpaceId(2L);
    room2.setStatus(RoomStatus.ENABLED.name());
    Room room3 = new Room();
    room3.setRoomId("!testRoom3:matrix.meeds.tn");
    room3.setSpaceId(3L);
    room3.setStatus(RoomStatus.ENABLED.name());

    when(matrixService.getById("!testRoom1", true)).thenReturn(room1);
    when(matrixService.getById("!testRoom1")).thenReturn(room1);
    when(matrixService.getById("!testRoom2", true)).thenReturn(room2);
    when(matrixService.getById("!testRoom2")).thenReturn(room2);
    when(matrixService.getById("!testRoom3", true)).thenReturn(room3);
    when(matrixService.getById("!testRoom3")).thenReturn(room3);
    Space space1 = new Space();
    space1.setDisplayName("Space of Heroes 1");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(matrixService.getRoomBySpaceId(1L)).thenReturn(room1);
    Space space2 = new Space();
    space2.setDisplayName("Space of Heroes 2");
    space2.setAvatarUrl("/Url/Of/Avatar.png");
    space2.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("2")).thenReturn(space2);
    when(matrixService.getRoomBySpaceId(2L)).thenReturn(room2);
    Space space3 = new Space();
    space3.setDisplayName("Space of Heroes 3");
    space3.setAvatarUrl("/Url/Of/Avatar.png");
    space3.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("3")).thenReturn(space3);
    when(matrixService.getRoomBySpaceId(3L)).thenReturn(room3);

    RoomEntity privateRoomEntity1 = createRoomEntity(4);
    privateRoomEntity1.setDirectChat(true);
    Room privateRoom1 = new Room();
    privateRoom1.setRoomId("!testRoom4:matrix.meeds.tn");
    privateRoom1.setSpaceId(null);
    privateRoom1.setFirstParticipant(SIMPLE_USER);
    privateRoom1.setSecondParticipant("user2");
    privateRoom1.setStatus(RoomStatus.ENABLED.name());
    createUserIdentity("user2");
    when(matrixService.getById("!testRoom4", true)).thenReturn(privateRoom1);
    when(matrixService.getById("!testRoom4")).thenReturn(privateRoom1);

    RoomList roomsList = new RoomList();
    roomsList.setTotalUnreadMessages(5);
    roomsList.setRooms(List.of(roomEntity1, roomEntity2, privateRoomEntity1));

    when(spaceService.getMemberSpacesIds(SIMPLE_USER,
                                         0,
                                         -1)).thenReturn(new ArrayList<>(List.of(new String[] { "1", "2", "3" })));
    ResultActions response = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                              .contentType(MediaType.APPLICATION_JSON)
                                                                              .content(asJsonString(roomsList)));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    // we should have roomEntity3 inside the response RoomList
    RoomList expectedRoomList = fromJsonString(response.andReturn().getResponse().getContentAsString(), RoomList.class);
    assertNotNull(expectedRoomList);
    assertNotNull(expectedRoomList.getRooms());
    assertEquals(4, expectedRoomList.getRooms().size());
    assertEquals(5, expectedRoomList.getTotalUnreadMessages());

    //
    Room room = new Room();
    room.setSpaceId(null);
    room.setFirstParticipant("root");
    room.setSecondParticipant("user");
    createUserIdentity("root");

    ResultActions response1 = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(asJsonString(roomsList)));
    response1.andExpect(status().isOk());
  }

  private void createUserIdentity(String userName) {
    Identity identity = new Identity();
    identity.setRemoteId(userName);
    identity.setId("1");
    Profile profile = new Profile();
    profile.setAvatarUrl("/avatar/of/root");
    profile.setProperty("firstName", userName);
    profile.setProperty("lastName", "The king");
    identity.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(identity);
  }

  private RoomEntity createRoomEntity(int index) {
    RoomEntity room = new RoomEntity();
    room.setId("!testRoom" + index + ":matrix.meeds.tn");
    room.setAvatarUrl("/avatar/" + index);
    room.setName("Chat number " + index);
    Member root = new Member("1", "userId", "matrixId", "root", "/user/avatar" + 1, System.currentTimeMillis());
    Member user = new Member("2", "userId", "matrixId", "user", "/user/avatar" + 2, System.currentTimeMillis());
    room.setMembers(Arrays.asList(user, root));
    room.setUnreadMessages(index);
    room.setPresence("online");
    room.setTopic("No topic");
    room.setUpdated(System.currentTimeMillis());
    LastMessage lastMessage = new LastMessage();
    lastMessage.setContent("This is a new message");
    lastMessage.setSender("@root:matrix.meeds.tn");
    room.setLastMessage(lastMessage);
    room.setStatus(RoomStatus.ENABLED.name());
    return room;
  }

  private RequestPostProcessor simpleUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD).authorities(new SimpleGrantedAuthority("users"));
  }

  private RequestPostProcessor adminUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD).authorities(new SimpleGrantedAuthority("administrators"));
  }

  @SneakyThrows
  public static String asJsonString(final Object obj) {
    return OBJECT_MAPPER.writeValueAsString(obj);
  }

  @Test
  void searchMessagesRequiresQuery() throws Exception {
    // Missing the required "query" parameter -> 400 Bad Request
    mockMvc.perform(get(REST_PATH + "/search").with(simpleUser()).contentType(MediaType.APPLICATION_JSON))
           .andExpect(status().isBadRequest());
  }

  @Test
  void searchMessagesEnrichesResultsWithConversationAvatar() throws Exception {
    // One hit in a space room (enriched with an avatar) and one in an unresolvable
    // room (getById -> null, left without an avatar): exercises both branches of the
    // avatar-enrichment loop added for the unified-search cards.
    ChatSearchResult spaceHit = new ChatSearchResult();
    spaceHit.setConversationId("!searchRoom1");
    spaceHit.setText("hello from the space room");
    ChatSearchResult orphanHit = new ChatSearchResult();
    orphanHit.setConversationId("!orphanRoom");
    orphanHit.setText("hello from a gone room");

    when(matrixService.searchChatMessages(eq(SIMPLE_USER), eq("hello"), isNull(), anyInt())).thenReturn(List.of(spaceHit,
                                                                                                               orphanHit));

    Room spaceRoom = new Room();
    spaceRoom.setRoomId("!searchRoom1:matrix.meeds.tn");
    spaceRoom.setSpaceId(1L);
    spaceRoom.setStatus(RoomStatus.ENABLED.name());
    when(matrixService.getById("!searchRoom1", true)).thenReturn(spaceRoom);
    when(matrixService.getRoomBySpaceId(1L)).thenReturn(spaceRoom);
    when(matrixService.getById("!orphanRoom", true)).thenReturn(null);
    Space space = new Space();
    space.setDisplayName("Space of Heroes 1");
    space.setAvatarUrl("/Url/Of/Avatar.png");
    space.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("1")).thenReturn(space);

    ResultActions response = mockMvc.perform(get(REST_PATH + "/search").with(simpleUser())
                                                                       .contentType(MediaType.APPLICATION_JSON)
                                                                       .param("query", "hello"));
    response.andExpect(status().isOk());
    List<?> results = fromJsonString(response.andReturn().getResponse().getContentAsString(), List.class);
    assertNotNull(results);
    assertEquals(2, results.size());
  }

  @Test
  void updatePresenceStatus() throws Exception {
    Presence presence = new Presence();
    presence.setPresence("online");
    presence.setStatusMessage("I am available");
    presence.setUserIdOnMatrix("@user:matrix.meeds.tn");
    when(matrixService.updateUserPresence(anyString(), anyString(), anyString())).thenReturn(presence.getPresence());

    ResultActions response = mockMvc.perform(put(REST_PATH + "/setStatus").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                          .content(asJsonString(presence)));
    response.andExpect(status().isOk());
    response.andExpect(content().string("online"));
  }

  @Test
  void getRoomById() throws Exception {
    String roomId = "!testRoomIdentifier:matrix.meeds.tn";
    ResultActions response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON)
                                                                         .param("roomId", roomId));
    response.andExpect(status().isForbidden());

    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId(1L);

    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(matrixService.getById(roomId)).thenReturn(room);
    when(matrixService.canAccess(eq(room), anyString())).thenReturn(true);

    ResultActions response1 = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                          .param("roomId", roomId));
    response1.andExpect(status().isOk());
  }

  @Test
  void getDirectMessagingRoom() throws Exception {
    String roomId = "!testRoomIdentifier:matrix.meeds.tn";
    ResultActions response = mockMvc.perform(get(REST_PATH + "/dmRoom").with(simpleUser())
                                                                       .contentType(MediaType.APPLICATION_JSON)
                                                                       .param("firstParticipant", "userOne")
                                                                       .param("secondParticipant", "userTwo"));
    response.andExpect(status().isNotFound());

    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId(null);
    room.setFirstParticipant("userOne");
    room.setSecondParticipant("userTwo");
    when(matrixService.getDirectMessagingRoom(eq("userOne"), eq("userTwo"))).thenReturn(room);
    ResultActions response1 = mockMvc.perform(get(REST_PATH + "/dmRoom").with(simpleUser())
                                                                        .contentType(MediaType.APPLICATION_JSON)
                                                                        .param("firstParticipant", "userOne")
                                                                        .param("secondParticipant", "userTwo"));
    response1.andExpect(status().isOk());
  }

  @Test
  void syncUsersAndSpaces() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/sync").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getMatrixRoomBySpaceId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH).with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH).with(simpleUser()).param("spaceId", "1").contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());

    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(spaceService.isMember(space, SIMPLE_USER)).thenReturn(true);
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId(1L);
    when(matrixService.getRoomBySpace(space)).thenReturn(room);
    response = mockMvc.perform(get(REST_PATH).with(simpleUser()).param("spaceId", "1").contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void linkSpaceToRoom() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());
    response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                           .param("spaceGroupId", "groupOne")
                                                           .param("roomId", "!roomIdenitifier:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());
    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceByGroupId("/spaces/groupOne")).thenReturn(space);
    response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                           .param("spaceGroupId", "groupOne")
                                                           .param("roomId", "!roomIdenitifier:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getByRoomId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                           .param("roomId", "!roomIdentifier")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId(1L);
    when(matrixService.getById("!testRoom:matrix.meeds.tn")).thenReturn(room);
    when(matrixService.canAccess(room, SIMPLE_USER)).thenReturn(true);
    response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                           .param("roomId", "!testRoom:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getByRoom() throws Exception {
    ResultActions response =
                           mockMvc.perform(get(REST_PATH + "/byRoom").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH + "/byRoom").with(simpleUser())
                                                         .param("roomId", "!roomIdentifier:matrix.exo.tn")
                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());

    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId(1L);
    when(matrixService.getById("!testRoom")).thenReturn(room);
    when(matrixService.canAccess(room, SIMPLE_USER)).thenReturn(true);
    response = mockMvc.perform(get(REST_PATH + "/byRoom").with(simpleUser())
                                                         .param("roomId", "!testRoom:matrix.meeds.tn")
                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());

    Space space1 = new Space();
    space1.setId(1);
    space1.setDisplayName("Space of Heroes");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById(1)).thenReturn(space1);
    Identity spaceIdentity = new Identity("spaceOne", SpaceIdentityProvider.NAME);
    when(identityManager.getOrCreateSpaceIdentity(space1.getPrettyName())).thenReturn(spaceIdentity);

    response = mockMvc.perform(get(REST_PATH + "/byRoom").with(simpleUser())
                                                         .param("roomId", "!testRoom:matrix.meeds.tn")
                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());

    room.setSpaceId(null);
    room.setFirstParticipant(SIMPLE_USER);
    room.setSecondParticipant(ADMIN_USER);

    Identity userIdentity = new Identity(OrganizationIdentityProvider.NAME, ADMIN_USER);
    when(identityManager.getOrCreateUserIdentity(ADMIN_USER)).thenReturn(userIdentity);

    response = mockMvc.perform(get(REST_PATH + "/byRoom").with(simpleUser())
                                                         .param("roomId", "!testRoom:matrix.meeds.tn")
                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getUserDirectMessagingRooms() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                                        .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                          .param("user", "john")
                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().string("{}"));

    Room room = new Room();
    room.setRoomId("!ThisIsARoom:matrix.meeds.tn");
    room.setSpaceId(null);
    room.setFirstParticipant("userOne");
    room.setSecondParticipant(SIMPLE_USER);
    when(matrixService.getMatrixDMRoomsOfUser(SIMPLE_USER)).thenReturn(Collections.singletonList(room));

    Identity userIdentity = new Identity();
    userIdentity.setRemoteId("userOne");
    userIdentity.setId("1");
    Profile profile = new Profile(userIdentity);
    profile.getProperties().put(USER_MATRIX_ID, "userOne");
    userIdentity.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity("userOne")).thenReturn(userIdentity);

    response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                          .param("user", SIMPLE_USER)
                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().string("{\"@userOne:matrix.meeds.tn\":[\"!ThisIsARoom:matrix.meeds.tn\"]}"));
  }

  @SneakyThrows
  public static final <T> T fromJsonString(String value, Class<T> resultClass) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return OBJECT_MAPPER.readValue(value, resultClass);
  }

  @Test
  void enableChat() throws Exception {
    ResultActions response = mockMvc.perform(put(REST_PATH + "/enable/1").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Space space1 = new Space();
    space1.setId(1);
    space1.setDisplayName("Space of Heroes");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId(1L);
    when(matrixService.enableSpaceChat(space1, true)).thenReturn(room);
    when(matrixService.getRoomBySpace(space1, true)).thenReturn(room);

    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(spaceService.canManageSpace(space1, SIMPLE_USER)).thenReturn(true);

    response = mockMvc.perform(put(REST_PATH + "/enable/1").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void disableChat() throws Exception {
    ResultActions response = mockMvc.perform(put(REST_PATH + "/disable/1").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Space space1 = new Space();
    space1.setId(1);
    space1.setDisplayName("Space of Heroes");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId(1L);
    when(matrixService.enableSpaceChat(space1, false)).thenReturn(room);
    when(matrixService.getRoomBySpace(space1, true)).thenReturn(room);

    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(spaceService.canManageSpace(space1, SIMPLE_USER)).thenReturn(true);

    response = mockMvc.perform(put(REST_PATH + "/disable/1").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void muteRoom() throws Exception {
    String roomId = "!testRoomToMute:matrix.meeds.tn";
    doNothing().when(chatNotificationService).toggleMutePrivateRoom(SIMPLE_USER, roomId);

    ResultActions response = mockMvc.perform(post(REST_PATH + "/muteRoom").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                                          .param("roomId", roomId));

    response.andExpect(status().isOk());
    response.andExpect(content().string("Room muted successfully"));

    verify(chatNotificationService, times(1)).toggleMutePrivateRoom(SIMPLE_USER, roomId);
  }

  @Test
  void testNotify() throws Exception {
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "InsufficientToken");
    String jsonNotification =
                            """
                                {
                                  "notification": {
                                    "content": {
                                      "body": "I'm floating in a most peculiar way.",
                                      "msgtype": "m.text"
                                    },
                                    "counts": {
                                      "missed_calls": 1,
                                      "unread": 2
                                    },
                                    "devices": [
                                      {
                                        "app_id": "org.matrix.matrixConsole.ios",
                                        "data": {},
                                        "pushkey": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0._yNyipMgOp5G2giBZPrne1jcCHeyEiKda_kQOW_bvZM",
                                        "pushkey_ts": 12345678,
                                        "tweaks": {
                                          "sound": "bing"
                                        }
                                      }
                                    ],
                                    "event_id": "$3957tyerfgewrf384",
                                    "prio": "high",
                                    "room_alias": "#exampleroom:matrix.org",
                                    "room_id": "!slw48wfj34rtnrf:example.com",
                                    "room_name": "Mission Control",
                                    "sender": "@exampleuser:matrix.org",
                                    "sender_display_name": "Major Tom",
                                    "type": "m.room.message"
                                  }
                                }
                                """;
    ResultActions response = mockMvc.perform(post(REST_PATH + "/notify").with(simpleUser())
                                                                        .content(jsonNotification)
                                                                        .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isInternalServerError());

    PropertyManager.setProperty(MATRIX_JWT_SECRET, "ThisIsASampleJWTTokenFoeTestingPurposes");
    when(matrixService.getUserFullMatrixID(SIMPLE_USER)).thenReturn("@user:matrix.meeds.tn");
    when(matrixService.findUserByMatrixId("@user:matrix.meeds.tn")).thenReturn(SIMPLE_USER);

    when(identityManager.identityExisted(OrganizationIdentityProvider.NAME, "user")).thenReturn(true);
    response = mockMvc.perform(post(REST_PATH + "/notify").with(simpleUser())
                                                          .content(jsonNotification)
                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().string("""
        {
          "rejected": []
        }
        """));
  }

  @Test
  void getMatrixId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/findId/demo").with(simpleUser())
                                                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED));

    response.andExpect(status().isNotFound());

    when(matrixService.getMatrixIdForUser("demo")).thenReturn("@demo:matrix.exo.tn");
    response = mockMvc.perform(get(REST_PATH + "/findId/demo").with(simpleUser())
                                                              .contentType(MediaType.APPLICATION_FORM_URLENCODED));

    response.andExpect(status().isOk());
    response.andExpect(content().string("@demo:matrix.exo.tn"));
  }

  @Test
  void getChatAuthorizationStatus() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH
        + "/spaceChatSetting/0").with(simpleUser()).contentType(MediaType.APPLICATION_FORM_URLENCODED));

    response.andExpect(status().isBadRequest());

    when(spaceService.getSpaceById(1)).thenReturn(null);
    response = mockMvc.perform(get(REST_PATH + "/spaceChatSetting/1").with(simpleUser())
                                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED));

    response.andExpect(status().isBadRequest());

    Space space = new Space();
    space.setId(1);
    space.setTemplateId(1);
    space.setDisplayName("Test space");
    when(spaceService.getSpaceById(1)).thenReturn(space);
    Room spaceRoom = new Room();
    spaceRoom.setId(1);
    spaceRoom.setSpaceId(1L);
    spaceRoom.setStatus(RoomStatus.ENABLED.name());

    SpaceTemplateSetting spaceTemplateSetting = new SpaceTemplateSetting(1, "Template One", "/icon.png", true, true);
    ChatSettingsEntity chatSettings = new ChatSettingsEntity(true, true, true, List.of(spaceTemplateSetting));
    when(matrixService.loadChatSettings(anyString(), any())).thenReturn(chatSettings);
    when(matrixService.isChatAuthorizedByAdministration(space)).thenReturn(true);
    when(matrixService.getRoomBySpace(space)).thenReturn(spaceRoom);
    response = mockMvc.perform(get(REST_PATH + "/spaceChatSetting/1").with(simpleUser())
                                                                     .contentType(MediaType.APPLICATION_FORM_URLENCODED));

    response.andExpect(status().isOk());
    response.andExpect(content().string("""
        {
          "chatAuthorizedForSpace": true
        }
        """));
  }

  @Test
  void markRoomAsRead() throws Exception {
    mockMvc.perform(post(REST_PATH + "/rooms/!room:matrix.meeds.tn/read").with(simpleUser())
                                                                          .param("eventId", "$evt")
                                                                          .param("ts", "5000"))
           .andExpect(status().isNoContent());
    verify(chatNotificationService).markRoomAsRead("user", "!room:matrix.meeds.tn", "$evt", 5000L);

    doThrow(new ObjectNotFoundException("not found")).when(chatNotificationService)
                                                     .markRoomAsRead("user", "!unknown:matrix.meeds.tn", "$evt", null);
    mockMvc.perform(post(REST_PATH + "/rooms/!unknown:matrix.meeds.tn/read").with(simpleUser()).param("eventId", "$evt"))
           .andExpect(status().isNotFound());

    doThrow(new IllegalAccessException("not a member")).when(chatNotificationService)
                                                       .markRoomAsRead("user", "!other:matrix.meeds.tn", "$evt", null);
    mockMvc.perform(post(REST_PATH + "/rooms/!other:matrix.meeds.tn/read").with(simpleUser()).param("eventId", "$evt"))
           .andExpect(status().isForbidden());

    mockMvc.perform(post(REST_PATH + "/rooms/!room:matrix.meeds.tn/read").param("eventId", "$evt"))
           .andExpect(status().isForbidden());

    doThrow(new IllegalArgumentException("matrix.markRoomAsRead.invalidParameters")).when(chatNotificationService)
                                                                                  .markRoomAsRead("user", "!room:matrix.meeds.tn", "bad", null);
    mockMvc.perform(post(REST_PATH + "/rooms/!room:matrix.meeds.tn/read").with(simpleUser()).param("eventId", "bad"))
           .andExpect(status().isBadRequest());

    // the receipt could not be posted: nothing was marked read, the caller must know
    doThrow(new IllegalStateException("matrix.markRoomAsRead.receiptNotPosted")).when(chatNotificationService)
                                                                              .markRoomAsRead("user", "!room:matrix.meeds.tn", "$down", null);
    mockMvc.perform(post(REST_PATH + "/rooms/!room:matrix.meeds.tn/read").with(simpleUser()).param("eventId", "$down"))
           .andExpect(status().isInternalServerError());
  }

}
