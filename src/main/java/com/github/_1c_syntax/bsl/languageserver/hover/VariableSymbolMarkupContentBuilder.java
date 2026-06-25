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
import com.github._1c_syntax.bsl.languageserver.types.model.LocalField;
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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    var eventParamDescription = descriptionFormatter.getEventHandlerParameterDescription(symbol);
    if (eventParamDescription != null && !eventParamDescription.isBlank()) {
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
    collectFieldBullets(bullets, types, lang, 0, docFieldIndex(symbol), new HashSet<>());
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
   * @param expandedSources функции-источники {@code см.}-ссылок, уже развёрнутые на
   *            текущем пути обхода. Поле, тип которого снова приводит к одной из них,
   *            не разворачивается повторно, а рендерится как {@code См. Функция} —
   *            это и обрывает взаимную/само-рекурсию (Контейнер↔Коробка) без
   *            искусственного лимита глубины.
   */
  private void collectFieldBullets(
    List<String> out, TypeSet types, Language lang, int indent, Map<String, DocField> doc,
    Set<Object> expandedSources
  ) {
    var pad = "  ".repeat(indent);
    var rendered = new HashSet<String>();
    for (var entry : collectFields(types).entrySet()) {
      var key = entry.getKey();
      rendered.add(key.toLowerCase(Locale.ROOT));
      var info = doc.get(key.toLowerCase(Locale.ROOT));
      renderInferredField(out, pad, key, entry.getValue(), types, lang, indent, info, expandedSources);
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
   * Отрендерить один выведенный инференсером ключ и, если он не образует цикла по
   * см.-ссылке, рекурсивно развернуть его вложенные поля.
   */
  private void renderInferredField(List<String> out, String pad, String key, LocalField field,
                                   TypeSet owner, Language lang, int indent, @Nullable DocField info,
                                   Set<Object> expandedSources) {
    var fieldTypes = field.types();
    // Описание: приоритет у doc-комментария (для параметров), иначе — описание
    // поля из модели типов (для локальной переменной из возврата функции).
    var description = info != null && !info.description().isBlank()
      ? info.description()
      : cleanupKeyDescription(field.description());

    // Функция-источник см.-ссылки, разворот которой даёт вложенные поля этого
    // ключа: собственная ленивая ссылка поля (`Содержимое - см. Коробка`), либо —
    // для коллекций — ленивый элемент (`Массив из см. Узел`). Именно её имя нужно
    // показать при обрыве цикла, а не источники уровнем глубже.
    var fieldSources = fieldExpansionSources(owner, key, fieldTypes);
    var cyclic = !fieldSources.isEmpty() && !Collections.disjoint(fieldSources, expandedSources);
    var seeLabel = cyclic ? seeReferenceLabel(fieldSources, lang) : "";
    var typeLabel = seeLabel.isBlank() ? fieldTypeLabel(fieldTypes, lang) : seeLabel;

    out.add(fieldBullet(pad, key, typeLabel, description));
    if (cyclic) {
      return;
    }
    var nextSources = expandedSources;
    if (!fieldSources.isEmpty()) {
      nextSources = new HashSet<>(expandedSources);
      nextSources.addAll(fieldSources);
    }
    collectFieldBullets(out, fieldTypes, lang, indent + 1,
      info == null ? Map.of() : info.children(), nextSources);
  }

  /**
   * Поля «открытого» объекта по всему набору типов: прямые {@code localFields}, а
   * для коллекций без собственных полей — поля типа-элемента (колонки строки
   * ТаблицыЗначений). Имена дедуплицируются (union-типы), значения объединяются.
   */
  private static Map<String, LocalField> collectFields(TypeSet types) {
    var fields = new LinkedHashMap<String, LocalField>();
    for (var ref : types.refs()) {
      var localFields = types.getLocalFields(ref);
      if (localFields.isEmpty()) {
        var elementTypes = types.getElementTypes(ref);
        for (var elemRef : elementTypes.refs()) {
          elementTypes.getLocalFields(elemRef).forEach((name, value) -> fields.merge(name, value, LocalField::merge));
        }
      } else {
        localFields.forEach((name, value) -> fields.merge(name, value, LocalField::merge));
      }
    }
    return fields;
  }

  /**
   * Функции-источники см.-ссылки, разворот которой даёт вложенные поля ключа
   * {@code key} объекта {@code owner}. Это либо собственная ленивая ссылка поля
   * (поле {@code owner.lazyFields[key]}), либо — если поле эагерное, но его тип —
   * коллекция с ленивым элементом — источник этого элемента. Возвращаемые ключи и
   * обнаруживают цикл, и дают имя для метки {@code См. Функция}.
   */
  private static Set<Object> fieldExpansionSources(TypeSet owner, String key, TypeSet fieldTypes) {
    var ownSource = lazyFieldSource(owner, key);
    if (ownSource != null) {
      return Set.of(ownSource);
    }
    var elementKeys = new HashSet<>();
    fieldTypes.lazyElements().values().forEach(lazy -> elementKeys.add(lazy.key()));
    return elementKeys;
  }

  /** Ключ собственной ленивой см.-ссылки поля {@code name} в наборе типов, либо {@code null}. */
  private static @Nullable Object lazyFieldSource(TypeSet owner, String name) {
    for (var byName : owner.lazyFields().values()) {
      for (var entry : byName.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue().types().key();
        }
      }
    }
    return null;
  }

  /**
   * Метка {@code См. Функция} для поля, чей тип задан {@code см.}-ссылкой,
   * образующей цикл. Пустая строка, если имя источника извлечь не удалось
   * (тогда вызывающий покажет обычную метку типа).
   */
  private static String seeReferenceLabel(Set<Object> sources, Language lang) {
    var prefix = lang == Language.EN ? "See " : "См. ";
    var names = sources.stream()
      .map(VariableSymbolMarkupContentBuilder::sourceName)
      .filter(name -> !name.isBlank())
      .distinct()
      .collect(Collectors.joining(" | "));
    return names.isBlank() ? "" : (prefix + names);
  }

  private static String sourceName(Object key) {
    return key instanceof MethodSymbol method ? method.getName() : "";
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
      line.append(" — ").append(description);
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
