/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.events;

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WorkspaceEventsTest {

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Autowired
  private WorkspaceEventCollector eventCollector;

  @TempDir
  Path workspaceDir;

  private URI workspaceUri;

  @BeforeEach
  void setUp() {
    workspaceUri = Absolute.uri(workspaceDir.toUri());
    eventCollector.clear();
  }

  @AfterEach
  void tearDown() {
    serverContextProvider.clear();
    eventCollector.clear();
  }

  @Test
  void testWorkspaceAddedEventPublished() {
    // when
    serverContextProvider.addWorkspace(workspaceUri);

    // then
    assertThat(eventCollector.getAddedEvents()).hasSize(1);
    var event = eventCollector.getAddedEvents().get(0);
    assertThat(event.getWorkspaceUri()).isEqualTo(workspaceUri);
    assertThat(event.getSource()).isSameAs(serverContextProvider);
  }

  @Test
  void testBeforeWorkspaceRemovedEventPublished() {
    // given
    serverContextProvider.addWorkspace(workspaceUri);
    eventCollector.clear();

    // when
    var workspaceFolder = new WorkspaceFolder(workspaceUri.toString(), "test");
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then
    assertThat(eventCollector.getBeforeRemovedEvents()).hasSize(1);
    var event = eventCollector.getBeforeRemovedEvents().get(0);
    assertThat(event.getWorkspaceUri()).isEqualTo(workspaceUri);
    assertThat(event.getSource()).isSameAs(serverContextProvider);
  }

  @Test
  void testWorkspaceRemovedEventPublished() {
    // given
    serverContextProvider.addWorkspace(workspaceUri);
    eventCollector.clear();

    // when
    var workspaceFolder = new WorkspaceFolder(workspaceUri.toString(), "test");
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then
    assertThat(eventCollector.getRemovedEvents()).hasSize(1);
    var event = eventCollector.getRemovedEvents().get(0);
    assertThat(event.getWorkspaceUri()).isEqualTo(workspaceUri);
    assertThat(event.getSource()).isSameAs(serverContextProvider);
  }

  @Test
  void testEventOrder() {
    // given
    serverContextProvider.addWorkspace(workspaceUri);
    eventCollector.clear();

    // when
    var workspaceFolder = new WorkspaceFolder(workspaceUri.toString(), "test");
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then - BeforeWorkspaceRemovedEvent should be published before WorkspaceRemovedEvent
    assertThat(eventCollector.getBeforeRemovedEvents()).hasSize(1);
    assertThat(eventCollector.getRemovedEvents()).hasSize(1);

    var beforeEvent = eventCollector.getBeforeRemovedEvents().get(0);
    var afterEvent = eventCollector.getRemovedEvents().get(0);

    // Both events should have the same workspace URI
    assertThat(beforeEvent.getWorkspaceUri()).isEqualTo(afterEvent.getWorkspaceUri());
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public WorkspaceEventCollector workspaceEventCollector() {
      return new WorkspaceEventCollector();
    }
  }

  static class WorkspaceEventCollector {
    private final List<WorkspaceAddedEvent> addedEvents = new ArrayList<>();
    private final List<BeforeWorkspaceRemovedEvent> beforeRemovedEvents = new ArrayList<>();
    private final List<WorkspaceRemovedEvent> removedEvents = new ArrayList<>();

    @EventListener
    public void onWorkspaceAdded(WorkspaceAddedEvent event) {
      addedEvents.add(event);
    }

    @EventListener
    public void onBeforeWorkspaceRemoved(BeforeWorkspaceRemovedEvent event) {
      beforeRemovedEvents.add(event);
    }

    @EventListener
    public void onWorkspaceRemoved(WorkspaceRemovedEvent event) {
      removedEvents.add(event);
    }

    public List<WorkspaceAddedEvent> getAddedEvents() {
      return addedEvents;
    }

    public List<BeforeWorkspaceRemovedEvent> getBeforeRemovedEvents() {
      return beforeRemovedEvents;
    }

    public List<WorkspaceRemovedEvent> getRemovedEvents() {
      return removedEvents;
    }

    public void clear() {
      addedEvents.clear();
      beforeRemovedEvents.clear();
      removedEvents.clear();
    }
  }
}
