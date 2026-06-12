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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.StringJoiner;

/**
 * Построитель контента для всплывающего окна для {@link MethodSymbol}.
 */
@Component
@RequiredArgsConstructor
public class MethodSymbolMarkupContentBuilder implements MarkupContentBuilder {

  private static final String EVENT_HANDLER_HEADER_KEY = "eventHandlerHeader";

  private final DescriptionFormatter descriptionFormatter;
  private final EventContractsIndex eventContractsIndex;
  private final Resources resources;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (MethodSymbol) reference.symbol();
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // местоположение метода
    // описание метода
    // параметры
    // возвращаемое значение
    // примеры
    // варианты вызова

    var eventContract = eventContractsIndex.getContract(symbol.getOwner(), symbol.getName());

    // сигнатура
    var signature = descriptionFormatter.getSignature(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение метода
    var methodLocation = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, methodLocation);

    // признак "обработчик события платформы" + платформенное описание события
    var eventSection = buildEventHandlerSection(eventContract,
      resources.getResourceString(getClass(), EVENT_HANDLER_HEADER_KEY));
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, eventSection);

    // описание метода
    var purposeSection = descriptionFormatter.getPurposeSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, purposeSection);

    // параметры: для обработчика — контракт события (имена/типы), иначе —
    // шапка-комментарий пользователя
    var parametersSection = eventContract.isPresent()
      ? descriptionFormatter.getParametersSection(eventContract.get())
      : descriptionFormatter.getParametersSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, parametersSection);

    // возвращаемое значение
    var returnedValueSection = descriptionFormatter.getReturnedValueSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, returnedValueSection);

    // примеры
    var examplesSection = descriptionFormatter.getExamplesSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, examplesSection);

    // варианты вызова
    var callOptionsSection = descriptionFormatter.getCallOptionsSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, callOptionsSection);

    var content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }


  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return MethodSymbol.class;
  }

  /**
   * Если метод является обработчиком платформенного события owner-типа
   * модуля, формирует секцию-шапку «&lt;header&gt;: &lt;имя&gt;» + описание
   * события из bsl-context. Заголовок локализован через ресурсы класса.
   */
  private static String buildEventHandlerSection(Optional<MemberDescriptor> contract, String header) {
    if (contract.isEmpty()) {
      return "";
    }
    var event = contract.get();
    var sj = new StringJoiner("\n");
    sj.add("**" + header + ":** `" + event.name() + "`");
    var description = event.description();
    if (description != null && !description.isBlank()) {
      sj.add("");
      sj.add(description);
    }
    return sj.toString();
  }

}
