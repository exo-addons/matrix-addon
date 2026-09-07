/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins the AI entry points this add-on ships. A UX binding names the agent that
 * answers it and, optionally, the tools that agent may call for it; together they
 * decide the cost of a click and what the model is allowed to reach.
 */
public class ChatUxBindingsTest {

  /** The agent that offers no tool at all, shipped by the AI add-on. */
  private static final String                   TEXT_ONLY_AGENT    = "WRITER";

  /**
   * Bindings whose prompt carries the message itself. They need no tool, and there
   * is no way to say so on a tool-capable agent: AiAgentService.getToolDefinitions
   * resolves a null binding list to the agent's, and a null agent list to every
   * installed tool. A regression here silently ships the whole catalogue to
   * summarise one chat message.
   */
  private static final List<String>             TEXT_ONLY_BINDINGS = List.of("Chat-Summarize-Message",
                                                                             "Chat-Translate-Message",
                                                                             "Chat-Draft-Reply");

  /** Bindings that do read the conversation, with the tools they may use. */
  private static final Map<String, List<String>> TOOL_BINDINGS     = Map.of("Chat-Summarize-Conversations",
                                                                            List.of("get_unread_chat_messages",
                                                                                    "list_chat_conversations"),
                                                                            "Chat-Summarize-Room",
                                                                            List.of("get_chat_messages"));

  /**
   * Reads the bindings from the resource the initializer itself loads.
   *
   * @return the shipped bindings, by name id
   * @throws Exception if the resource is missing or unparseable
   */
  private Map<String, JsonNode> bindings() throws Exception {
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ai-ux-bindings.json")) {
      assertNotNull("ai-ux-bindings.json is not on the classpath", inputStream);
      JsonNode root = new ObjectMapper().readTree(inputStream);
      Map<String, JsonNode> byNameId = new HashMap<>();
      root.get("bindings").forEach(binding -> byNameId.put(binding.path("nameId").asText(), binding));
      return byNameId;
    }
  }

  /**
   * @param binding a shipped binding
   * @return the tool names it declares, empty when it declares none
   */
  private List<String> toolNames(JsonNode binding) {
    List<String> tools = new ArrayList<>();
    binding.path("toolNames").forEach(tool -> tools.add(tool.asText()));
    return tools;
  }

  @Test
  public void textOnlyBindingsRunOnTheAgentThatOffersNoTool() throws Exception {
    Map<String, JsonNode> bindings = bindings();
    for (String nameId : TEXT_ONLY_BINDINGS) {
      JsonNode binding = bindings.get(nameId);
      assertNotNull("No binding named " + nameId, binding);
      assertEquals(nameId + " must run on the text-only agent: its message is already in the prompt",
                   TEXT_ONLY_AGENT,
                   binding.path("agentNameId").asText());
      assertTrue(nameId + " lists tools, which the text-only agent cannot offer", toolNames(binding).isEmpty());
      assertFalse(nameId + " has no version, so the initializer never re-imports it",
                  binding.path("version").asText("").isEmpty());
    }
  }

  @Test
  public void conversationBindingsNameTheToolsTheyRead() throws Exception {
    Map<String, JsonNode> bindings = bindings();
    for (Map.Entry<String, List<String>> expected : TOOL_BINDINGS.entrySet()) {
      JsonNode binding = bindings.get(expected.getKey());
      assertNotNull("No binding named " + expected.getKey(), binding);
      assertEquals(expected.getKey() + " reads the conversation: its tools are what it may reach",
                   expected.getValue(),
                   toolNames(binding));
    }
  }

  /**
   * A tool-capable agent with no list reaches every installed tool, so an empty
   * list never means "none" -- the defect this add-on shipped until EXO-90038.
   */
  @Test
  public void noBindingLeavesItsToolsUnsaidOnAToolCapableAgent() throws Exception {
    bindings().forEach((nameId, binding) -> {
      boolean textOnly = TEXT_ONLY_AGENT.equals(binding.path("agentNameId").asText());
      assertTrue(nameId + " runs on a tool-capable agent without naming its tools: it reaches the whole catalogue",
                 textOnly || !toolNames(binding).isEmpty());
    });
  }
}
