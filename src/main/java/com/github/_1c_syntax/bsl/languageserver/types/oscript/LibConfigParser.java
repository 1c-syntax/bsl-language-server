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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Парсер {@code lib.config} OneScript-библиотек.
 * <p>
 * Структура:
 * <pre>
 * &lt;package-def&gt;
 *   &lt;module name="..." file="..."/&gt;
 *   &lt;class  name="..." file="..."/&gt;
 * &lt;/package-def&gt;
 * </pre>
 * Пути в {@code file=} относительны каталогу самого {@code lib.config}.
 */
@Slf4j
public final class LibConfigParser {

  private final XmlMapper xmlMapper = XmlMapper.builder().build();

  /**
   * @param libConfigPath абсолютный путь к {@code lib.config}
   * @return распарсенный манифест либо пустой, если файл не читается
   */
  public LibConfig parse(Path libConfigPath) {
    try {
      var content = Files.readString(libConfigPath);
      var raw = xmlMapper.readValue(content, RawPackageDef.class);
      return toLibConfig(raw);
    } catch (IOException e) {
      LOGGER.warn("Failed to parse lib.config: {}", libConfigPath, e);
      return new LibConfig(List.of(), List.of());
    } catch (RuntimeException e) {
      LOGGER.warn("Malformed lib.config: {}", libConfigPath, e);
      return new LibConfig(List.of(), List.of());
    }
  }

  private static LibConfig toLibConfig(RawPackageDef raw) {
    if (raw == null) {
      return new LibConfig(List.of(), List.of());
    }
    var modules = new ArrayList<LibEntry>();
    if (raw.module != null) {
      for (var m : raw.module) {
        if (m.name != null && m.file != null) {
          modules.add(new LibEntry(m.name, m.file));
        }
      }
    }
    var classes = new ArrayList<LibEntry>();
    if (raw.klass != null) {
      for (var c : raw.klass) {
        if (c.name != null && c.file != null) {
          classes.add(new LibEntry(c.name, c.file));
        }
      }
    }
    return new LibConfig(List.copyOf(modules), List.copyOf(classes));
  }

  /**
   * Запись манифеста.
   */
  public record LibConfig(List<LibEntry> modules, List<LibEntry> classes) {
  }

  /**
   * Одна запись манифеста — {@code <module>} или {@code <class>}.
   */
  public record LibEntry(String name, String file) {
  }

  /** Внутренняя POJO для парсинга XML. */
  public static final class RawPackageDef {
    public List<RawEntry> module;

    @JacksonXmlProperty(localName = "class")
    public List<RawEntry> klass;
  }

  /** Внутренняя POJO для одной записи. */
  public static final class RawEntry {
    @JacksonXmlProperty(isAttribute = true)
    public String name;

    @JacksonXmlProperty(isAttribute = true)
    public String file;
  }
}

