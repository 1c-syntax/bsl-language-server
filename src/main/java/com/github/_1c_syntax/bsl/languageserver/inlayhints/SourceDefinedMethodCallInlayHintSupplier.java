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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.hover.DescriptionFormatter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
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
 * Объявление параметра уже известно на этапе построения хинта (метод
 * вызова разрешён по индексу ссылок), поэтому ссылка проставляется жадно — без
 * отложенного {@code inlayHint/resolve}. «Тяжёлых» полей, требующих ленивого
 * разрешения, у хинта нет, поэтому он не несёт {@code data}.
 */
@Component
public class SourceDefinedMethodCallInlayHintSupplier
  extends AbstractMethodCallInlayHintSupplier<DefaultInlayHintData> {

  // TODO: высчитать позицию хинта относительно последнего параметра.
  private static final boolean DEFAULT_SHOW_ALL_PARAMETERS = false;

  private final ReferenceIndex referenceIndex;
  private final DescriptionFormatter descriptionFormatter;

  public SourceDefinedMethodCallInlayHintSupplier(
    LanguageServerConfiguration configuration,
    ReferenceIndex referenceIndex,
    DescriptionFormatter descriptionFormatter
  ) {
    super(configuration);
    this.referenceIndex = referenceIndex;
    this.descriptionFormatter = descriptionFormatter;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Хинт имени параметра проставляет ссылку части метки жадно и не откладывает
   * полей на резолв — используется дефолтный дата-класс {@link DefaultInlayHintData}.
   *
   * @return Класс {@link DefaultInlayHintData}.
   */
  @Override
  public Class<DefaultInlayHintData> getInlayHintDataClass() {
    return DefaultInlayHintData.class;
  }

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var range = params.getRange();
    var references = referenceIndex.getMethodCallReferencesFrom(documentContext.getUri()).stream()
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
        .ifPresent(doCall -> result.addAll(toInlayHints(reference, doCall)));
    }
    return result;
  }

  private List<InlayHint> toInlayHints(
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

      setLabelAndPadding(inlayHint, parameter, passedValue, targetUri);
      setPosition(inlayHint, callParam);
      setTooltip(inlayHint, parameter);

      hints.add(inlayHint);
    }

    return hints;
  }

  private void setLabelAndPadding(
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
    // Объявление параметра уже разрешено — ссылка проставляется жадно.
    labelPart.setLocation(new Location(targetUri, parameter.getRange()));

    inlayHint.setLabel(List.of(labelPart));
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
