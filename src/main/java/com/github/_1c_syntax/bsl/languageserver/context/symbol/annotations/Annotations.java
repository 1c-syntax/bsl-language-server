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
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Построение моделей {@link Annotation} из узлов AST.
 * <p>
 * Общий код для вычислителей символов: аннотации могут стоять как над методами,
 * так и над переменными уровня модуля, поэтому разбор вынесен сюда.
 */
@UtilityClass
public class Annotations {

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
      .map(ParserRuleContext::getText)
      .map(Annotations::excludeTrailingQuotes)
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

  private static String excludeTrailingQuotes(String text) {
    if (text.length() > 2 && text.charAt(0) == '"') {
      return text.substring(1, text.length() - 1);
    }
    return text;
  }
}
