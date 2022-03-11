/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.websocket;

import com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class WebsocketLauncherTest {

  private CountDownLatch latch;
  private Thread thread;
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;

  @BeforeEach
  void setUpStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @BeforeEach
  void setCountDownLatch() {
    latch = new CountDownLatch(1);
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
    System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
  }

  @AfterEach
  public void killThread() {
    if (thread != null) {
      thread.interrupt();
    }
  }

  void startWebsocketServer(String[] arguments) {
    thread = new Thread(new Runnable() {
      @Override
      public void run() {
        BSLLSPLauncher.main(arguments);
      }
    });
    thread.start();
  }

  void connectClientToServer(int websocketPort) throws URISyntaxException, DeploymentException, IOException {
    final ClientEndpointConfig endPoint = ClientEndpointConfig.Builder.create().build();

    ClientManager client = ClientManager.createClient();
    client.connectToServer(new Endpoint() {

      @Override
      public void onOpen(Session session, EndpointConfig config) {
        latch.countDown();
      }

    }, endPoint, new URI("ws://localhost:" + websocketPort + BSLLSWebSocketServerConfigProvider.WEBSOCKET_SERVER_PATH));
  }

  void testWebsocketServer(String[] args, int websocketPort) {
    try {
      startWebsocketServer(args);
      latch.await(1, TimeUnit.SECONDS);
      connectClientToServer(websocketPort);
    } catch (Exception error) {
      System.err.print(error.getMessage());
    }
  }

  @Test
  void testWebsocketDefaultMode() {
    // given
    String[] args = "-w ".split(" ");

    // when
    testWebsocketServer(args, 8025);

    // then
    assertThat(latch.getCount()).isEqualTo(0);
    assertThat(outContent.toString()).contains("start with ws://localhost:8025");
    assertThat(outContent.toString()).contains("server started");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testWebsocketConnectionError() {
    // given
    String[] args = "-w ".split(" ");

    // when
    testWebsocketServer(args, 8026);

    // then
    assertThat(latch.getCount()).isEqualTo(1);
    assertThat(errContent.toString()).contains("Connection refused");
  }

  @Test
  void testWebsocketOnSpecifiedPort() {
    // given
    String[] args = "-w -p 8026".split(" ");
    latch = new CountDownLatch(1);

    // when
    testWebsocketServer(args, 8026);

    // then
    assertThat(latch.getCount()).isEqualTo(0);
    assertThat(outContent.toString()).contains("start with ws://localhost:8026");
    assertThat(outContent.toString()).contains("server started");
    assertThat(errContent.toString()).isEmpty();
  }

}