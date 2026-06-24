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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LazyTypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.CollectionTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
   * Типы параметра, объявленные в описании метода. Вычисляется on-demand —
   * декларации параметров уже распарсены парсером. {@code См.}-ссылки (в т.ч.
   * вложенные — элементы коллекций и поля структур) разворачиваются через
   * {@code owner}.
   *
   * @param parameter параметр.
   * @param owner     документ-владелец метода — для разворота {@code См.}-ссылок.
   * @return набор типов параметра; {@link TypeSet#EMPTY}, если тип не объявлен.
   */
  public TypeSet getDeclaredParameterTypes(ParameterDefinition parameter, DocumentContext owner) {
    return parameter.getDescription()
      .map(descr -> resolveTypes(descr.types(),
        new ResolutionContext(owner, owner.getFileType(), new HashSet<>())))
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
        var returnedValue = descr.getReturnedValue();
        var owner = method.getOwner();
        // visited содержит уже посещённые при развороте См.-цепочек локальные
        // функции (защита от закольцованных ссылок); сам индексируемый метод —
        // тоже в наборе, чтобы оборвать самоссылку.
        var visited = new HashSet<MethodSymbol>();
        visited.add(method);
        var context = new ResolutionContext(owner, owner.getFileType(), visited);
        // Прямые типы и См.-ссылки (// Возвращаемое значение: см. Метод, включая
        // вложенные — элементы коллекций и поля структур) разворачиваются единым
        // проходом: см.-ссылка резолвится через resolveSeeReference при наличии owner.
        var returnTypes = resolveTypes(returnedValue, context);
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
   * Развернуть {@code См.}-ссылку (из описания возвращаемого значения, параметра
   * или висячего комментария переменной) в {@link TypeSet}.
   * <ul>
   *   <li>квалифицированная ссылка {@code Модуль.Метод} / {@code Тип.Член} —
   *       через {@link #resolveHyperlink(String, FileType)};</li>
   *   <li>неквалифицированная ссылка на функцию того же модуля — её возвращаемый
   *       тип: сначала из уже проиндексированных типов
   *       ({@link #getDeclaredReturnTypes(MethodSymbol)}, поэтому разворачиваются
   *       и цепочки {@code см.}), а на этапе самой индексации (когда цель ещё не
   *       проиндексирована) — напрямую из описания (с полями структуры/ТЗ
   *       и элементами коллекций из JsDoc);</li>
   *   <li>иначе ссылка трактуется как имя типа и резолвится через
   *       {@link TypeRegistry}.</li>
   * </ul>
   * <p>
   * Единая точка разворачивания {@code См.}-ссылок: используется и индексацией
   * возвращаемых значений, и выводом типов параметров
   * ({@code ExpressionTypeInferencer}), и резолвером висячих комментариев
   * переменных ({@code MemberTypeFromCommentResolver}).
   *
   * @param link     имя/ссылка из {@code См.}-ссылки (без текста описания).
   * @param owner    документ-владелец — для поиска локальной функции.
   * @param fileType язык владельца — для резолва имён.
   * @return {@link TypeSet} (возможно с {@code localFields}); {@link TypeSet#EMPTY},
   *         если ссылка не разворачивается.
   */
  public TypeSet resolveSeeReference(String link, DocumentContext owner, FileType fileType) {
    return resolveSeeReference(link, owner, fileType, new HashSet<>());
  }

  /**
   * Вариант {@link #resolveSeeReference(String, DocumentContext, FileType)} с
   * набором уже посещённых локальных функций — для защиты от закольцованных
   * {@code см.}-ссылок при рекурсивном разворачивании вложенных типов
   * (элементов коллекций и полей структур).
   */
  private TypeSet resolveSeeReference(
    String link,
    DocumentContext owner,
    FileType fileType,
    Set<MethodSymbol> visited
  ) {
    // Парсер не отдаёт null: Hyperlink.link()/TypeDescription.name() в крайнем
    // случае возвращают пустую строку, поэтому достаточно проверки на пустоту.
    if (link.isBlank()) {
      return TypeSet.EMPTY;
    }
    if (link.contains(".")) {
      var hyperlinkTypes = resolveHyperlink(link, fileType);
      if (!hyperlinkTypes.isEmpty()) {
        return hyperlinkTypes;
      }
      // Не разрешилось как ссылка на член (Модуль.Метод / Тип.Член) — пробуем
      // трактовать как полное имя типа (например, квалифицированный платформенный
      // тип) через TypeRegistry ниже.
    }
    var localFunction = findLocalFunction(owner, link);
    if (localFunction != null) {
      return resolveLocalFunctionTypes(localFunction, owner, fileType, visited);
    }
    return typeRegistry.resolve(link, fileType).map(TypeSet::of).orElse(TypeSet.EMPTY);
  }

  /**
   * Возвращаемый тип локальной функции, на которую указывает {@code см.}-ссылка.
   * <p>
   * Предпочитаем уже проиндексированный тип (в т.ч. с раскрытыми цепочками см.);
   * если цель ещё не проиндексирована (вызов из самой индексации) — резолвим
   * напрямую из описания. {@code visited} скоупится на текущий путь обхода:
   * после возврата из ветки функция убирается, иначе вторая (нециклическая)
   * ссылка на неё из соседнего поля/элемента ложно считалась бы циклом.
   */
  private TypeSet resolveLocalFunctionTypes(MethodSymbol localFunction, DocumentContext owner,
                                            FileType fileType, Set<MethodSymbol> visited) {
    var cached = getDeclaredReturnTypes(localFunction);
    if (!cached.isEmpty()) {
      return cached;
    }
    if (!visited.add(localFunction)) {
      return TypeSet.EMPTY;
    }
    try {
      var returnedValue = localFunction.getDescription()
        .map(MethodDescription::getReturnedValue)
        .orElse(List.of());
      return resolveTypes(returnedValue, new ResolutionContext(owner, fileType, visited));
    } finally {
      visited.remove(localFunction);
    }
  }

  /**
   * Контекст разворачивания {@code см.}-ссылок при рекурсивном резолве типов.
   *
   * @param owner    документ-владелец описания — для поиска локальной функции.
   * @param fileType язык владельца — для резолва имён.
   * @param visited  уже посещённые локальные функции — защита от закольцованных ссылок.
   */
  private record ResolutionContext(
    DocumentContext owner,
    FileType fileType,
    Set<MethodSymbol> visited
  ) {
  }

  /**
   * Разрешить список описаний типов в {@link TypeSet} с навешиванием полей
   * структур/ТЗ и элементов коллекций. {@code См.}-ссылки разворачиваются
   * единообразно на любом уровне (см. {@link #resolveTypeDescription}).
   */
  private TypeSet resolveTypes(List<? extends TypeDescription> descriptions, ResolutionContext context) {
    if (descriptions == null || descriptions.isEmpty()) {
      return TypeSet.EMPTY;
    }
    TypeSet acc = TypeSet.EMPTY;
    for (var td : descriptions) {
      var resolved = resolveTypeDescription(td, context);
      // У коллекций (`Соответствие из КлючИЗначение: * Ключ - ...`) поля описывают
      // ЭЛЕМЕНТ и навешиваются внутри resolveCollection; у простых типов
      // (`Структура: * Поле - ...`) — на сам тип.
      if (td.variant() != TypeDescription.Variant.COLLECTION) {
        resolved = applyFields(resolved, td, context);
      }
      acc = acc.union(resolved);
    }
    return acc;
  }

  /**
   * Разрешить одно описание типа в {@link TypeSet}.
   * <ul>
   *   <li>{@code HYPERLINK} ({@code См. Метод} / {@code См. Справочник.X})
   *       разворачивается через {@link #resolveSeeReference}. Работает одинаково
   *       на верхнем уровне и во вложенных позициях (элементы коллекций, поля
   *       структур).</li>
   *   <li>{@code COLLECTION} ({@code Массив из X, Y}) — головной тип берётся
   *       из {@link CollectionTypeDescription#collectionName()}, элементы
   *       коллекции — рекурсивно из {@link CollectionTypeDescription#valueTypes()}
   *       и навешиваются через {@link TypeSet#withElement(TypeRef, TypeSet)}.</li>
   *   <li>{@code SIMPLE} — простое имя резолвится через {@link TypeRegistry}.</li>
   * </ul>
   */
  private TypeSet resolveTypeDescription(TypeDescription td, ResolutionContext context) {
    return switch (td.variant()) {
      case HYPERLINK ->
        resolveSeeReference(td.name(), context.owner(), context.fileType(), context.visited());
      case SIMPLE -> resolveOne(td.name()).map(TypeSet::of).orElse(TypeSet.EMPTY);
      case COLLECTION -> resolveCollection((CollectionTypeDescription) td, context);
    };
  }

  private TypeSet resolveCollection(CollectionTypeDescription td, ResolutionContext context) {
    var headRef = resolveOne(td.collectionName()).orElse(null);
    if (headRef == null) {
      return TypeSet.EMPTY;
    }
    var result = TypeSet.of(headRef);
    for (var valueType : td.valueTypes()) {
      var localFunction = localFunctionSeeRef(valueType, context);
      if (localFunction != null) {
        // Тип элемента задан см.-ссылкой на локальную функцию — возможно
        // самоссылочную (дерево). Храним ленивую ссылку: реальный тип берётся
        // из её возвращаемого значения на чтении, глубина — по выражению курсора.
        result = result.withLazyElement(headRef, lazyReturnTypes(localFunction));
      } else {
        // Поля коллекции (`* Ключ - Строка`) относятся к элементу (КлючИЗначение,
        // строке ТЗ и т.п.), поэтому навешиваем их на тип элемента, а не на голову.
        var eager = applyFields(resolveTypes(List.of(valueType), context), td, context);
        if (!eager.isEmpty()) {
          result = result.withElement(headRef, eager);
        }
      }
    }
    return result;
  }

  /**
   * Если у описания типа есть {@link TypeDescription#fields() поля}
   * (декларация структуры/ТЗ ключами через {@code * Поле - Тип}),
   * навесить их на головной {@link TypeRef}. Поле, типизированное см.-ссылкой
   * на локальную функцию, навешивается лениво ({@link TypeSet#withLazyField}) —
   * для поддержки рекурсивных структур.
   */
  private TypeSet applyFields(TypeSet base, TypeDescription td, ResolutionContext context) {
    var fields = td.fields();
    if (fields == null || fields.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var headRef = base.refs().iterator().next();
    var result = base;
    for (var field : fields) {
      var eager = TypeSet.EMPTY;
      for (var fieldType : field.types()) {
        var localFunction = localFunctionSeeRef(fieldType, context);
        if (localFunction != null) {
          result = result.withLazyField(headRef, field.name(),
            lazyReturnTypes(localFunction), fieldDescription(field));
        } else {
          eager = eager.union(resolveTypes(List.of(fieldType), context));
        }
      }
      if (!eager.isEmpty()) {
        result = result.withField(headRef, field.name(), eager, fieldDescription(field));
      }
    }
    return result;
  }

  /**
   * Если {@code td} — неквалифицированная {@code см.}-ссылка на функцию того же
   * модуля, вернуть её символ; иначе {@code null}. Квалифицированные ссылки
   * ({@code Модуль.Метод}) и имена типов не рекурсивны — резолвятся eager.
   */
  @Nullable
  private static MethodSymbol localFunctionSeeRef(TypeDescription td, ResolutionContext context) {
    if (td.variant() != TypeDescription.Variant.HYPERLINK) {
      return null;
    }
    var name = td.name();
    if (name.isBlank() || name.contains(".")) {
      return null;
    }
    return findLocalFunction(context.owner(), name);
  }

  @Nullable
  private static MethodSymbol findLocalFunction(DocumentContext owner, String name) {
    return owner.getSymbolTree().getMethods().stream()
      .filter(candidate -> candidate.isFunction() && candidate.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElse(null);
  }

  /** Ленивая ссылка на возвращаемый тип функции (из кэша, на момент чтения). */
  private LazyTypeSet lazyReturnTypes(MethodSymbol function) {
    return new LazyTypeSet(function, () -> getDeclaredReturnTypes(function));
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
