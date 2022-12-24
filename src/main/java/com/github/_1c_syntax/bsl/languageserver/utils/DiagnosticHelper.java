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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameterInfo;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public final class DiagnosticHelper {

  public static boolean equalNodes(Tree leftNode, Tree rightNode) {

    if (leftNode.getChildCount() != rightNode.getChildCount()
      || !leftNode.getClass().equals(rightNode.getClass())) {
      return false;
    }

    if (leftNode instanceof TerminalNode) {

      int leftNodeType = ((TerminalNode) leftNode).getSymbol().getType();
      int rightNodeType = ((TerminalNode) rightNode).getSymbol().getType();

      if (leftNodeType != rightNodeType
        || (leftNodeType == BSLParser.STRING
        && !leftNode.toString().equals(rightNode.toString()))
        || (!leftNode.toString().equalsIgnoreCase(rightNode.toString()))) {
        return false;
      }

    }

    for (int i = 0; i < leftNode.getChildCount(); i++) {
      if (!equalNodes(leftNode.getChild(i), rightNode.getChild(i))) {
        return false;
      }
    }

    return true;
  }

  public static boolean isStructureType(ParseTree tnc) {
    return "Структура".equalsIgnoreCase(tnc.getText()) || "Structure".equalsIgnoreCase(tnc.getText());
  }

  public static boolean isFixedStructureType(ParseTree tnc) {
    return "ФиксированнаяСтруктура".equalsIgnoreCase(tnc.getText()) || "FixedStructure".equalsIgnoreCase(tnc.getText());
  }

  public static boolean isWSDefinitionsType(ParseTree tnc) {
    return "WSОпределения".equalsIgnoreCase(tnc.getText()) || "WSDefinitions".equalsIgnoreCase(tnc.getText());
  }

  public static boolean isFTPConnectionType(ParseTree tnc) {
    return "FTPСоединение".equalsIgnoreCase(tnc.getText()) || "FTPConnection".equalsIgnoreCase(tnc.getText());
  }

  public static boolean isInternetMailProfileType(ParseTree tnc) {
    return "ИнтернетПочтовыйПрофиль".equalsIgnoreCase(tnc.getText())
      || "InternetMailProfile".equalsIgnoreCase(tnc.getText());
  }

  public static void configureDiagnostic(BSLDiagnostic diagnostic, Map<String, Object> configuration) {
    if (configuration == null || configuration.isEmpty()) {
      return;
    }

    Set<Class<?>> types = new HashSet<>();
    types.add(Integer.class);
    types.add(Boolean.class);
    types.add(Float.class);
    types.add(String.class);

    diagnostic.getInfo().getParameters().stream()
      .filter(diagnosticParameterInfo -> configuration.containsKey(diagnosticParameterInfo.getName())
        && types.contains(diagnosticParameterInfo.getType()))
      .forEach((DiagnosticParameterInfo diagnosticParameterInfo) -> {
        try {
          var field = diagnostic.getClass().getDeclaredField(diagnosticParameterInfo.getName());
          if (field.trySetAccessible()) {
            field.set(diagnostic, configuration.get(field.getName()));
          }
        } catch (NoSuchFieldException | IllegalAccessException e) {
          LOGGER.error("Can't set param.", e);
        }
      });
  }

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
