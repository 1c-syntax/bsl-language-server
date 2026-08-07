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
package com.github._1c_syntax.bsl.languageserver.reporters;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Файл отчёта с семантикой «всё или ничего».
 * <p>
 * Содержимое пишется в {@link #stream()} по мере формирования отчёта. Файл остаётся на диске
 * только после {@link #commit()}; если объект закрыт без фиксации, файл удаляется — недописанный
 * отчёт не должен пережить неудачный запуск и не должен выдаваться за результат.
 * <p>
 * {@link #close()} не бросает исключений: он вызывается на пути обработки ошибки, где подменять
 * исходную причину нечем.
 */
@Slf4j
final class ReportFile implements Closeable {

  private final Path path;
  private final OutputStream stream;

  private boolean committed;

  private ReportFile(Path path, OutputStream stream) {
    this.path = path;
    this.stream = stream;
  }

  /**
   * Создать файл отчёта, при необходимости создав каталог. Существующий файл усекается.
   *
   * @param outputDir каталог размещения отчёта
   * @param fileName  имя файла отчёта
   * @return открытый файл отчёта
   * @throws UncheckedIOException если каталог или файл не удалось создать
   */
  static ReportFile create(Path outputDir, String fileName) {
    var path = outputDir.resolve(fileName);
    try {
      Files.createDirectories(outputDir);
      return new ReportFile(path, new BufferedOutputStream(Files.newOutputStream(path)));
    } catch (IOException e) {
      throw new UncheckedIOException("Can't create report file " + path, e);
    }
  }

  /**
   * Поток для записи содержимого отчёта.
   *
   * @return поток вывода, действительный до {@link #commit()} или {@link #close()}
   */
  OutputStream stream() {
    return stream;
  }

  /**
   * Путь к файлу отчёта.
   *
   * @return путь, по которому файл окажется после фиксации
   */
  Path path() {
    return path;
  }

  /**
   * Зафиксировать отчёт: дописать буфер, закрыть поток и оставить файл на диске.
   *
   * @throws UncheckedIOException если содержимое не удалось дописать
   */
  void commit() {
    try {
      stream.close();
    } catch (IOException e) {
      throw new UncheckedIOException("Can't write report file " + path, e);
    }
    committed = true;
  }

  /**
   * Закрыть поток и, если фиксации не было, удалить файл.
   */
  @Override
  public void close() {
    try {
      stream.close();
    } catch (IOException e) {
      LOGGER.warn("Can't close report file {}", path, e);
    }

    if (committed) {
      return;
    }

    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOGGER.warn("Can't delete incomplete report file {}", path, e);
    }
  }
}
