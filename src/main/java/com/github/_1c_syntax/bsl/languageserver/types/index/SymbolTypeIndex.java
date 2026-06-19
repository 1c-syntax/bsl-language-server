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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.CollectionTypeDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@WorkspaceScope
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
   * Развернуть hyperlink-ссылку {@code Модуль.Метод} / {@code Модуль.Метод.Параметр}
   * через {@link TypeRegistry} и {@link TypeRegistry#getMembers}.
   * <p>
   * Алгоритм: от самого длинного префикса к короткому пробуем
   * {@code TypeRegistry.resolve(prefix)}; остальные сегменты — имена членов
   * (или параметра в случае последнего сегмента). Возвращает {@link TypeSet}
   * c одним элементом или {@link TypeSet#EMPTY}, если ссылка не разворачивается.
   */
  public TypeSet resolveHyperlink(String link, FileType fileType) {
    if (link == null || link.isBlank()) {
      return TypeSet.EMPTY;
    }
    var parts = link.split("\\.");
    if (parts.length == 0) {
      return TypeSet.EMPTY;
    }
    for (int prefixLen = parts.length - 1; prefixLen >= 1; prefixLen--) {
      var head = String.join(".", Arrays.copyOfRange(parts, 0, prefixLen));
      var headRef = typeRegistry.resolve(head, fileType).orElse(null);
      if (headRef == null) {
        continue;
      }
      var resolved = walkMembers(headRef, parts, prefixLen, fileType);
      if (resolved != null) {
        return resolved;
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Пройти по оставшимся сегментам ссылки, начиная с {@code parts[startIndex]},
   * через members типа. Последний сегмент может оказаться именем параметра
   * метода (записи вида {@code Модуль.Метод.Параметр}) — тогда возвращаются
   * его типы.
   *
   * @return TypeSet, если все сегменты успешно разрешены; {@code null} при
   *         неудаче (вызывающий может попробовать более короткий префикс).
   */
  @Nullable
  private TypeSet walkMembers(TypeRef headRef, String[] parts, int startIndex, FileType fileType) {
    TypeRef current = headRef;
    MemberDescriptor lastMethod = null;
    for (int i = startIndex; i < parts.length; i++) {
      var name = parts[i];
      var member = findMember(current, name, fileType);
      if (member == null) {
        // Если предыдущий сегмент был method и текущее имя — параметр этого
        // метода (запись вида Модуль.Метод.Параметр), вернём типы параметра.
        if (lastMethod != null && i == parts.length - 1) {
          var paramTypes = parameterFromMember(lastMethod, name);
          if (paramTypes != null && !paramTypes.isEmpty()) {
            return paramTypes;
          }
        }
        return null;
      }
      var next = member.returnType();
      if (next == null || next.kind() == TypeKind.UNKNOWN) {
        return null;
      }
      current = next;
      lastMethod = member.kind() == MemberKind.METHOD ? member : null;
    }
    return TypeSet.of(current);
  }

  @Nullable
  private MemberDescriptor findMember(TypeRef typeRef, String name, FileType fileType) {
    for (var member : typeRegistry.getMembers(typeRef, fileType)) {
      if (member.matches(name)) {
        return member;
      }
    }
    return null;
  }

  /**
   * Достать типы параметра по имени из сигнатур member-метода.
   * Возвращает {@code null}, если такого параметра нет.
   */
  @Nullable
  private static TypeSet parameterFromMember(MemberDescriptor member, String parameterName) {
    for (var signature : member.signatures()) {
      for (var parameter : signature.parameters()) {
        if (parameter.matches(parameterName)) {
          return parameter.types();
        }
      }
    }
    return null;
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

  /**
   * Разрешить список описаний типов в {@link TypeSet} on-demand, с навешиванием
   * полей структур/ТЗ ({@link TypeDescription#fields()}) и элементов коллекций.
   * В отличие от {@link #getDeclaredReturnTypes(MethodSymbol)} не требует
   * предварительной индексации — вычисляет напрямую по описанию.
   */
  public TypeSet resolveDescribedTypes(List<? extends TypeDescription> descriptions) {
    return resolveTypes(descriptions);
  }

  private TypeSet resolveTypes(List<? extends TypeDescription> descriptions) {
    if (descriptions == null || descriptions.isEmpty()) {
      return TypeSet.EMPTY;
    }
    TypeSet acc = TypeSet.EMPTY;
    for (var td : descriptions) {
      acc = acc.union(applyFields(resolveTypeDescription(td), td));
    }
    return acc;
  }

  /**
   * Разрешить одно описание типа в {@link TypeSet}.
   * <ul>
   *   <li>{@code HYPERLINK} ({@code См. Метод} / {@code См. Справочник.X}) сам
   *       по себе тип не образует — его резолвят consumer'ы, имеющие контекст
   *       документа; возвращается {@link TypeSet#EMPTY}.</li>
   *   <li>{@code COLLECTION} ({@code Массив из X, Y}) — головной тип берётся
   *       из {@link CollectionTypeDescription#collectionName()}, элементы
   *       коллекции — рекурсивно из {@link CollectionTypeDescription#valueTypes()}
   *       и навешиваются через {@link TypeSet#withElement(TypeRef, TypeSet)}.</li>
   *   <li>{@code SIMPLE} — простое имя резолвится через {@link TypeRegistry}.</li>
   * </ul>
   */
  private TypeSet resolveTypeDescription(TypeDescription td) {
    return switch (td.variant()) {
      case HYPERLINK -> TypeSet.EMPTY;
      case SIMPLE -> resolveOne(td.name()).map(TypeSet::of).orElse(TypeSet.EMPTY);
      case COLLECTION -> resolveCollection((CollectionTypeDescription) td);
    };
  }

  private TypeSet resolveCollection(CollectionTypeDescription td) {
    var headRef = resolveOne(td.collectionName()).orElse(null);
    if (headRef == null) {
      return TypeSet.EMPTY;
    }
    var elementTypes = resolveTypes(td.valueTypes());
    if (elementTypes.isEmpty()) {
      return TypeSet.of(headRef);
    }
    return TypeSet.of(headRef).withElement(headRef, elementTypes);
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
        result = result.withField(headRef, field.name(), fieldTypes, fieldDescription(field));
      }
    }
    return result;
  }

  /**
   * Текстовое описание поля из doc-комментария: первое непустое описание среди
   * типов поля ({@code * Поле - Тип - текст}). У самого {@link TypeDescription}
   * поля описания нет — оно лежит на типах поля.
   */
  private static String fieldDescription(ParameterDescription field) {
    return field.types().stream()
      .map(TypeDescription::description)
      .filter(text -> text != null && !text.isBlank())
      .findFirst()
      .map(String::strip)
      .orElse("");
  }

  private Optional<TypeRef> resolveOne(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var head = name.trim();
    return typeRegistry.resolve(head)
      .or(() -> Optional.of(typeRegistry.intern(TypeKind.USER, head)));
  }
}
