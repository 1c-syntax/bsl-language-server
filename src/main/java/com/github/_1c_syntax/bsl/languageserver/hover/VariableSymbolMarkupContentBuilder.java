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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VariableSymbolMarkupContentBuilder implements MarkupContentBuilder<VariableSymbol> {

  private static final String VARIABLE_KEY = "var";
  private static final String EXPORT_KEY = "export";
  private static final String TYPE_KEY = "type";

  private final LanguageServerConfiguration configuration;
  private final DescriptionFormatter descriptionFormatter;
  private final Resources resources;
  private final TypeService typeService;

  @Override
  public MarkupContent getContent(VariableSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // информация о переменной
    // местоположение переменной
    // описание переменной

    // сигнатура
    String signature = descriptionFormatter.getSignature(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // информация о переменной
    var variableInfo = getVariableInfo(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, variableInfo);

    // тип (выведенный)
    var typesInfo = getInferredTypes(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, typesInfo);

    // местоположение переменной
    var location = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, location);

    // описание переменной
    symbol.getDescription()
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(description -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, description));

    symbol.getDescription()
      .flatMap(VariableDescription::getTrailingDescription)
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(trailingDescription -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, trailingDescription));

    var content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Variable;
  }

  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return VariableSymbol.class;
  }

  private String getVariableInfo(VariableSymbol symbol) {
    return switch (symbol.getKind()) {
      case GLOBAL -> getResourceString("globalVariable");
      case MODULE -> getResourceString("moduleVariable");
      case LOCAL -> getResourceString("localVariable").formatted(symbol.getScope().getName());
      case PARAMETER -> getResourceString("methodParameter").formatted(symbol.getScope().getName());
      case DYNAMIC -> symbol.getScope().getSymbolKind() == SymbolKind.Module
        ? getResourceString("dynamicVariableOfModule")
        : getResourceString("dynamicVariableOfMethod").formatted(symbol.getScope().getName());
    };
  }

  private String getResourceString(String key) {
    return resources.getResourceString(getClass(), key);
  }

  private String getInferredTypes(VariableSymbol symbol) {
    TypeSet types = typeService.findTypes(symbol);
    if (types.isEmpty()) {
      return "";
    }
    // Hover — элемент интерфейса, поэтому язык отображения берём из настроек
    // LS (configuration), а не из ScriptVariant (язык исходников).
    var lang = configuration.getLanguage();
    String joined = types.refs().stream()
      .map(ref -> renderRef(types, ref, lang))
      .collect(Collectors.joining(" | "));
    return "%s: %s".formatted(getResourceString(TYPE_KEY), joined);
  }

  private String renderRef(TypeSet owner, TypeRef ref, Language lang) {
    var name = typeService.displayName(ref, lang);
    var elementTypes = owner.getElementTypes(ref);
    if (!elementTypes.isEmpty()) {
      var elemJoined = elementTypes.refs().stream()
        .map(r -> renderRef(elementTypes, r, lang))
        .collect(Collectors.joining(", "));
      name = name + collectionOf(lang) + elemJoined;
    }
    var fields = owner.getLocalFields(ref);
    if (!fields.isEmpty()) {
      var fieldsJoined = fields.entrySet().stream()
        .map(e -> e.getKey() + ": " + e.getValue().refs().stream()
          .map(r -> renderRef(e.getValue(), r, lang))
          .collect(Collectors.joining(" | ")))
        .collect(Collectors.joining(", "));
      name = name + " { " + fieldsJoined + " }";
    }
    return name;
  }

  /** Разделитель «коллекция → тип элемента» в локали отображения. */
  private static String collectionOf(Language lang) {
    return lang == Language.EN ? " Of " : " из ";
  }

}
