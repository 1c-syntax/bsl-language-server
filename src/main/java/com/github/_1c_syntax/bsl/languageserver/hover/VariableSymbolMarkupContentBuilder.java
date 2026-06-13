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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class VariableSymbolMarkupContentBuilder implements MarkupContentBuilder {

  private static final String VARIABLE_KEY = "var";
  private static final String EXPORT_KEY = "export";
  private static final String TYPE_KEY = "type";

  private final LanguageServerConfiguration configuration;
  private final DescriptionFormatter descriptionFormatter;
  private final Resources resources;
  private final TypeService typeService;
  private final EventContractsIndex eventContractsIndex;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (VariableSymbol) reference.symbol();
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
    var typesInfo = getInferredTypes(symbol, typeService.typesAt(reference));
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, typesInfo);

    // местоположение переменной
    var location = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, location);

    // описание параметра из контракта события (для обработчиков платформенных
    // событий) — приоритетнее doc-комментария, который может устаревать
    var eventParamDescription = eventHandlerParameterDescription(symbol);
    if (!eventParamDescription.isBlank()) {
      descriptionFormatter.addSectionIfNotEmpty(markupBuilder, eventParamDescription);
    } else {
      symbol.getDescription()
        .map(VariableDescription::getPurposeDescription)
        .ifPresent(description -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, description));
    }

    symbol.getDescription()
      .flatMap(VariableDescription::getTrailingDescription)
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(trailingDescription -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, trailingDescription));

    var content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
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

  private String getInferredTypes(VariableSymbol symbol, TypeSet types) {
    if (types.isEmpty()) {
      return "";
    }
    // Hover — элемент интерфейса, поэтому язык отображения берём из настроек
    // LS (configuration), а не из ScriptVariant (язык исходников).
    var lang = configuration.getLanguage();
    String header = types.refs().stream()
      .map(ref -> inlineTypeLabel(types, ref, lang, false))
      .collect(Collectors.joining(" | "));

    var sb = new StringBuilder("%s: %s".formatted(getResourceString(TYPE_KEY), header));

    // Содержимое «открытых» объектов (Структура/Соответствие/Фиксированные,
    // строка ТаблицыЗначений) рендерим маркдаун-списком под заголовком типа.
    // Описания (и недостающие ключи) подмешиваем из doc-комментария параметра.
    var bullets = new ArrayList<String>();
    collectFieldBullets(bullets, types, lang, 0, docFieldIndex(symbol));
    if (!bullets.isEmpty()) {
      sb.append('\n');
      bullets.forEach(line -> sb.append('\n').append(line));
    }
    return sb.toString();
  }

  /**
   * Собрать строки маркдаун-списка для полей «открытого» объекта в наборе типов.
   * Поля берутся как из самих типов ({@link TypeSet#getLocalFields}), так и из
   * типов-элементов коллекции (например, колонки {@code СтрокаТаблицыЗначений},
   * подвешенной к {@code ТаблицаЗначений}). Поля union-типов дедуплицируются по
   * имени; вложенные структуры рекурсивно получают увеличенный отступ.
   *
   * @param doc описания ключей из doc-комментария (имя ключа в нижнем регистре →
   *            тип/описание/вложенные ключи); пустая мапа, если документации нет.
   */
  private void collectFieldBullets(
    List<String> out, TypeSet types, Language lang, int indent, Map<String, DocField> doc
  ) {
    var pad = "  ".repeat(indent);
    var rendered = new HashSet<String>();
    for (var entry : collectFields(types).entrySet()) {
      var key = entry.getKey();
      rendered.add(key.toLowerCase(Locale.ROOT));
      var fieldTypes = entry.getValue();
      var info = doc.get(key.toLowerCase(Locale.ROOT));
      out.add(fieldBullet(pad, key, fieldTypeLabel(fieldTypes, lang), info == null ? "" : info.description()));
      collectFieldBullets(out, fieldTypes, lang, indent + 1, info == null ? Map.of() : info.children());
    }
    // Ключи, описанные в doc-комментарии, но не выведенные инференсером.
    for (var info : doc.values()) {
      if (rendered.contains(info.name().toLowerCase(Locale.ROOT))) {
        continue;
      }
      out.add(fieldBullet(pad, info.name(), info.typeLabel(), info.description()));
      collectDocOnlyBullets(out, info.children(), indent + 1);
    }
  }

  /**
   * Поля «открытого» объекта по всему набору типов: прямые {@code localFields}, а
   * для коллекций без собственных полей — поля типа-элемента (колонки строки
   * ТаблицыЗначений). Имена дедуплицируются (union-типы), значения объединяются.
   */
  private static Map<String, TypeSet> collectFields(TypeSet types) {
    var fields = new LinkedHashMap<String, TypeSet>();
    for (var ref : types.refs()) {
      var localFields = types.getLocalFields(ref);
      if (localFields.isEmpty()) {
        var elementTypes = types.getElementTypes(ref);
        for (var elemRef : elementTypes.refs()) {
          elementTypes.getLocalFields(elemRef).forEach((name, value) -> fields.merge(name, value, TypeSet::union));
        }
      } else {
        localFields.forEach((name, value) -> fields.merge(name, value, TypeSet::union));
      }
    }
    return fields;
  }

  /** markdown-метка типов значения поля: имена в кавычках, объединение через {@code |}. */
  private String fieldTypeLabel(TypeSet fieldTypes, Language lang) {
    return fieldTypes.refs().stream()
      .map(ref -> inlineTypeLabel(fieldTypes, ref, lang, true))
      .collect(Collectors.joining(" | "));
  }

  /** Развернуть вложенные ключи, известные только из doc-комментария. */
  private static void collectDocOnlyBullets(List<String> out, Map<String, DocField> doc, int indent) {
    var pad = "  ".repeat(indent);
    for (var info : doc.values()) {
      out.add(fieldBullet(pad, info.name(), info.typeLabel(), info.description()));
      collectDocOnlyBullets(out, info.children(), indent + 1);
    }
  }

  private static String fieldBullet(String pad, String key, String typeLabel, String description) {
    var line = new StringBuilder(pad).append("* **").append(key).append("**");
    if (!typeLabel.isBlank()) {
      line.append(": ").append(typeLabel);
    }
    if (!description.isBlank()) {
      line.append(" - ").append(description);
    }
    return line.toString();
  }

  /**
   * Однострочная подпись типа: имя (опционально в обратных кавычках) и, для
   * коллекций, тип элемента через «из»/«Of». Поля здесь не разворачиваются —
   * они идут отдельным списком. У «открытых» объектов с собственными полями
   * тип элемента (КлючИЗначение) опускаем — он лишь шум на фоне списка ключей.
   *
   * @param code обрамлять ли имена типов обратными кавычками (для значений полей).
   */
  private String inlineTypeLabel(TypeSet owner, TypeRef ref, Language lang, boolean code) {
    var name = typeService.displayName(ref, lang);
    var label = code ? ("`" + name + "`") : name;
    var elementTypes = owner.getElementTypes(ref);
    if (!elementTypes.isEmpty() && owner.getLocalFields(ref).isEmpty()) {
      var elemJoined = elementTypes.refs().stream()
        .map(r -> inlineTypeLabel(elementTypes, r, lang, code))
        .collect(Collectors.joining(" | "));
      label = label + collectionOf(lang) + elemJoined;
    }
    return label;
  }

  /**
   * Дерево описаний ключей структуры, разобранное из doc-комментария параметра
   * (секция «Параметры:» с вложенными «* Ключ - Тип - описание»).
   *
   * @param name        исходное имя ключа (для doc-only ключей).
   * @param typeLabel   markdown-метка типов ключа (для doc-only ключей).
   * @param description описание ключа (может быть пустым).
   * @param children    вложенные ключи (имя в нижнем регистре → описание).
   */
  private record DocField(String name, String typeLabel, String description, Map<String, DocField> children) {
  }

  /**
   * Описание параметра из контракта платформенного события (bsl-context):
   * сопоставление <b>по позиции</b> — имена параметров обработчика задаёт
   * пользователь, в коде они могут не совпадать с именами в контракте.
   * При выходе за длину контракта возвращаем пусто, если последний параметр
   * контракта не variadic (хвост переменной арности).
   */
  private String eventHandlerParameterDescription(VariableSymbol symbol) {
    if (symbol.getKind() != VariableKind.PARAMETER
      || !(symbol.getScope() instanceof MethodSymbol method)) {
      return "";
    }
    var contractOpt = eventContractsIndex.getContract(method.getOwner(), method.getName());
    if (contractOpt.isEmpty()) {
      return "";
    }
    var paramIndex = indexOfParameter(method, symbol.getName());
    if (paramIndex < 0) {
      return "";
    }
    return parameterAt(contractOpt.get(), paramIndex)
      .map(p -> p.bilingualDescription().ru())
      .orElse("");
  }

  private static int indexOfParameter(MethodSymbol method, String name) {
    var params = method.getParameters();
    for (var i = 0; i < params.size(); i++) {
      if (params.get(i).getName().equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  private static Optional<ParameterDescriptor> parameterAt(MemberDescriptor contract, int index) {
    if (contract.signatures().isEmpty()) {
      return Optional.empty();
    }
    var params = contract.signatures().get(0).parameters();
    if (params.isEmpty()) {
      return Optional.empty();
    }
    var idx = index < params.size() ? index : (params.size() - 1);
    var p = params.get(idx);
    if (index >= params.size() && !p.variadic()) {
      return Optional.empty();
    }
    return Optional.of(p);
  }

  /** Описания ключей из doc-комментария параметра (для PARAMETER-переменной). */
  private Map<String, DocField> docFieldIndex(VariableSymbol symbol) {
    if (symbol.getKind() != VariableKind.PARAMETER
      || !(symbol.getScope() instanceof MethodSymbol method)) {
      return Map.of();
    }
    return method.getParameters().stream()
      .filter(parameter -> parameter.getName().equalsIgnoreCase(symbol.getName()))
      .findFirst()
      .flatMap(ParameterDefinition::getDescription)
      .map(description -> docFieldsFromTypes(description.types()))
      .orElseGet(Map::of);
  }

  private static Map<String, DocField> docFieldsFromTypes(List<TypeDescription> types) {
    var map = new LinkedHashMap<String, DocField>();
    for (var type : types) {
      for (ParameterDescription field : type.fields()) {
        // Имя типа в doc-комментарии может содержать перечисление через запятую
        // («Строка, Число») — разворачиваем в union с тем же разделителем, что и везде.
        var typeLabel = field.types().stream()
          .map(TypeDescription::name)
          .flatMap(typeName -> Stream.of(typeName.split(",")))
          .map(String::strip)
          .filter(typeName -> !typeName.isEmpty())
          .map(typeName -> "`" + typeName + "`")
          .collect(Collectors.joining(" | "));
        var description = field.types().stream()
          .map(TypeDescription::description)
          .filter(text -> text != null && !text.isBlank())
          .map(VariableSymbolMarkupContentBuilder::cleanupKeyDescription)
          .findFirst()
          .orElse("");
        var children = docFieldsFromTypes(field.types());
        map.putIfAbsent(
          field.name().toLowerCase(Locale.ROOT),
          new DocField(field.name(), typeLabel, description, children)
        );
      }
    }
    return map;
  }

  /** Убрать хвостовое двоеточие у описания ключа-структуры (перед списком подключей). */
  private static String cleanupKeyDescription(String text) {
    var trimmed = text.strip();
    if (trimmed.endsWith(":")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
    }
    return trimmed;
  }

  /** Разделитель «коллекция → тип элемента» в локали отображения. */
  private static String collectionOf(Language lang) {
    return lang == Language.EN ? " Of " : " из ";
  }

}
