/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.context.symbol.description;

import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Вспомогательный класс для чтения данных из описания метода
 */
@UtilityClass
public class DescriptionReader {

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и формирует список описаний параметров метода
   *
   * @param ctx Дерево описания метода
   * @return Список описаний параметров метода
   */
  public static List<ParameterDescription> readParameters(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    List<ParameterDescription> result = new ArrayList<>();
    var current = new TempParameterData();
    var strings = getParametersStrings(ctx);
    for (BSLMethodDescriptionParser.ParametersStringContext string : strings) {

      // найдем дочерние ноды. на самом деле должна быть либо одна указанная нода, либо ни одной, что заставляет
      // считать строку как описание
      var child = Trees.findAllRuleNodes(string,
        BSLMethodDescriptionParser.RULE_parameterString, BSLMethodDescriptionParser.RULE_subParameterString,
        BSLMethodDescriptionParser.RULE_typeWithDescription)
        .stream()
        .findFirst();

      // сразу необходимо обыграть неклассифицированную строку
      if (child.isEmpty()) {
        current = readUnknownParameterString(result, current, string);
      } else {
        var parameterPart = (BSLParserRuleContext) child.get();
        current = readSpecialParameterString(result, current, parameterPart);
      }
    }

    // не забываем сохранить последний прочитанный параметр
    saveLastParameter(current, result);
    return result;
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и формирует список описаний возвращаемых значений
   *
   * @param ctx Дерево описания метода
   * @return Список описаний возвращаемых значений
   */
  public static List<TypeDescription> readReturnedValue(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    var current = new TempParameterData();
    var strings = getReturnedValuesStrings(ctx);
    for (BSLMethodDescriptionParser.ReturnsValuesStringContext string : strings) {
      current.empty = false;

      // найдем дочерние ноды. на самом деле должна быть либо одна указанная нода, либо ни одной, что заставляет
      // считать строку как описание
      var child = Trees.findAllRuleNodes(string,
        BSLMethodDescriptionParser.RULE_returnsValueString, BSLMethodDescriptionParser.RULE_subParameterString,
        BSLMethodDescriptionParser.RULE_typeWithDescription)
        .stream()
        .findFirst();

      // сразу необходимо обыграть неклассифицированную строку
      if (child.isEmpty()) {
        // если прочитанных параметров не было, то учтем как тип без описания
        if (current.children.isEmpty()) {
          current.addType(string.getText());
        } else {
          // считаем что текущая строка является продолжением описания прочитанного на шаге ранее
          current.appendDescription(string.getText());
        }
      } else {
        var typePart = (BSLParserRuleContext) child.get();
        // выполняется разбор параметров в зависимости от типа подстроки
        if (typePart.getRuleIndex() == BSLMethodDescriptionParser.RULE_subParameterString) {
          current.addSubParam((BSLMethodDescriptionParser.SubParameterStringContext) typePart);
        } else {
          current.addType(typePart);
        }
      }
    }

    return current.getReturnedValueDescription();
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает описание устаревшего метода
   *
   * @param ctx Дерево описания метода
   * @return Описание устаревшего метода
   */
  public static String readDeprecationInfo(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx.deprecate() != null) {
      var deprecationDescription = ctx.deprecate().deprecateDescription();
      if (deprecationDescription != null) {
        return deprecationDescription.getText().strip();
      }
    }
    return "";
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает список примеров (или вариантов вызова)
   *
   * @param ctx Дерево описания метода
   * @return Список примеров
   */
  public static List<String> readExamples(BSLMethodDescriptionParser.MethodDescriptionContext ctx, int ruleIndex) {
    var exampleStringNodes = Trees.findAllRuleNodes(ctx, ruleIndex);
    if (exampleStringNodes.isEmpty()) {
      return Collections.emptyList();
    } else {
      return exampleStringNodes.stream()
        .map(parseTree -> parseTree.getText().strip())
        .filter(str -> !str.isEmpty())
        .collect(Collectors.toList());
    }
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает описание назначения метода.
   *
   * @param ctx Дерево описания метода
   * @return Описание назначения метода
   */
  public static String readPurposeDescription(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx != null && ctx.description() != null) {
      var strings = ctx.description().descriptionString();
      if (strings != null) {
        return strings.stream()
          .map(BSLParserRuleContext::getText)
          .collect(Collectors.joining("\n"))
          .strip();
      }
    }
    return "";
  }

  private List<? extends BSLMethodDescriptionParser.ParametersStringContext> getParametersStrings(
    BSLMethodDescriptionParser.MethodDescriptionContext ast) {
    if (ast.parameters() != null) {
      var strings = ast.parameters().parametersString();
      if (strings != null) {
        return strings;
      }
    }
    return Collections.emptyList();
  }

  private List<? extends BSLMethodDescriptionParser.ReturnsValuesStringContext> getReturnedValuesStrings(
    BSLMethodDescriptionParser.MethodDescriptionContext ast) {
    if (ast.returnsValues() != null) {
      var strings = ast.returnsValues().returnsValuesString();
      if (strings != null) {
        return strings;
      }
    }
    return Collections.emptyList();
  }

  private static TempParameterData readSpecialParameterString(List<ParameterDescription> result,
                                                              TempParameterData current,
                                                              BSLParserRuleContext ctx) {
    // выполняется разбор параметров в зависимости от типа подстроки
    if (ctx.getRuleIndex() == BSLMethodDescriptionParser.RULE_parameterString) {
      saveLastParameter(current, result);
      current = new TempParameterData(ctx);
    } else if (ctx.getRuleIndex() == BSLMethodDescriptionParser.RULE_subParameterString) {
      current.addSubParam((BSLMethodDescriptionParser.SubParameterStringContext) ctx);
    } else { // BSLMethodDescriptionParser.RULE_typeWithDescription
      current.addType(ctx);
    }
    return current;
  }

  @NotNull
  private static TempParameterData readUnknownParameterString(List<ParameterDescription> result,
                                                              TempParameterData current,
                                                              BSLParserRuleContext string) {
    // Строка может состоят из одного слова и пробелов, запомним на будущее
    var isOneWord = string.getTokens().stream()
      .filter(t -> t.getType() != BSLMethodDescriptionParser.SPACE
        && t.getType() != BSLMethodDescriptionParser.EOL).count() == 1;

    // если прочитанных параметров не было, то учтем как параметр без описания
    if (isOneWord && current.isEmpty()) {
      current = new TempParameterData(string.getText());
      // некоторые параметры ранее были прочитаны, но если у них нет описания, то учтем как параметр без описания
    } else if (isOneWord && current.missingLastDescription()) {
      saveLastParameter(current, result);
      current = new TempParameterData(string.getText());
      // считаем что текущая строка является продолжением описания параметра, прочитанного на шаге ранее
    } else {
      current.appendDescription(string.getText());
    }
    return current;
  }

  private static void saveLastParameter(TempParameterData current, List<ParameterDescription> result) {
    if (current.isEmpty()) {
      return;
    }
    result.add(current.getParameterDescription());
  }

  /**
   * Служебный класс для временного хранения прочитанной информации из описания параметра
   */
  private static class TempParameterData {
    private String name;
    private final StringJoiner description;
    private boolean empty;
    private final List<TempParameterData> children;
    private TempParameterData last;
    private TempParameterData parent;
    private int level;

    TempParameterData() {
      name = "";
      description = new StringJoiner("\n");
      empty = true;
      children = new ArrayList<>();
      level = 0;
    }

    TempParameterData(String text) {
      this();
      name = text.strip();
      empty = false;
    }

    TempParameterData(BSLParserRuleContext ctx) {
      this();
      readAndAddType(ctx);
    }

    TempParameterData(BSLParserRuleContext ctx, TempParameterData current) {
      this();
      if (current != null) {
        level = current.level + 1;
        parent = current;
      }
      readAndAddType(ctx);
    }

    TempParameterData(String text, TempParameterData current) {
      this(text);
      if (current != null) {
        level = current.level;
        parent = current;
      }
    }

    private void readAndAddType(BSLParserRuleContext ctx) {
      Trees.getFirstChild(ctx, BSLMethodDescriptionParser.RULE_parameterName)
        .ifPresent((BSLParserRuleContext child) -> {
          name = child.getText();
          empty = false;
          addType(ctx);
        });
    }

    private boolean isEmpty() {
      return empty;
    }

    private void appendDescription(ParseTree ctx) {
      appendDescription(ctx.getText());
    }

    private void appendDescription(String text) {
      if (!isEmpty()) {
        if (last == null) {
          description.add(text.strip());
        } else {
          last.appendDescription(text);
        }
      }
    }

    private void addType(BSLParserRuleContext ctx) {
      if (isEmpty()) {
        return;
      }

      if (last != null && !last.children.isEmpty()) {
        last.addType(ctx);
      } else if (ctx.getRuleIndex() == BSLMethodDescriptionParser.RULE_typeWithDescription
        || ctx.getRuleIndex() == BSLMethodDescriptionParser.RULE_returnsValueString) {
        addNewType(ctx);
      } else {
        Trees.getFirstChild(ctx, BSLMethodDescriptionParser.RULE_typeWithDescription)
          .ifPresent(this::addNewType);
      }
    }

    private void addType(String text) {
      var newType = new TempParameterData(text, this);
      children.add(newType);
      last = newType;
    }

    private void addNewType(BSLParserRuleContext ctx) {
      last = null;
      Trees.getFirstChild(ctx, BSLMethodDescriptionParser.RULE_types).ifPresent(
        (BSLParserRuleContext typeString) -> {
          var newType = new TempParameterData(typeString.getText(), this);
          Trees.findAllRuleNodes(ctx, BSLMethodDescriptionParser.RULE_typeDescription)
            .stream().findFirst()
            .ifPresent(newType::appendDescription);
          children.add(newType);
          last = newType;
        });
    }

    private void addSubParam(BSLMethodDescriptionParser.SubParameterStringContext ctx) {
      if (isEmpty()) {
        return;
      }

      // если тип не определен был, то подчиненных параметров быть не может
      if (last == null) {
        appendDescription(ctx.getText());
        return;
      }
      var child = ctx.starPreffix();
      var subParamLevel = child.getText().length();
      TempParameterData subParameter;
      if (last.level >= subParamLevel) {
        while (last.level >= subParamLevel) {
          last = last.parent;
        }
      }
      subParameter = new TempParameterData(ctx, last);
      last.children.add(subParameter);
      last = subParameter.last;
    }

    private boolean missingLastDescription() {
      if (last != null) {
        return last.missingLastDescription();
      }
      return description.toString().isEmpty();
    }

    private ParameterDescription getParameterDescription() {
      var parameterTypes = children.stream()
        .map((TempParameterData child) -> {
          List<ParameterDescription> subParameters = new ArrayList<>();
          if (!child.children.isEmpty()) {
            child.children.forEach(subParam -> subParameters.add(subParam.getParameterDescription()));
          }
          return new TypeDescription(child.name, child.description.toString(), subParameters);
        }).collect(Collectors.toList());

      var descriptionStr = description.toString();
      if (descriptionStr.isEmpty() && parameterTypes.size() == 1) {
        descriptionStr = parameterTypes.get(0).getDescription();
      }
      return new ParameterDescription(name, parameterTypes, descriptionStr);
    }

    private List<TypeDescription> getReturnedValueDescription() {
      if (!isEmpty()) {
        return getParameterDescription().getTypes();
      }
      return Collections.emptyList();
    }
  }
}
