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
package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Построение моделей {@link Annotation} из узлов AST.
 * <p>
 * Общий код для вычислителей символов: аннотации могут стоять как над методами,
 * так и над переменными уровня модуля, поэтому разбор вынесен сюда.
 */
@UtilityClass
public class Annotations {

  /** Минимальная длина строкового литерала в кавычках ({@code ""}). */
  private static final int QUOTED_LITERAL_MIN_LENGTH = 2;

  /** Маркер строки-продолжения многострочного литерала: перевод строки + отступ + {@code |}. */
  private static final Pattern CONTINUATION_MARKER = Pattern.compile("(\\r?\\n)[ \\t]*\\|");

  /**
   * Построить список аннотаций из соответствующих узлов AST.
   */
  public static List<Annotation> from(List<? extends BSLParser.AnnotationContext> annotationContexts) {
    return annotationContexts.stream()
      .map(Annotations::from)
      .toList();
  }

  /**
   * Построить одну аннотацию из узла AST.
   */
  public static Annotation from(BSLParser.AnnotationContext annotation) {
    return Annotation.builder()
      .name(annotation.annotationName().getText().intern())
      .kind(AnnotationKind.of(annotation.annotationName().getStop().getType()))
      .parameters(parametersFrom(annotation.annotationParams()))
      .build();
  }

  private static List<AnnotationParameterDefinition> parametersFrom(
    BSLParser.AnnotationParamsContext annotationParamsContext
  ) {
    if (annotationParamsContext == null) {
      return Collections.emptyList();
    }

    return annotationParamsContext.annotationParam().stream()
      .map(Annotations::parameterFrom)
      .toList();
  }

  private static AnnotationParameterDefinition parameterFrom(BSLParser.AnnotationParamContext annotationParam) {
    var name = Optional.ofNullable(annotationParam.annotationParamName())
      .map(ParserRuleContext::getText)
      .orElse("");
    var value = Optional.ofNullable(annotationParam.annotationParamValue())
      .map(BSLParser.AnnotationParamValueContext::constValue)
      .map(Annotations::constValueText)
      .map(Either::<String, Annotation>forLeft)
      .or(
        () -> Optional.ofNullable(annotationParam.annotationParamValue())
          .map(BSLParser.AnnotationParamValueContext::annotation)
          .map(Annotations::from)
          .map(Either::<String, Annotation>forRight)
      )
      .orElse(Either.forLeft(""));
    var optional = annotationParam.annotationParamValue() != null;

    return new AnnotationParameterDefinition(name, value, optional);
  }

  /**
   * Логическое значение литерала-параметра аннотации.
   * <p>
   * Берётся исходный текст из потока символов (а не {@code getText()}, который
   * склеивает токены, теряя переводы строк), чтобы корректно собрать
   * многострочные строковые литералы.
   */
  private static String constValueText(BSLParser.ConstValueContext constValue) {
    return unwrapStringLiteral(rawText(constValue));
  }

  /** Исходный текст узла из потока символов — с сохранением переводов строк. */
  private static String rawText(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    if (start.getStartIndex() > stop.getStopIndex()) {
      return ctx.getText();
    }
    return start.getInputStream().getText(Interval.of(start.getStartIndex(), stop.getStopIndex()));
  }

  private static String unwrapStringLiteral(String text) {
    if (text.length() < QUOTED_LITERAL_MIN_LENGTH
      || text.charAt(0) != '"'
      || text.charAt(text.length() - 1) != '"') {
      return text;
    }
    // Снимаем обрамляющие кавычки. Для многострочного литерала строки-продолжения
    // начинаются с | (после необязательных отступов) — убираем маркер, сохраняя
    // сам перевод строки. В конце разэкранируем удвоённые кавычки ("" -> ").
    var inner = text.substring(1, text.length() - 1);
    return CONTINUATION_MARKER.matcher(inner).replaceAll("$1")
      .replace("\"\"", "\"");
  }
}
