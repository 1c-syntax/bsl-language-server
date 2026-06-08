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
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
@Component
public final class LibConfigParser {

  private static final String MODULE_ELEMENT = "module";
  private static final String CLASS_ELEMENT = "class";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String FILE_ATTRIBUTE = "file";

  private static XMLInputFactory createXmlInputFactory() {
    var factory = XMLInputFactory.newDefaultFactory();
    // Защита от XXE: lib.config не должен подтягивать DTD/внешние сущности.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory;
  }

  /**
   * Разобрать манифест {@code lib.config}.
   * <p>
   * Элементы {@code <module>}/{@code <class>} читаются потоково в документном порядке, поэтому
   * любое их чередование обрабатывается корректно (Jackson-десериализация двух unwrapped-списков
   * теряла первый элемент каждого типа при интерливинге). Поток также корректно отрабатывает BOM.
   *
   * @param libConfigPath абсолютный путь к {@code lib.config}
   * @return распарсенный манифест либо пустой, если файл не читается или некорректен
   */
  public LibConfig parse(Path libConfigPath) {
    var modules = new ArrayList<LibEntry>();
    var classes = new ArrayList<LibEntry>();

    // Фабрика создаётся на вызов: XMLInputFactory не гарантирует потокобезопасность,
    // а newDefaultFactory() дёшев (без ServiceLoader), parse() не на горячем пути.
    try (var input = Files.newInputStream(libConfigPath)) {
      readEntries(createXmlInputFactory().createXMLStreamReader(input), modules, classes);
    } catch (IOException e) {
      LOGGER.warn("Failed to parse lib.config: {}", libConfigPath, e);
      return new LibConfig(List.of(), List.of());
    } catch (XMLStreamException e) {
      LOGGER.warn("Malformed lib.config: {}", libConfigPath, e);
      return new LibConfig(List.of(), List.of());
    }

    return new LibConfig(List.copyOf(modules), List.copyOf(classes));
  }

  private static void readEntries(XMLStreamReader reader, List<LibEntry> modules, List<LibEntry> classes)
    throws XMLStreamException {
    try {
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          addEntry(reader, modules, classes);
        }
      }
    } finally {
      reader.close();
    }
  }

  private static void addEntry(XMLStreamReader reader, List<LibEntry> modules, List<LibEntry> classes) {
    var element = reader.getLocalName();
    List<LibEntry> target;
    if (MODULE_ELEMENT.equals(element)) {
      target = modules;
    } else if (CLASS_ELEMENT.equals(element)) {
      target = classes;
    } else {
      return;
    }
    var name = reader.getAttributeValue(null, NAME_ATTRIBUTE);
    var file = reader.getAttributeValue(null, FILE_ATTRIBUTE);
    if (name != null && file != null) {
      target.add(new LibEntry(name, file));
    }
  }

  /**
   * Запись манифеста.
   *
   * @param modules список объявленных модулей
   * @param classes список объявленных классов
   */
  public record LibConfig(List<LibEntry> modules, List<LibEntry> classes) {
  }

  /**
   * Одна запись манифеста — {@code <module>} или {@code <class>}.
   *
   * @param name имя модуля или класса
   * @param file путь к {@code .os}-файлу
   */
  public record LibEntry(String name, String file) {
  }
}

