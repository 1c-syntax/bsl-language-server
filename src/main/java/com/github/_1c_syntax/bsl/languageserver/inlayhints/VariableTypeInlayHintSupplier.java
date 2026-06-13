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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Поставщик подсказок о выведенном типе переменной.
 * <p>
 * Для присваивания вида {@code Перем = Выражение}, где тип правой части
 * выводится и нетривиален (не {@code Произвольный}/{@code any} и не очевиден
 * из литерала), показывает подсказку {@link InlayHintKind#Type} сразу после
 * имени переменной — например {@code Контрагент: Массив = Новый Массив()}.
 */
@Component
@RequiredArgsConstructor
public class VariableTypeInlayHintSupplier implements InlayHintSupplier {

  private final TypeService typeService;
  private final LanguageServerConfiguration configuration;
  private final JsonMapper jsonMapper;

  /**
   * Получение подсказок о выведенном типе переменных в присваиваниях.
   * <p>
   *
   * {@inheritDoc}
   */
  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var range = params.getRange();
    return Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast)
      .map(assignment -> toInlayHint(documentContext, assignment, range))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private InlayHint toInlayHint(DocumentContext documentContext, BSLParser.AssignmentContext assignment, Range range) {
    var identifier = simpleTargetIdentifier(assignment);
    if (identifier == null) {
      return null;
    }

    var namePosition = Ranges.create(identifier, identifier).getEnd();
    if (!Ranges.containsPosition(range, namePosition)) {
      return null;
    }

    var expression = assignment.expression();
    if (expression == null || isTrivialLiteral(expression)) {
      return null;
    }

    var inferredType = inferType(documentContext, expression);
    if (inferredType == null) {
      return null;
    }

    var typeName = typeService.displayName(inferredType, configuration.getLanguage());

    var inlayHint = new InlayHint();
    inlayHint.setKind(InlayHintKind.Type);
    inlayHint.setLabel(": " + typeName);
    inlayHint.setPosition(namePosition);
    inlayHint.setPaddingRight(Boolean.TRUE);
    // Tooltip (полное описание типа) строится лениво на inlayHint/resolve —
    // в data кладём ссылку на тип, остальное (label/position/kind) жадно.
    inlayHint.setData(new VariableTypeInlayHintData(getId(), documentContext.getUri(), inferredType.qualifiedName()));
    return inlayHint;
  }

  /**
   * Дорасчёт tooltip хинта типа по ленивым данным {@link VariableTypeInlayHintData}.
   * <p>
   * Восстанавливает {@link TypeRef} по сохранённому имени и кладёт в tooltip
   * полное описание типа (markdown). Если данных нет либо тип не восстановлен —
   * хинт возвращается без изменений.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param inlayHint       Неразрешённый хинт с заполненным {@link InlayHint#getData()}.
   * @return Разрешённый хинт с заполненным tooltip.
   */
  @Override
  public InlayHint resolve(DocumentContext documentContext, InlayHint inlayHint) {
    var data = extractData(inlayHint);
    if (data == null) {
      return inlayHint;
    }

    var typeRef = typeService.resolve(data.typeName(), documentContext.getFileType())
      .orElse(new TypeRef(TypeRef.UNKNOWN.kind(), data.typeName()));

    var displayName = typeService.displayName(typeRef, configuration.getLanguage());
    var description = typeService.getDescription(typeRef, configuration.getLanguage(), documentContext.getFileType());

    var markdown = description.isBlank() ? displayName : displayName + "\n\n" + description;
    inlayHint.setTooltip(new MarkupContent(MarkupKind.MARKDOWN, markdown));
    return inlayHint;
  }

  private VariableTypeInlayHintData extractData(InlayHint inlayHint) {
    var rawData = inlayHint.getData();
    if (rawData == null) {
      return null;
    }
    if (rawData instanceof VariableTypeInlayHintData data) {
      return data;
    }
    // Клиент присылает data назад как JSON-объект — восстанавливаем round-trip'ом.
    return jsonMapper.convertValue(rawData, VariableTypeInlayHintData.class);
  }

  /**
   * Идентификатор простой переменной-цели присваивания ({@code Перем = ...}),
   * либо {@code null}, если цель — обращение к члену/индексу ({@code Перем.Поле = ...}).
   */
  private static TerminalNode simpleTargetIdentifier(BSLParser.AssignmentContext assignment) {
    var lValue = assignment.lValue();
    if (lValue == null || lValue.IDENTIFIER() == null || lValue.acceptor() != null) {
      return null;
    }
    return lValue.IDENTIFIER();
  }

  /**
   * Единственный выведенный тип выражения правой части присваивания, либо
   * {@code null}, если тип не выведен, выведен как union из нескольких типов
   * или тривиален ({@link TypeRef#ANY}/{@link TypeRef#UNKNOWN}).
   */
  private TypeRef inferType(DocumentContext documentContext, BSLParser.ExpressionContext expression) {
    var start = expression.getStart();
    var position = new Position(start.getLine() - 1, start.getCharPositionInLine());
    var types = typeService.expressionTypesAt(documentContext, position);
    if (types.size() != 1) {
      return null;
    }
    var ref = types.refs().iterator().next();
    if (ref.equals(TypeRef.ANY) || ref.equals(TypeRef.UNKNOWN)) {
      return null;
    }
    return ref;
  }

  /**
   * Правая часть — единственный литерал ({@code = 1}, {@code = "Текст"},
   * {@code = Истина}): тип очевиден из записи, подсказка не нужна.
   */
  private static boolean isTrivialLiteral(BSLParser.ExpressionContext expression) {
    if (expression == null || !expression.operation().isEmpty() || expression.member().size() != 1) {
      return false;
    }
    var member = expression.member().getFirst();
    return member.constValue() != null;
  }
}
