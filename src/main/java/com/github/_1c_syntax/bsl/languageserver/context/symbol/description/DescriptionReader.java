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
package com.github._1c_syntax.bsl.languageserver.context.symbol.description;

import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Вспомогательный класс для чтения данных из описания метода
 */
@UtilityClass
public class DescriptionReader {

  private static final int HYPERLINK_REF_LEN = 4;

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и формирует список описаний параметров метода
   *
   * @param ctx Дерево описания метода
   * @return Список описаний параметров метода
   */
  public static List<ParameterDescription> readParameters(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {

    // параметров нет
    if (ctx.parameters() == null) {
      return Collections.emptyList();
    }

    // есть только гиперссылка вместо параметров
    if (ctx.parameters().hyperlinkBlock() != null) {
      List<ParameterDescription> result = new ArrayList<>();
      if (ctx.parameters().hyperlinkBlock().hyperlinkType() != null) {
        result.add(new ParameterDescription(
          "",
          Collections.emptyList(),
          getDescriptionString(ctx.parameters().hyperlinkBlock()).substring(HYPERLINK_REF_LEN),
          true
        ));
      }
      return result;
    }

    // блок параметры есть, но самих нет
    if (ctx.parameters().parameterString() == null) {
      return Collections.emptyList();
    }

    return getParametersStrings(ctx.parameters().parameterString());

  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и формирует список описаний возвращаемых значений
   *
   * @param ctx Дерево описания метода
   * @return Список описаний возвращаемых значений
   */
  public static List<TypeDescription> readReturnedValue(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {

    // возвращаемого значения нет
    if (ctx.returnsValues() == null) {
      return Collections.emptyList();
    }

    // есть только гиперссылка вместо значения
    if (ctx.returnsValues().hyperlinkBlock() != null) {
      List<TypeDescription> result = new ArrayList<>();
      if (ctx.returnsValues().hyperlinkBlock().hyperlinkType() != null) {
        result.add(new TypeDescription(
          "",
          "",
          Collections.emptyList(),
          getDescriptionString(ctx.returnsValues().hyperlinkBlock()).substring(HYPERLINK_REF_LEN),
          true
        ));
      }
      return result;
    }

    // блок возвращаемого значения есть, но самих нет
    if (ctx.returnsValues().returnsValuesString() == null) {
      return Collections.emptyList();
    }

    var fakeParam = new TempParameterData("");
    for (BSLMethodDescriptionParser.ReturnsValuesStringContext string : ctx.returnsValues().returnsValuesString()) {
      // это строка с возвращаемым значением
      if (string.returnsValue() != null) {
        fakeParam.addType(string.returnsValue().type(), string.returnsValue().typeDescription());
      } else if (string.typesBlock() != null) { // это строка с описанием параметра
        fakeParam.addType(string.typesBlock().type(), string.typesBlock().typeDescription());
      } else if (string.typeDescription() != null) { // это строка с описанием
        fakeParam.addTypeDescription(string.typeDescription());
      } else if (string.subParameter() != null) { // это строка с вложенным параметром типа
        fakeParam.addSubParameter(string.subParameter());
      } else { // прочее - пустая строка
        // noop
      }
    }

    return fakeParam.makeParameterDescription().types();
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
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает список вариантов вызова
   *
   * @param ctx Дерево описания метода
   * @return Список вариантов вызова
   */
  public static List<String> readCallOptions(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx.callOptions() != null) {
      var strings = ctx.callOptions().callOptionsString();
      if (strings != null) {
        return strings.stream()
          .map(DescriptionReader::getDescriptionString)
          .filter((String s) -> !s.isBlank())
          .map(String::intern)
          .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает список примеров
   *
   * @param ctx Дерево описания метода
   * @return Список примеров
   */
  public static List<String> readExamples(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx.examples() != null) {
      var strings = ctx.examples().examplesString();
      if (strings != null) {
        return strings.stream()
          .map(DescriptionReader::getDescriptionString)
          .filter((String s) -> !s.isBlank())
          .map(String::intern)
          .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и возвращает описание назначения метода.
   *
   * @param ctx Дерево описания метода
   * @return Описание назначения метода
   */
  public static String readPurposeDescription(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx.descriptionBlock() != null) {
      if (ctx.descriptionBlock().description() != null) {
        var strings = ctx.descriptionBlock().description().descriptionString();
        if (strings != null) {
          return strings.stream()
            .map(DescriptionReader::getDescriptionString)
            .collect(Collectors.joining("\n"))
            .strip();
        }
      }
      if (ctx.descriptionBlock().hyperlinkBlock() != null) {
        return getDescriptionString(ctx.descriptionBlock().hyperlinkBlock());
      }
    }
    return "";
  }

  /**
   * Выполняет разбор прочитанного AST дерева описания метода и достает описание назначения метода.
   * Если описание метода представляет собой только ссылку, то возвращает ее значение, иначе - пустая строка
   *
   * @param ctx Дерево описания метода
   * @return Ссылка в методе
   */
  public static String readLink(BSLMethodDescriptionParser.MethodDescriptionContext ctx) {
    if (ctx.descriptionBlock() != null && ctx.descriptionBlock().hyperlinkBlock() != null) {
      return getDescriptionString(ctx.descriptionBlock().hyperlinkBlock()).substring(HYPERLINK_REF_LEN);
    }
    return "";
  }

  private String getDescriptionString(ParserRuleContext ctx) {
    var strings = new StringJoiner("");
    for (int i = 0; i < ctx.getChildCount(); i++) {
      var child = ctx.getChild(i);

      if (!(child instanceof BSLMethodDescriptionParser.StartPartContext)) {
        strings.add(child.getText());
      }
    }

    return strings.toString().strip();
  }

  private List<ParameterDescription> getParametersStrings(
    List<? extends BSLMethodDescriptionParser.ParameterStringContext> strings) {

    List<ParameterDescription> result = new ArrayList<>();
    var current = new TempParameterData();

    for (BSLMethodDescriptionParser.ParameterStringContext string : strings) {
      // это строка с параметром
      if (string.parameter() != null) {
        if (!current.isEmpty()) {
          result.add(current.makeParameterDescription());
        }
        current = new TempParameterData(string.parameter());
      } else if (string.typesBlock() != null) { // это строка с описанием параметра
        current.addType(string.typesBlock().type(), string.typesBlock().typeDescription());
      } else if (string.typeDescription() != null) { // это строка с описанием
        if (current.isEmpty()) {
          var text = string.typeDescription().getText().strip();
          if (text.split("\\s").length == 1) {
            current = new TempParameterData(text);
          }
        } else {
          current.addTypeDescription(string.typeDescription());
        }
      } else if (string.subParameter() != null) { // это строка с вложенным параметром типа
        current.addSubParameter(string.subParameter());
      } else { // прочее - пустая строка
        // noop
      }
    }

    if (!current.isEmpty()) {
      result.add(current.makeParameterDescription());
    }

    return result;
  }

  /**
   * Служебный класс для временного хранения прочитанной информации из описания параметра
   */
  private static final class TempParameterData {
    private String name;
    private boolean empty;
    private final List<TempParameterTypeData> types;
    private int level;

    private TempParameterData() {
      this.name = "";
      this.empty = true;
      this.types = new ArrayList<>();
      this.level = 1;
    }

    private TempParameterData(BSLMethodDescriptionParser.ParameterContext parameter) {
      this();
      if (parameter.parameterName() != null) {
        this.name = parameter.parameterName().getText().strip().intern();
        this.empty = false;
        if (parameter.typesBlock() != null) {
          addType(parameter.typesBlock().type(), parameter.typesBlock().typeDescription());
        }
      }
    }

    private TempParameterData(BSLMethodDescriptionParser.SubParameterContext subParameter, int level) {
      this();
      this.level = level;
      if (subParameter.parameterName() != null) {
        this.name = subParameter.parameterName().getText().strip().intern();
        this.empty = false;
        if (subParameter.typesBlock() != null) {
          addType(subParameter.typesBlock().type(), subParameter.typesBlock().typeDescription());
        }
      }
    }

    private TempParameterData(String name) {
      this();
      this.name = name.strip().intern();
      this.empty = false;
    }

    private boolean isEmpty() {
      return empty;
    }

    private Optional<TempParameterTypeData> lastType() {
      if (!types.isEmpty()) {
        return Optional.of(types.get(types.size() - 1));
      }
      return Optional.empty();
    }

    private ParameterDescription makeParameterDescription() {
      var parameterTypes = types.stream()
        .map((TempParameterTypeData child) -> {
          List<ParameterDescription> subParameters = new ArrayList<>();
          if (!child.subParameters.isEmpty()) {
            child.subParameters.forEach(subParam -> subParameters.add(subParam.makeParameterDescription()));
          }
          var link = "";
          if (child.isHyperlink) {
            link = child.name.substring(HYPERLINK_REF_LEN);
          }
          return new TypeDescription(
            child.name.intern(),
            child.description.toString(),
            subParameters,
            link,
            child.isHyperlink
          );
        }).collect(Collectors.toList());
      return new ParameterDescription(name.intern(), parameterTypes, "", false);
    }

    private void addType(BSLMethodDescriptionParser.@Nullable TypeContext paramType,
                         BSLMethodDescriptionParser.@Nullable TypeDescriptionContext paramDescription) {
      if (isEmpty() || paramType == null) {
        return;
      }

      if (paramType.listTypes() != null) {
        var stringTypes = paramType.listTypes().getText().split(",");
        for (String stringType : stringTypes) {
          if (!stringType.isBlank()) {
            addType(paramDescription, stringType.strip(), false);
          }
        }
      } else if (paramType.hyperlinkType() != null) {
        addType(paramDescription, paramType.hyperlinkType().getText(), true);
      } else if (paramType.simpleType() != null) {
        addType(paramDescription, paramType.simpleType().getText(), false);
      } else if (paramType.complexType() != null) {
        addType(paramDescription, paramType.complexType().getText(), false);
      } else {
        // noop
      }
    }

    private void addType(BSLMethodDescriptionParser.@Nullable TypeDescriptionContext descriptionContext,
                         String text,
                         boolean isHyperlink) {
      var newType = new TempParameterTypeData(text, level, isHyperlink);
      if (descriptionContext != null) {
        newType.addTypeDescription(descriptionContext);
      }
      types.add(newType);
    }

    private void addTypeDescription(BSLMethodDescriptionParser.TypeDescriptionContext typeDescription) {
      lastType().ifPresent(lastType -> lastType.addTypeDescription(typeDescription));
    }

    private void addSubParameter(BSLMethodDescriptionParser.SubParameterContext subParameter) {
      lastType().ifPresent(lastType -> lastType.addSubParameter(subParameter));
    }
  }

  /**
   * Служебный класс для временного хранения прочитанной информации из описания типа
   */
  private static final class TempParameterTypeData {
    private final String name;
    private final StringJoiner description;
    private final int level;
    private final List<TempParameterData> subParameters;
    private final boolean isHyperlink;

    private TempParameterTypeData(String name, int level, boolean isHyperlink) {
      this.name = name.intern();
      this.description = new StringJoiner("\n");
      this.level = level;
      this.subParameters = new ArrayList<>();
      this.isHyperlink = isHyperlink;
    }

    private void addTypeDescription(BSLMethodDescriptionParser.TypeDescriptionContext typeDescription) {
      if (typeDescription.getText() != null) {
        this.description.add(typeDescription.getText().strip());
      }
    }

    private Optional<TempParameterData> lastSubParameter() {
      if (!subParameters.isEmpty()) {
        return Optional.of(subParameters.get(subParameters.size() - 1));
      }
      return Optional.empty();
    }

    private void addSubParameter(BSLMethodDescriptionParser.SubParameterContext subParameter) {
      var star = subParameter.getToken(BSLMethodDescriptionParser.STAR, 0);
      if (star == null) {
        return;
      }

      if (star.getText().length() == level) {
        subParameters.add(new TempParameterData(subParameter, level + 1));
      } else {
        lastSubParameter().ifPresent(subParam -> subParam.addSubParameter(subParameter));
      }
    }
  }
}
