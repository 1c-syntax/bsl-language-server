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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.hover.DescriptionFormatter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.apache.commons.lang3.Strings;
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
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Поставщик подсказок о параметрах вызываемого метода.
 * <p>
 * Метка хинта рендерится не голой строкой, а единственной частью
 * {@link InlayHintLabelPart}, к которой привязывается ссылка
 * ({@link InlayHintLabelPart#setLocation}) на объявление соответствующего
 * параметра в сигнатуре вызываемого source-defined метода. Клик по подсказке
 * выполняет переход к объявлению параметра.
 * <p>
 * Построение ссылки учитывает {@code inlayHint.resolveSupport} клиента: если
 * клиент объявил отложенное разрешение свойства {@code label.location}, ссылка
 * не строится жадно, а откладывается на {@code inlayHint/resolve} — в data
 * хинта кладутся координаты объявления параметра, а сама {@link Location}
 * собирается в {@link #resolve}. Если поддержки нет — ссылка проставляется сразу.
 */
@Component
public class SourceDefinedMethodCallInlayHintSupplier
  extends AbstractMethodCallInlayHintSupplier<MethodCallInlayHintData> {

  /**
   * Имя свойства {@code label.location} в {@code inlayHint.resolveSupport}:
   * объявив его, клиент сообщает о готовности дотягивать ссылку части метки
   * лениво через {@code inlayHint/resolve}.
   */
  private static final String LABEL_LOCATION_PROPERTY = "label.location";

  // TODO: высчитать позицию хинта относительно последнего параметра.
  private static final boolean DEFAULT_SHOW_ALL_PARAMETERS = false;

  private final ReferenceIndex referenceIndex;
  private final DescriptionFormatter descriptionFormatter;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Объявил ли клиент в inlayHint.resolveSupport свойство
  // label.location — то есть готов лениво дотягивать ссылку части метки через
  // inlayHint/resolve. Если да — Location откладывается на резолв (см. toInlayHints),
  // иначе проставляется жадно, как и tooltip.
  private boolean labelLocationResolveSupport;

  public SourceDefinedMethodCallInlayHintSupplier(
    LanguageServerConfiguration configuration,
    ReferenceIndex referenceIndex,
    DescriptionFormatter descriptionFormatter,
    ClientCapabilitiesHolder clientCapabilitiesHolder
  ) {
    super(configuration);
    this.referenceIndex = referenceIndex;
    this.descriptionFormatter = descriptionFormatter;
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
   * Хинт имени параметра откладывает построение ссылки части метки на резолв
   * (когда клиент это поддерживает), поэтому использует собственный дата-класс
   * {@link MethodCallInlayHintData}.
   *
   * @return Класс {@link MethodCallInlayHintData}.
   */
  @Override
  public Class<MethodCallInlayHintData> getInlayHintDataClass() {
    return MethodCallInlayHintData.class;
  }

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var range = params.getRange();
    var references = referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(reference -> Ranges.containsPosition(range, reference.selectionRange().getStart()))
      .filter(Reference::isSourceDefinedSymbolReference)
      .toList();
    if (references.isEmpty()) {
      return List.of();
    }

    // Один обход AST документа на все ссылки: индекс сопоставляет каждый вызов с
    // диапазоном имени метода (тем же, что хранится в reference.selectionRange()),
    // чтобы дальше резолвить вызов по ссылке за O(1) вместо обхода AST на каждую ссылку.
    var doCallRangeIndex = DoCallRangeIndex.of(documentContext);

    var result = new ArrayList<InlayHint>();
    for (var reference : references) {
      doCallRangeIndex.doCallFor(reference)
        .ifPresent(doCall -> result.addAll(toInlayHints(documentContext, reference, doCall)));
    }
    return result;
  }

  private List<InlayHint> toInlayHints(
    DocumentContext documentContext,
    Reference reference,
    BSLParser.DoCallContext doCall
  ) {

    var callParamList = doCall.callParamList();
    if (callParamList == null) {
      return List.of();
    }
    var callParams = callParamList.callParam();

    var methodSymbol = (MethodSymbol) reference.symbol();
    var parameters = methodSymbol.getParameters();
    var targetUri = methodSymbol.getOwner().getUri().toString();

    var hints = new ArrayList<InlayHint>();
    for (var i = 0; i < parameters.size(); i++) {

      // todo: show all parameters (in config)?
      if (callParams.size() < i + 1) {
        break;
      }

      var parameter = parameters.get(i);
      var callParam = callParams.get(i);

      var passedValue = callParam.getText();

      if (!showParametersWithTheSameName() && Strings.CI.contains(passedValue, parameter.getName())) {
        continue;
      }

      var inlayHint = new InlayHint();
      inlayHint.setKind(InlayHintKind.Parameter);

      setLabelAndPadding(documentContext, inlayHint, parameter, passedValue, targetUri);
      setPosition(inlayHint, callParam);
      setTooltip(inlayHint, parameter);

      hints.add(inlayHint);
    }

    return hints;
  }

  private void setLabelAndPadding(
    DocumentContext documentContext,
    InlayHint inlayHint,
    ParameterDefinition parameter,
    String passedValue,
    String targetUri
  ) {

    var defaultValue = parameter.getDefaultValue();

    var labelBuilder = new StringBuilder();
    labelBuilder.append(parameter.getName());

    if (showDefaultValues()
      && passedValue.isBlank()
      && !defaultValue.equals(ParameterDefinition.DefaultValue.EMPTY)
    ) {
      labelBuilder.append(" (");
      labelBuilder.append(defaultValue.value());
      labelBuilder.append(")");
    } else {
      labelBuilder.append(":");
      inlayHint.setPaddingRight(Boolean.TRUE);
    }

    var labelPart = new InlayHintLabelPart(labelBuilder.toString());
    var parameterRange = parameter.getRange();
    if (labelLocationResolveSupport) {
      // Ссылка строится лениво на inlayHint/resolve — в data кладём координаты
      // объявления параметра, остальное (value/position/kind) жадно.
      inlayHint.setData(new MethodCallInlayHintData(
        documentContext.getUri(),
        getId(),
        targetUri,
        parameterRange.getStart().getLine(),
        parameterRange.getStart().getCharacter(),
        parameterRange.getEnd().getLine(),
        parameterRange.getEnd().getCharacter()
      ));
    } else {
      labelPart.setLocation(new Location(targetUri, parameterRange));
    }

    inlayHint.setLabel(List.of(labelPart));
  }

  /**
   * Дорасчёт ссылки части метки хинта по ленивым данным
   * {@link MethodCallInlayHintData}.
   * <p>
   * Собирает {@link Location} объявления параметра по сохранённым координатам и
   * проставляет её единственной части метки хинта. Если метка хинта пуста (хинт
   * пришёл без частей) — возвращается без изменений.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param unresolved      Неразрешённый хинт с заполненным {@link InlayHint#getData()}.
   * @param data            Десериализованные данные хинта.
   * @return Разрешённый хинт с заполненной ссылкой части метки.
   */
  @Override
  public InlayHint resolve(
    DocumentContext documentContext,
    InlayHint unresolved,
    MethodCallInlayHintData data
  ) {
    var label = unresolved.getLabel();
    if (label == null || !label.isRight() || label.getRight().isEmpty()) {
      return unresolved;
    }
    var range = Ranges.create(
      data.getStartLine(), data.getStartCharacter(), data.getEndLine(), data.getEndCharacter()
    );
    var location = new Location(data.getTargetUri(), range);
    label.getRight().getFirst().setLocation(location);
    return unresolved;
  }

  private static void setPosition(InlayHint inlayHint, BSLParser.CallParamContext callParam) {
    var position = new Position(callParam.getStart().getLine() - 1, callParam.getStart().getCharPositionInLine());
    inlayHint.setPosition(position);
  }

  private void setTooltip(InlayHint inlayHint, ParameterDefinition parameter) {
    var markdown = descriptionFormatter.parameterToString(parameter);
    var tooltip = new MarkupContent(MarkupKind.MARKDOWN, markdown);
    inlayHint.setTooltip(tooltip);
  }
}
