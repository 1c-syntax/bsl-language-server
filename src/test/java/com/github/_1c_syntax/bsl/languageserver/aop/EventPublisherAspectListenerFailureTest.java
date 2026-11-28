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
package com.github._1c_syntax.bsl.languageserver.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(EventPublisherAspectListenerFailureTest.FailingListenerConfiguration.class)
@CleanupContextBeforeClassAndAfterClass
class EventPublisherAspectListenerFailureTest {

  private static final URI FAILING_URI =
    Absolute.uri(Absolute.path("src/test/resources/empty-workspace/listener-failure.bsl").toUri());
  private static final String FAILURE_MESSAGE = "listener failure";

  @Test
  void listenerFailureIsLoggedWithStackTrace() {
    // given
    var aspectLog = (Logger) LoggerFactory.getLogger(EventPublisherAspect.class);
    var logged = new ListAppender<ILoggingEvent>();
    logged.start();
    aspectLog.addAppender(logged);

    // when
    try {
      TestUtils.getDocumentContext(FAILING_URI, "А = 1;");
    } finally {
      aspectLog.detachAppender(logged);
    }

    // then
    assertThat(logged.list)
      .filteredOn(event -> event.getLevel() == Level.WARN)
      .anySatisfy(event -> {
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo(FAILURE_MESSAGE);
      });
  }

  @TestConfiguration
  static class FailingListenerConfiguration {
    @Bean
    FailingListener failingListener() {
      return new FailingListener();
    }
  }

  static class FailingListener {
    @EventListener
    public void handleDocumentAdded(ServerContextDocumentAddedEvent event) {
      if (Absolute.uri(event.getUri()).equals(FAILING_URI)) {
        throw new IllegalStateException(FAILURE_MESSAGE);
      }
    }
  }
}
