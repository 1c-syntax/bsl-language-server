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
package com.github._1c_syntax.bsl.languageserver;

import jakarta.annotation.PostConstruct;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Информация о сервере. Автоматически заполняется на основании данных о версии и имени приложения.
 */
@Slf4j
@EqualsAndHashCode(exclude = "applicationName", callSuper = true)
@Component
public class AutoServerInfo extends org.eclipse.lsp4j.ServerInfo implements Serializable {

  private static final String MANIFEST_VERSION = readVersion();

  @SuppressWarnings("FieldMayBeFinal")
  @Value("${spring.application.name:Dummy Language Server}")
  // Field is transient to exclude it from GSON serialization in jsonrpc.
  private transient String applicationName = "";

  @PostConstruct
  private void init() {
    setName(applicationName);
    setVersion(MANIFEST_VERSION);
  }

  private static String readVersion() {
    final InputStream mfStream = Thread.currentThread()
      .getContextClassLoader()
      .getResourceAsStream("META-INF/MANIFEST.MF");

    Manifest manifest = new Manifest();
    try {
      manifest.read(mfStream);
    } catch (IOException e) {
      LOGGER.error("Can't read manifest", e);
      return "";
    }

    return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
  }

}
