/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

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
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("websocket")
@CleanupContextBeforeClassAndAfterEachTestMethod
class WebsocketLauncherTest {

  @LocalServerPort
  private int port;

  private CountDownLatch latch;
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;

  private WebSocketContainer webSocketContainer;
  private TestWebSocketClient client;
  private Session session;

  @Value("${app.websocket.lsp-path}")
  private String endpointPath;

  @BeforeEach
  void setUpStreams() {
    webSocketContainer = ContainerProvider.getWebSocketContainer();
    client = new TestWebSocketClient();

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
  public void closeSession() throws IOException {
    if (session != null) {
      session.close();
    }
  }

  void connectClientToServer(int websocketPort) throws URISyntaxException, DeploymentException, IOException {
    session = webSocketContainer.connectToServer(client, new URI("ws://localhost:" + websocketPort + endpointPath));
  }

  @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
  void testWebsocketServer(int websocketPort) {
    try {
      latch.await(1, TimeUnit.SECONDS);
      connectClientToServer(websocketPort);
    } catch (Exception error) {
      System.err.print(error.getMessage());
    }
  }

  @Test
  void testWebsocketDefaultMode() {
    // when
    testWebsocketServer(port);

    // then
    assertThat(latch.getCount()).isZero();
    assertThat(outContent.toString()).contains("localhost");
    assertThat(outContent.toString()).contains("Completed initialization");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testWebsocketConnectionError() {
    // when
    testWebsocketServer(port + 1);

    // then
    assertThat(latch.getCount()).isEqualTo(1);
    assertThat(errContent.toString()).isNotEmpty();
  }

  @ClientEndpoint
  public class TestWebSocketClient {

    @OnOpen
    public void onOpen(){
      latch.countDown();
    }
  }
}