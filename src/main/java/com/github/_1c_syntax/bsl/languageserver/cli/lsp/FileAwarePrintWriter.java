/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.cli.lsp;

import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Обертка над PrintWriter, позволяющая изменять выходной файловый поток "на-лету",
 * в отличие от установки в конструкторе в оригинальном {@link PrintWriter}.
 */
@Component
@Slf4j
public class FileAwarePrintWriter extends PrintWriter {

  private boolean isEmpty = true;
  @Nullable
  private File file;

  /**
   * Конструктор по умолчанию. Отправляет вывод в /dev/null.
   */
  public FileAwarePrintWriter() {
    super(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8);
  }

  /**
   * @param file Файл, в который отныне нужно перенаправлять вывод PrintWriter
   */
  public synchronized void setFile(@Nullable File file) {

    if (Objects.equals(file, this.file)) {
      return;
    }

    this.file = file;

    if (file == null) {
      closeOutputStream();
      return;
    }

    if (file.isDirectory()) {
      LOGGER.error("Trace log setting must lead to file, not directory! {}", file.getAbsolutePath());
      return;
    }

    FileOutputStream fileOutputStream;
    try {
      // stream is not closed, cause it used as output stream in writer. See this#out field.
      fileOutputStream = new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      LOGGER.error("Can't create LSP trace file", e);
      return;
    }

    closeOutputStream();

    this.out = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));
    this.lock = this.out;
    this.isEmpty = false;

  }

  @Override
  public void print(String s) {
    if (isEmpty) {
      return;
    }
    super.print(s);
  }

  @Override
  public void flush() {
    if (isEmpty) {
      return;
    }
    super.flush();
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    setFile(event.getSource().getTraceLog());
  }

  @SneakyThrows
  private void closeOutputStream() {
    out.close();
    isEmpty = true;
  }
}
