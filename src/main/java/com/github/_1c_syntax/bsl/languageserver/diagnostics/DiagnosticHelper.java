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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticParameterInfo;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Вспомогательный класс для работы с диагностиками.
 * <p>
 * Предоставляет утилитные методы для сравнения узлов AST,
 * конфигурирования диагностик и работы с их параметрами.
 */
@Slf4j
@UtilityClass
public final class DiagnosticHelper {

  /**
   * Проверить, является ли узел типом "Структура".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип Структура/Structure
   */
  public static boolean isStructureType(ParseTree tnc) {
    return "Структура".equalsIgnoreCase(tnc.getText()) || "Structure".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Проверить, является ли узел типом "ФиксированнаяСтруктура".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип ФиксированнаяСтруктура/FixedStructure
   */
  public static boolean isFixedStructureType(ParseTree tnc) {
    return "ФиксированнаяСтруктура".equalsIgnoreCase(tnc.getText()) || "FixedStructure".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Проверить, является ли узел типом "Соответствие".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип Соответствие/Map
   */
  public static boolean isCorrespondenceType(ParseTree tnc) {
    return "Соответствие".equalsIgnoreCase(tnc.getText()) || "Map".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Проверить, является ли узел типом "WSОпределения".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип WSОпределения/WSDefinitions
   */
  public static boolean isWSDefinitionsType(ParseTree tnc) {
    return "WSОпределения".equalsIgnoreCase(tnc.getText()) || "WSDefinitions".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Проверить, является ли узел типом "FTPСоединение".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип FTPСоединение/FTPConnection
   */
  public static boolean isFTPConnectionType(ParseTree tnc) {
    return "FTPСоединение".equalsIgnoreCase(tnc.getText()) || "FTPConnection".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Проверить, является ли узел типом "ИнтернетПочтовыйПрофиль".
   *
   * @param tnc Узел дерева разбора
   * @return true, если узел представляет тип ИнтернетПочтовыйПрофиль/InternetMailProfile
   */
  public static boolean isInternetMailProfileType(ParseTree tnc) {
    return "ИнтернетПочтовыйПрофиль".equalsIgnoreCase(tnc.getText())
      || "InternetMailProfile".equalsIgnoreCase(tnc.getText());
  }

  /**
   * Настроить параметры диагностики из конфигурации.
   *
   * @param diagnostic    Диагностика для настройки
   * @param configuration Карта конфигурации с параметрами
   */
  public static void configureDiagnostic(BSLDiagnostic diagnostic, @Nullable Map<String, Object> configuration) {
    if (configuration == null || configuration.isEmpty()) {
      return;
    }

    Set<Class<?>> types = new HashSet<>();
    types.add(Integer.class);
    types.add(Boolean.class);
    types.add(Float.class);
    types.add(String.class);
    types.add(Either.class);

    diagnostic.getInfo().getParameters().stream()
      .filter(diagnosticParameterInfo -> configuration.containsKey(diagnosticParameterInfo.getName())
        && types.contains(diagnosticParameterInfo.getType()))
      .forEach((DiagnosticParameterInfo diagnosticParameterInfo) -> {
        try {
          var field = diagnostic.getClass().getDeclaredField(diagnosticParameterInfo.getName());
          if (field.trySetAccessible()) {
            field.set(diagnostic, castParameterValue(field, configuration.get(field.getName())));
          }
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
          LOGGER.error("Can't set param.", e);
        }
      });
  }

  /**
   * Приводит значение параметра из конфигурации к типу целевого поля диагностики.
   * <p>
   * Для полей типа {@link Either}{@code <String, List<String>>} значение может прийти
   * как строка (список через разделитель — например, из SonarQube UI) либо как JSON-массив
   * (десериализуется Jackson в {@link List}). Строка оборачивается в левую ветвь,
   * массив — в правую. Для остальных полей значение возвращается без изменений.
   *
   * @param field Целевое поле диагностики
   * @param value Значение из конфигурации ({@code String}, {@code List} и т.п.)
   * @return Значение, пригодное для присвоения полю
   */
  private static Object castParameterValue(Field field, Object value) {
    if (Either.class.isAssignableFrom(field.getType())) {
      if (value instanceof List<?> list) {
        return Either.forRight(list.stream().map(String::valueOf).toList());
      }
      return Either.forLeft(String.valueOf(value));
    }
    return value;
  }

  /**
   * Настроить параметры диагностики с фильтрацией по именам параметров.
   *
   * @param diagnostic    Диагностика для настройки
   * @param configuration Карта конфигурации с параметрами
   * @param filter        Список имён параметров для применения
   */
  public static void configureDiagnostic(BSLDiagnostic diagnostic,
                                         Map<String, Object> configuration,
                                         String... filter) {
    Map<String, Object> newConfiguration = new HashMap<>();
    for (String name : filter) {
      if (configuration.containsKey(name)) {
        newConfiguration.put(name, configuration.get(name));
      }
    }

    configureDiagnostic(diagnostic, newConfiguration);
  }

  /**
   * Разворачивает значение параметра-списка в список строк.
   * <p>
   * Левая ветвь {@link Either} — строка с элементами через запятую (форма, приходящая из SonarQube UI
   * и из «старых» конфигов); правая ветвь — уже готовый список (из JSON-массива). Элементы обрезаются
   * по краям от пробелов; пустые элементы отбрасываются.
   *
   * @param value Значение параметра: строка через запятую либо список строк
   * @return Список строк без пустых элементов
   */
  public static List<String> asStringList(Either<String, List<String>> value) {
    List<String> source;
    if (value.isRight()) {
      source = value.getRight();
    } else {
      source = List.of(value.getLeft().split(","));
    }

    return source.stream()
      .map(String::trim)
      .filter(element -> !element.isEmpty())
      .toList();
  }

  /**
   * Создает PATTERN из строки со словами с разделителем ',' (запятая) (используется в параметрах диагностики).
   * При создании паттерна удаляются концевые пробелы слов
   *
   * @param words Строка со словами
   * @return Созданный паттерн
   */
  public static Pattern createPatternFromString(String words) {
    return createPatternFromString(words, ",");
  }

  /**
   * Создает PATTERN из строки со словами с указанным разделителем (используется в параметрах диагностики).
   * При создании паттерна удаляются концевые пробелы слов
   *
   * @param words Строка со словами
   * @return Созданный паттерн
   */
  public static Pattern createPatternFromString(String words, String delimiter) {
    StringJoiner stringJoiner = new StringJoiner("|");
    for (String elem : words.split(delimiter)) {
      stringJoiner.add(Pattern.quote(elem.trim()));
    }

    return CaseInsensitivePattern.compile("(?:^" + stringJoiner + ").*");
  }
}
