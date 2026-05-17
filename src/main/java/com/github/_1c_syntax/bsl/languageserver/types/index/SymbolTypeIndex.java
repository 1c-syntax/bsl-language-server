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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс декларативных типов символов.
 * <p>
 * Для {@link MethodSymbol} eagerly кэширует return-types из
 * {@code MethodDescription.returnedValue}. Для {@link ParameterDefinition} типы
 * читаются on-demand из {@code ParameterDescription.types()} —
 * это дёшево и не требует отдельного хранения.
 * <p>
 * Типы переменных в этом индексе не хранятся: они вычисляются
 * {@code ExpressionTypeInferencer}'ом по выражению инициализации.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class SymbolTypeIndex {

  private final TypeRegistry typeRegistry;

  private final Map<MethodSymbol, TypeSet> declaredReturnTypes = new ConcurrentHashMap<>();
  private final Map<URI, List<MethodSymbol>> indexedByUri = new ConcurrentHashMap<>();

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    var uri = documentContext.getUri();

    clear(uri);

    var collected = new ArrayList<MethodSymbol>();
    indexMethodsRecursive(documentContext.getSymbolTree().getModule(), collected);
    indexedByUri.put(uri, collected);
  }

  /**
   * @return объявленные типы возвращаемого значения метода либо пустой {@link TypeSet}.
   */
  public TypeSet getDeclaredReturnTypes(MethodSymbol method) {
    return declaredReturnTypes.getOrDefault(method, TypeSet.EMPTY);
  }

  /**
   * @return типы параметра, объявленные в описании метода. Вычисляется on-demand —
   *         декларации параметров уже распарсены парсером.
   */
  public TypeSet getDeclaredParameterTypes(ParameterDefinition parameter) {
    return parameter.getDescription()
      .map(descr -> resolveTypes(descr.types()))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Очистить записи, относящиеся к данному URI.
   */
  public void clear(URI uri) {
    var methods = indexedByUri.remove(uri);
    if (methods == null) {
      return;
    }
    for (var m : methods) {
      declaredReturnTypes.remove(m);
    }
  }

  private void indexMethodsRecursive(SourceDefinedSymbol parent, List<MethodSymbol> collected) {
    if (parent instanceof MethodSymbol method) {
      method.getDescription().ifPresent(descr -> {
        var returnTypes = resolveTypes(descr.getReturnedValue());
        if (!returnTypes.isEmpty()) {
          declaredReturnTypes.put(method, returnTypes);
          collected.add(method);
        }
      });
    }
    for (var child : parent.getChildren()) {
      indexMethodsRecursive(child, collected);
    }
  }

  private TypeSet resolveTypes(List<? extends TypeDescription> descriptions) {
    if (descriptions == null || descriptions.isEmpty()) {
      return TypeSet.EMPTY;
    }
    TypeSet acc = TypeSet.EMPTY;
    TypeRef pendingCollection = null;
    for (var td : descriptions) {
      var name = td.name();
      if (name == null) {
        continue;
      }
      // Hyperlink (`См. Метод` / `См. Справочник.X`) сам по себе тип не образует —
      // его резолвят consumer'ы, имеющие контекст документа.
      if (td.variant() == TypeDescription.Variant.HYPERLINK) {
        continue;
      }
      // Парсер bsl-parser разбивает `Массив из Число, Строка` на два
      // TypeDescription'а: «Массив из Число» и «Строка». Если простая запись
      // следует за коллекцией с element-types — трактуем её как продолжение
      // element-types этой коллекции.
      if (pendingCollection != null && !looksLikeCollectionHead(name) && !name.contains(".")) {
        var elementRef = resolveOne(name).orElse(null);
        if (elementRef != null) {
          acc = acc.withElement(pendingCollection, TypeSet.of(elementRef));
          continue;
        }
      }
      var single = applyFields(resolveTypeDescription(name), td);
      acc = acc.union(single);
      final var snapshot = single;
      pendingCollection = snapshot.refs().stream()
        .filter(ref -> !snapshot.getElementTypes(ref).isEmpty())
        .findFirst()
        .orElse(null);
    }
    return acc;
  }

  /**
   * Если у описания типа есть {@link TypeDescription#fields() поля}
   * (декларация структуры/ТЗ ключами через {@code * Поле - Тип}),
   * навесить их на головной {@link TypeRef} через
   * {@link TypeSet#withField(TypeRef, String, TypeSet)}.
   */
  private TypeSet applyFields(TypeSet base, TypeDescription td) {
    var fields = td.fields();
    if (fields == null || fields.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var headRef = base.refs().iterator().next();
    var result = base;
    for (var field : fields) {
      var fieldTypes = resolveTypes(field.types());
      if (!fieldTypes.isEmpty()) {
        result = result.withField(headRef, field.name(), fieldTypes);
      }
    }
    return result;
  }

  private static final Set<String> COLLECTION_HEADS = Set.of(
    "массив", "array",
    "фиксированныймассив", "fixedarray",
    "соответствие", "map",
    "фиксированноесоответствие", "fixedmap",
    "таблицазначений", "valuetable",
    "деревозначений", "valuetree",
    "списокзначений", "valuelist",
    "структура", "structure",
    "фиксированнаяструктура", "fixedstructure"
  );

  /**
   * @return {@code true} если запись начинается с известного имени коллекции.
   */
  private static boolean looksLikeCollectionHead(String name) {
    var trimmed = name.trim();
    var headEnd = findHeadEnd(trimmed);
    var head = trimmed.substring(0, headEnd).toLowerCase(Locale.ROOT);
    return COLLECTION_HEADS.contains(head);
  }

  /**
   * Разобрать запись типа из JsDoc вида
   * {@code "Head"} / {@code "Head из Tail"} / {@code "Head<Tail>"} /
   * {@code "Head[Tail]"} и вернуть {@link TypeSet} с заголовочным
   * {@link TypeRef} и (если есть) типами элементов через
   * {@link TypeSet#withElement(TypeRef, TypeSet)}.
   */
  private TypeSet resolveTypeDescription(String name) {
    if (name == null || name.isBlank()) {
      return TypeSet.EMPTY;
    }
    var trimmed = name.trim();
    var headEnd = findHeadEnd(trimmed);
    var head = trimmed.substring(0, headEnd);
    var headRef = resolveOne(head).orElse(null);
    if (headRef == null) {
      return TypeSet.EMPTY;
    }
    var tail = extractTail(trimmed, headEnd);
    if (tail.isEmpty()) {
      return TypeSet.of(headRef);
    }
    TypeSet elementTypes = TypeSet.EMPTY;
    for (var part : tail.split(",")) {
      var partTrim = part.trim();
      if (partTrim.isEmpty()) {
        continue;
      }
      elementTypes = elementTypes.union(resolveTypeDescription(partTrim));
    }
    if (elementTypes.isEmpty()) {
      return TypeSet.of(headRef);
    }
    return TypeSet.of(headRef).withElement(headRef, elementTypes);
  }

  /**
   * Найти границу головного идентификатора: первый whitespace, '<' или '['.
   */
  private static int findHeadEnd(String s) {
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      if (Character.isWhitespace(c) || c == '<' || c == '[') {
        return i;
      }
    }
    return s.length();
  }

  /**
   * Достать «хвост» — список типов элементов, ободрав ключевое слово {@code из}
   * (или {@code of}) и закрывающие угловые/квадратные скобки.
   */
  private static String extractTail(String full, int headEnd) {
    if (headEnd >= full.length()) {
      return "";
    }
    var rest = full.substring(headEnd).trim();
    if (rest.isEmpty()) {
      return "";
    }
    if (rest.startsWith("<") && rest.endsWith(">")) {
      return rest.substring(1, rest.length() - 1).trim();
    }
    if (rest.startsWith("[") && rest.endsWith("]")) {
      return rest.substring(1, rest.length() - 1).trim();
    }
    // Russian "из" / English "of" префикс — отбрасываем.
    var lowered = rest.toLowerCase(Locale.ROOT);
    if (lowered.startsWith("из ")) {
      return rest.substring(3).trim();
    }
    if (lowered.startsWith("of ")) {
      return rest.substring(3).trim();
    }
    return "";
  }

  private Optional<TypeRef> resolveOne(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    // Парсер возвращает имена коллекций как "Массив<Произвольный>" / "Массив из Произвольный".
    // Берём только головной идентификатор для резолва через TypeRegistry.
    var head = name.trim().split("[\\s<\\[]", 2)[0];
    return typeRegistry.resolve(head)
      .or(() -> Optional.of(typeRegistry.intern(TypeKind.USER, head)));
  }
}
