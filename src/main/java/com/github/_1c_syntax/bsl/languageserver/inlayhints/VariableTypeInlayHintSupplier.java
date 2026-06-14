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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintCapabilities;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintResolveSupportCapabilities;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Поставщик подсказок о выведенном типе переменной.
 * <p>
 * Для присваивания вида {@code Перем = Выражение}, где тип правой части
 * выводится и нетривиален (не {@code Произвольный}/{@code any} и не очевиден
 * из литерала), показывает подсказку {@link InlayHintKind#Type} сразу после
 * имени переменной — например {@code Контрагент: Массив = Новый Массив()}.
 * <p>
 * Метка хинта рендерится единственной частью {@link InlayHintLabelPart}: когда
 * выведенный тип объявлен в исходниках рабочей области (общий модуль, модуль
 * менеджера объекта конфигурации, класс/модуль OneScript), к части привязывается
 * ссылка ({@link InlayHintLabelPart#setLocation}) на объявление типа — клик по
 * подсказке выполняет переход к модулю/классу. Платформенные и примитивные типы
 * ({@code Массив}, {@code Строка}, …) объявляющего исходник-символа не имеют —
 * для них метка остаётся без ссылки.
 * <p>
 * Построение ссылки учитывает {@code inlayHint.resolveSupport} клиента так же, как
 * {@link SourceDefinedMethodCallInlayHintSupplier}: если клиент объявил отложенное
 * разрешение свойства {@code label.location}, ссылка откладывается на
 * {@code inlayHint/resolve} (в data кладутся координаты объявления типа), иначе
 * проставляется жадно.
 */
@Component
public class VariableTypeInlayHintSupplier implements InlayHintSupplier<VariableTypeInlayHintData> {

  /**
   * Имя свойства {@code label.location} в {@code inlayHint.resolveSupport}:
   * объявив его, клиент сообщает о готовности дотягивать ссылку части метки
   * лениво через {@code inlayHint/resolve}.
   */
  private static final String LABEL_LOCATION_PROPERTY = "label.location";

  private final TypeService typeService;
  private final LanguageServerConfiguration configuration;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Объявил ли клиент в inlayHint.resolveSupport свойство
  // label.location — то есть готов лениво дотягивать ссылку части метки через
  // inlayHint/resolve. Если да — Location откладывается на резолв, иначе жадно.
  private boolean labelLocationResolveSupport;

  public VariableTypeInlayHintSupplier(
    TypeService typeService,
    LanguageServerConfiguration configuration,
    ClientCapabilitiesHolder clientCapabilitiesHolder
  ) {
    this.typeService = typeService;
    this.configuration = configuration;
    this.clientCapabilitiesHolder = clientCapabilitiesHolder;
  }

  /**
   * Кэширует на инициализации флаг поддержки клиентом отложенного разрешения
   * свойства {@code label.location} в {@code inlayHint.resolveSupport}.
   * <p>
   * Вызывается по событию {@link LanguageServerInitializeRequestReceivedEvent}
   * (а также из тестов для пересчёта флага после подмены возможностей клиента).
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    labelLocationResolveSupport = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getInlayHint)
      .map(InlayHintCapabilities::getResolveSupport)
      .map(InlayHintResolveSupportCapabilities::getProperties)
      .map(properties -> properties.contains(LABEL_LOCATION_PROPERTY))
      .orElse(Boolean.FALSE);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Хинт типа откладывает построение tooltip на резолв, поэтому использует
   * собственный дата-класс {@link VariableTypeInlayHintData}.
   */
  @Override
  public Class<VariableTypeInlayHintData> getInlayHintDataClass() {
    return VariableTypeInlayHintData.class;
  }

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
      .flatMap(Optional::stream)
      .toList();
  }

  private Optional<InlayHint> toInlayHint(
    DocumentContext documentContext,
    BSLParser.AssignmentContext assignment,
    Range range
  ) {
    var maybeIdentifier = simpleTargetIdentifier(assignment);
    if (maybeIdentifier.isEmpty()) {
      return Optional.empty();
    }
    var identifier = maybeIdentifier.get();

    var namePosition = Ranges.create(identifier).getEnd();
    if (!Ranges.containsPosition(range, namePosition)) {
      return Optional.empty();
    }

    var expression = assignment.expression();
    if (expression == null || isTrivialLiteral(expression) || isNewExpression(expression)) {
      return Optional.empty();
    }

    var maybeInferredType = inferType(documentContext, expression);
    if (maybeInferredType.isEmpty()) {
      return Optional.empty();
    }
    var inferredType = maybeInferredType.get();

    var typeName = typeService.displayName(inferredType, configuration.getLanguage());

    var inlayHint = new InlayHint();
    inlayHint.setKind(InlayHintKind.Type);
    inlayHint.setPosition(namePosition);
    inlayHint.setPaddingRight(Boolean.TRUE);

    var labelPart = new InlayHintLabelPart(": " + typeName);
    inlayHint.setLabel(List.of(labelPart));

    var declaration = typeService.definingSymbol(inferredType, documentContext).orElse(null);
    if (declaration == null) {
      // Платформенный/примитивный тип — объявляющего исходник-символа нет,
      // часть метки остаётся без ссылки. Tooltip всё равно дорассчитывается лениво,
      // поэтому data несёт только имя типа, без координат объявления.
      inlayHint.setData(noLocationData(documentContext, inferredType));
      return Optional.of(inlayHint);
    }

    var targetUri = declaration.getOwner().getUri().toString();
    var targetRange = declaration.getSelectionRange();
    if (labelLocationResolveSupport) {
      // Ссылка строится лениво на inlayHint/resolve — в data кладём координаты
      // объявления типа, остальное (label/position/kind) жадно.
      inlayHint.setData(locationData(documentContext, inferredType, targetUri, targetRange));
    } else {
      labelPart.setLocation(new Location(targetUri, targetRange));
      // Tooltip всё равно дорассчитывается лениво — data несёт имя типа без координат.
      inlayHint.setData(noLocationData(documentContext, inferredType));
    }
    return Optional.of(inlayHint);
  }

  private VariableTypeInlayHintData noLocationData(DocumentContext documentContext, TypeRef inferredType) {
    return new VariableTypeInlayHintData(
      documentContext.getUri(),
      getId(),
      inferredType.qualifiedName(),
      "",
      VariableTypeInlayHintData.NO_LOCATION,
      VariableTypeInlayHintData.NO_LOCATION,
      VariableTypeInlayHintData.NO_LOCATION,
      VariableTypeInlayHintData.NO_LOCATION
    );
  }

  private VariableTypeInlayHintData locationData(
    DocumentContext documentContext,
    TypeRef inferredType,
    String targetUri,
    Range targetRange
  ) {
    return new VariableTypeInlayHintData(
      documentContext.getUri(),
      getId(),
      inferredType.qualifiedName(),
      targetUri,
      targetRange.getStart().getLine(),
      targetRange.getStart().getCharacter(),
      targetRange.getEnd().getLine(),
      targetRange.getEnd().getCharacter()
    );
  }

  /**
   * Дорасчёт tooltip и (при наличии) ссылки части метки хинта типа по ленивым
   * данным {@link VariableTypeInlayHintData}.
   * <p>
   * Восстанавливает {@link TypeRef} по сохранённому имени и кладёт в tooltip
   * полное описание типа (markdown). Если тип не восстановлен — tooltip строится
   * по сохранённому имени. Если в данных есть координаты объявления типа
   * ({@link VariableTypeInlayHintData#hasLocation()}) — собирает {@link Location}
   * и проставляет её единственной части метки хинта (ленивое разрешение
   * {@code label.location}); если метка хинта пуста — ссылка не проставляется.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param unresolved      Неразрешённый хинт с заполненным {@link InlayHint#getData()}.
   * @param data            Десериализованные данные хинта.
   * @return Разрешённый хинт с заполненным tooltip и (при наличии) ссылкой части метки.
   */
  @Override
  public InlayHint resolve(
    DocumentContext documentContext,
    InlayHint unresolved,
    VariableTypeInlayHintData data
  ) {
    var typeRef = typeService.resolve(data.getTypeName(), documentContext.getFileType())
      .orElse(new TypeRef(TypeRef.UNKNOWN.kind(), data.getTypeName()));

    var displayName = typeService.displayName(typeRef, configuration.getLanguage());
    var description = typeService.getDescription(typeRef, configuration.getLanguage(), documentContext.getFileType());

    var markdown = description.isBlank() ? displayName : (displayName + "\n\n" + description);
    unresolved.setTooltip(new MarkupContent(MarkupKind.MARKDOWN, markdown));

    if (data.hasLocation()) {
      resolveLabelLocation(unresolved, data);
    }
    return unresolved;
  }

  private static void resolveLabelLocation(InlayHint unresolved, VariableTypeInlayHintData data) {
    var label = unresolved.getLabel();
    if (label == null || !label.isRight() || label.getRight().isEmpty()) {
      return;
    }
    var range = Ranges.create(
      data.getStartLine(), data.getStartCharacter(), data.getEndLine(), data.getEndCharacter()
    );
    label.getRight().getFirst().setLocation(new Location(data.getTargetUri(), range));
  }

  /**
   * Идентификатор простой переменной-цели присваивания ({@code Перем = ...}).
   *
   * @param assignment Контекст присваивания.
   * @return Идентификатор простой переменной-цели, либо {@code empty}, если цель —
   *   обращение к члену/индексу ({@code Перем.Поле = ...}).
   */
  private static Optional<TerminalNode> simpleTargetIdentifier(BSLParser.AssignmentContext assignment) {
    var lValue = assignment.lValue();
    if (lValue == null || lValue.IDENTIFIER() == null || lValue.acceptor() != null) {
      return Optional.empty();
    }
    return Optional.of(lValue.IDENTIFIER());
  }

  /**
   * Единственный выведенный тип выражения правой части присваивания.
   *
   * @param documentContext Контекст документа.
   * @param expression      Выражение правой части присваивания.
   * @return Выведенный тип, либо {@code empty}, если тип не выведен, выведен как
   *   union из нескольких типов или тривиален ({@link TypeRef#ANY}/{@link TypeRef#UNKNOWN}).
   */
  private Optional<TypeRef> inferType(DocumentContext documentContext, BSLParser.ExpressionContext expression) {
    var start = expression.getStart();
    var position = new Position(start.getLine() - 1, start.getCharPositionInLine());
    var types = typeService.expressionTypesAt(documentContext, position);
    if (types.size() != 1) {
      return Optional.empty();
    }
    var ref = types.refs().iterator().next();
    if (ref.equals(TypeRef.ANY) || ref.equals(TypeRef.UNKNOWN)) {
      return Optional.empty();
    }
    return Optional.of(ref);
  }

  /**
   * Правая часть — единственный литерал ({@code = 1}, {@code = "Текст"},
   * {@code = Истина}): тип очевиден из записи, подсказка не нужна.
   */
  private static boolean isTrivialLiteral(BSLParser.ExpressionContext expression) {
    if (!expression.operation().isEmpty() || expression.member().size() != 1) {
      return false;
    }
    var member = expression.member().getFirst();
    return member.constValue() != null;
  }

  /**
   * Правая часть — конструктор {@code Новый Тип(...)} без дальнейших обращений:
   * тип очевиден из самой записи {@code Новый Тип}, подсказка не нужна.
   * <p>
   * Путь в грамматике: {@code expression → member → complexIdentifier →
   * newExpression}. Проверяется единственный член без операций и без
   * модификаторов ({@code .Поле}, {@code [...]}, {@code (...)} после конструктора),
   * чтобы не скрывать хинт для случаев вида {@code Новый Массив().Найти(...)}.
   */
  private static boolean isNewExpression(BSLParser.ExpressionContext expression) {
    if (!expression.operation().isEmpty() || expression.member().size() != 1) {
      return false;
    }
    var complexIdentifier = expression.member().getFirst().complexIdentifier();
    return complexIdentifier != null
      && complexIdentifier.newExpression() != null
      && complexIdentifier.modifier().isEmpty();
  }
}
