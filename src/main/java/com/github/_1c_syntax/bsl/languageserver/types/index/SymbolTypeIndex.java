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
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.OpenDataObjectInference;
import com.github._1c_syntax.bsl.languageserver.types.model.LazyTypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.CollectionTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
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

  /** Минимальное число сегментов квалифицированной ссылки ({@code Модуль.Метод}). */
  private static final int MIN_QUALIFIED_SEGMENTS = 2;

  /** Коллекция строк — у дерева значений строки лежат в ней, а не в самом дереве. */
  private static final String ROWS = "Строки";

  /** Свойство таблицы формы с типом её строки. */
  private static final String CURRENT_DATA = "ТекущиеДанные";

  /** Наименьшая ссылка на метаданные: вид объекта, его имя и имя подчинённого. */
  private static final int MIN_METADATA_SEGMENTS = 3;

  /** Номер части ссылки на метаданные: вид объекта. */
  private static final int KIND_PART = 0;

  /** Номер части ссылки на метаданные: имя объекта. */
  private static final int NAME_PART = 1;

  /** Номер части ссылки на метаданные: имя подчинённого — табличной части либо реквизита. */
  private static final int CHILD_PART = 2;

  /** Номер части ссылки на метаданные: имя реквизита табличной части. */
  private static final int ATTRIBUTE_PART = 3;

  private static final String OBJECT = "Объект.";
  private static final String TABULAR_SECTION = "ТабличнаяЧасть.";
  private static final String TABULAR_SECTION_ROW = "ТабличнаяЧастьСтрока.";

  private final TypeRegistry typeRegistry;
  private final FormByNameResolver formByNameResolver;

  private final Map<MethodSymbol, TypeSet> declaredReturnTypes = new ConcurrentHashMap<>();
  private final Map<URI, List<MethodSymbol>> indexedByUri = new ConcurrentHashMap<>();

  /**
   * Типы возвращаемого значения, выведенные по телу метода. Пишет их
   * {@code MethodReturnTypeIndexer}; сюда они складываются, чтобы ответ на вопрос
   * «что возвращает метод» был один и тот же у всех потребителей.
   */
  private final Map<MethodSymbol, TypeSet> inferredReturnTypes = new ConcurrentHashMap<>();

  /**
   * Методы документа, у которых есть выведенное значение, — по ним запись стирается вместе
   * с документом. Набор, а не список: значение метода пересчитывается многократно (разбор,
   * доразрешение, разнос по потребителям), и список копил бы один и тот же метод при каждом
   * пересчёте.
   */
  private final Map<URI, Set<MethodSymbol>> inferredByUri = new ConcurrentHashMap<>();

  // Раньше MethodReturnTypeIndexer (@Order 300): он кладёт сюда выведенные по телу типы,
  // а здесь записи документа сначала стираются. Без явного порядка слушатель без
  // аннотации идёт последним и стёр бы только что записанное.
  @Order(200)
  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    if (!event.isContentChanged()) {
      // Тот же самый текст перечитан заново: записи по нему остаются верными. Символы из
      // построенного заново дерева равны прежним (имя, документ, позиция имени), поэтому
      // находятся по старым записям. Снести и собрать их заново означало бы оставить окно,
      // в котором чужой поток не находит типов уже посчитанного метода.
      return;
    }
    var documentContext = event.getSource();
    clear(documentContext.getUri());
    reindexDeclared(documentContext);
  }

  // Порядок раньше MethodReturnTypeIndexer (у него порядка нет, то есть он последний):
  // расчёт типов по телу читает объявленные типы вызванных методов, поэтому пересобрать
  // их надо до него.
  @Order(100)
  @EventListener
  public void handleServerContextPopulated(ServerContextPopulatedEvent event) {
    // Ссылка `см.` разворачивается в момент разбора документа, когда часть модулей рабочей
    // области ещё не зарегистрирована: тогда она никуда не ведёт, и объявленный тип выходит
    // беднее написанного. Теперь область наполнена — разворачиваем заново.
    event.getSource().getDocuments().values().forEach(this::reindexDeclared);
  }

  /**
   * Пересобирает объявленные типы возвращаемых значений методов документа, стирая прежние.
   * Выведенные по телу значения не трогает.
   * <p>
   * Работает по дереву символов, поэтому разбор документа для этого не нужен.
   *
   * @param documentContext документ.
   */
  public void reindexDeclared(DocumentContext documentContext) {
    var uri = documentContext.getUri();

    var previous = indexedByUri.remove(uri);
    if (previous != null) {
      previous.forEach(declaredReturnTypes::remove);
    }

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
   * Типы возвращаемого значения метода: объявленные в документирующем комментарии вместе
   * с рассчитанными по телу.
   * <p>
   * По общим типам верим описанию: у {@code Массив из Число} из комментария состав
   * элементов точнее, чем у того же {@code Массив}, собранного по телу.
   *
   * @param method метод.
   * @return типы возвращаемого значения; {@link TypeSet#EMPTY}, если ни один источник
   *     ничего не дал.
   */
  public TypeSet getReturnTypes(MethodSymbol method) {
    var declared = getDeclaredReturnTypes(method);
    var inferred = inferredReturnTypes.get(method);
    if (inferred == null || inferred.isEmpty()) {
      return declared;
    }
    var extra = inferred;
    for (var ref : declared.refs()) {
      extra = extra.without(ref);
    }
    return declared.union(extra);
  }

  /**
   * Типы возвращаемого значения, выведенные по телу метода, без объявленных в описании.
   *
   * @param method метод.
   * @return выведенные типы; {@link TypeSet#EMPTY}, если по телу ничего не выведено.
   */
  public TypeSet getInferredReturnTypes(MethodSymbol method) {
    return inferredReturnTypes.getOrDefault(method, TypeSet.EMPTY);
  }

  /**
   * Забыть все выведенные по телам значения, оставив объявленные в описаниях.
   */
  public void clearInferredReturnTypes() {
    inferredReturnTypes.clear();
    inferredByUri.clear();
  }

  /**
   * Запомнить типы возвращаемого значения, выведенные по телу метода.
   *
   * @param method метод.
   * @param types  выведенные типы; пустой набор стирает прежнюю запись.
   */
  public void putReturnTypes(MethodSymbol method, TypeSet types) {
    // Обе карты меняются под одним замком — по ключу документа. Иначе расчёт по запросу,
    // идущий вне событий жизненного цикла, мог бы вклиниться в сброс между снятием набора
    // и обходом, и значение осталось бы в карте типов без ссылки из карты по документам —
    // то есть недостижимым для следующего сброса.
    inferredByUri.compute(method.getOwner().getUri(), (uri, methods) -> {
      if (types.isEmpty()) {
        inferredReturnTypes.remove(method);
        if (methods == null) {
          return null;
        }
        methods.remove(method);
        return methods.isEmpty() ? null : methods;
      }
      inferredReturnTypes.put(method, types);
      var target = methods == null ? ConcurrentHashMap.<MethodSymbol>newKeySet() : methods;
      target.add(method);
      return target;
    });
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
   * в тип. Проход по цепочке членов ({@link #resolveChain}) от самого длинного
   * префикса к короткому; берётся тип возврата последнего члена (или типы
   * параметра для записи {@code …Метод.Параметр}).
   *
   * @return {@link TypeSet} c одним элементом или {@link TypeSet#EMPTY}.
   */
  public TypeSet resolveHyperlink(String link, FileType fileType) {
    if (link == null || link.isBlank()) {
      return TypeSet.EMPTY;
    }
    var parts = link.split("\\.");
    for (int prefixLen = parts.length - 1; prefixLen >= 1; prefixLen--) {
      var chain = resolveChain(parts, prefixLen, fileType);
      if (chain == null) {
        continue;
      }
      if (chain.member() == null) {
        return chain.parameterTypes();
      }
      var returnType = chain.member().returnType();
      if (returnType.kind() != TypeKind.UNKNOWN) {
        return TypeSet.of(returnType);
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Разрешить квалифицированную {@code см.}-ссылку ({@code Модуль.Метод},
   * {@code Справочники.X.Метод} и т.п.) в символ-определение цели.
   * <p>
   * Это тот же проход по цепочке членов, что и в {@link #resolveHyperlink}
   * ({@link #resolveChain}), но у найденного члена берётся не тип, а
   * символ-источник ({@link MemberDescriptor#getSourceSymbol()}) — единообразно
   * для общих модулей, модулей менеджеров и прочих типов.
   *
   * @return символ-определение цели ссылки, либо {@link Optional#empty()}, если
   *         ссылка не разрешается или у члена нет source-defined источника.
   */
  public Optional<SourceDefinedSymbol> resolveReferenceSymbol(String link, FileType fileType) {
    if (link.isBlank()) {
      return Optional.empty();
    }
    var parts = link.split("\\.");
    if (parts.length < MIN_QUALIFIED_SEGMENTS) {
      return Optional.empty();
    }
    for (int prefixLen = parts.length - 1; prefixLen >= 1; prefixLen--) {
      var chain = resolveChain(parts, prefixLen, fileType);
      if (chain != null && chain.member() != null
        && chain.member().getSourceSymbol().orElse(null) instanceof SourceDefinedSymbol target) {
        return Optional.of(target);
      }
    }
    return Optional.empty();
  }

  /**
   * Результат прохода по цепочке членов ссылки: разрешённый последний
   * {@code member}, либо (для записи {@code Модуль.Метод.Параметр}) типы параметра
   * при {@code member == null}.
   */
  private record MemberChain(@Nullable MemberDescriptor member, TypeSet parameterTypes) {
  }

  /**
   * Резолвит голову длиной {@code prefixLen} через {@link TypeRegistry} и проходит
   * по оставшимся сегментам как по членам. Тип возврата члена используется лишь
   * чтобы спуститься к следующему сегменту; тип/символ последнего сегмента
   * извлекает вызывающий.
   *
   * @return цепочка членов, либо {@code null}, если голова или какой-то сегмент
   *         не резолвятся (вызывающий пробует более короткий префикс).
   */
  @Nullable
  private MemberChain resolveChain(String[] parts, int prefixLen, FileType fileType) {
    var head = String.join(".", List.of(parts).subList(0, prefixLen));
    var current = typeRegistry.resolve(head, fileType).orElse(null);
    if (current == null) {
      return null;
    }
    var lastIndex = parts.length - 1;
    MemberDescriptor lastMethod = null;
    for (int i = prefixLen; i < parts.length; i++) {
      var member = findMember(current, parts[i], fileType);
      if (member == null) {
        // Модуль.Метод.Параметр: последний сегмент — имя параметра пред. метода.
        return i == lastIndex ? parameterChain(lastMethod, parts[i]) : null;
      }
      if (i == lastIndex) {
        return new MemberChain(member, TypeSet.EMPTY);
      }
      var next = member.returnType();
      lastMethod = member.kind() == MemberKind.METHOD ? member : null;
      if (next.kind() == TypeKind.UNKNOWN) {
        // Спускаться в неизвестный тип возврата некуда (у процедур и
        // недокументированных функций он всегда UNKNOWN), но следующий и
        // последний сегмент ещё может быть именем параметра этого метода.
        return i == lastIndex - 1 ? parameterChain(lastMethod, parts[i + 1]) : null;
      }
      current = next;
    }
    return null;
  }

  /**
   * Цепочка для записи {@code …Метод.Параметр}: типы параметра {@code parameterName}
   * метода {@code method}. {@code null}, если метод не задан (предыдущий сегмент — не
   * метод) или у него нет такого параметра.
   */
  @Nullable
  private static MemberChain parameterChain(@Nullable MemberDescriptor method, String parameterName) {
    if (method == null) {
      return null;
    }
    var parameterTypes = parameterFromMember(method, parameterName);
    return parameterTypes != null && !parameterTypes.isEmpty()
      ? new MemberChain(null, parameterTypes)
      : null;
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
    // Снятие набора и удаление значений — под тем же замком по ключу документа, что и запись
    // (см. putReturnTypes): иначе параллельная запись осталась бы в карте типов навсегда.
    inferredByUri.compute(uri, (key, methods) -> {
      if (methods != null) {
        methods.forEach(inferredReturnTypes::remove);
      }
      return null;
    });
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
   *   <li>путь в нотации конфигуратора ({@code Справочник.Товары.ЕдиницыИзмерения}
   *       и его продолжение по членам) — через {@link #resolveMetadataPath};</li>
   *   <li>полное имя формы ({@code Справочник.Товары.Форма.ФормаЭлемента}) — через
   *       {@link FormByNameResolver};</li>
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
   * переменных ({@code CommentTypeResolver}).
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
      var qualifiedTypes = resolveQualifiedLink(link, owner, fileType);
      if (!qualifiedTypes.isEmpty()) {
        return qualifiedTypes;
      }
      // Не разрешилось как ссылка на член (Модуль.Метод / Тип.Член), путь метаданных
      // или имя формы — пробуем трактовать как полное имя типа (например,
      // квалифицированный платформенный тип) через TypeRegistry ниже.
    }
    var localFunction = findLocalFunction(owner, link);
    if (localFunction != null) {
      return resolveLocalFunctionTypes(localFunction, owner, fileType, visited);
    }
    return typeRegistry.resolve(link, fileType).map(TypeSet::of).orElse(TypeSet.EMPTY);
  }

  /**
   * Тип по ссылке с точкой: сначала как цепочка членов ({@code Модуль.Метод},
   * {@code Тип.Член}), затем как путь в нотации конфигуратора, затем как имя формы
   * с путём внутрь неё.
   * <p>
   * Виды перебираются по очереди: какой из них перед нами, из самой строки не видно —
   * все три записываются одинаково, через точку.
   *
   * @param link     ссылка целиком.
   * @param owner    документ-владелец — нужен резолверу форм.
   * @param fileType язык, на котором резолвятся имена.
   * @return тип по ссылке; {@link TypeSet#EMPTY}, если ни один вид не подошёл.
   */
  private TypeSet resolveQualifiedLink(String link, DocumentContext owner, FileType fileType) {
    var hyperlinkTypes = resolveHyperlink(link, fileType);
    if (!hyperlinkTypes.isEmpty()) {
      return hyperlinkTypes;
    }
    var metadataTypes = resolveMetadataPath(link, fileType);
    if (!metadataTypes.isEmpty()) {
      return metadataTypes;
    }
    return resolveFormPath(link, owner, fileType);
  }

  /**
   * Тип по ссылке на форму: {@code Справочник.Товары.Форма.ФормаЭлемента} — сама форма,
   * {@code …ФормаЭлемента.Объект} — её реквизит, {@code …ФормаСписка.Элементы.Список} —
   * её элемент.
   * <p>
   * Форму называют полным именем, а её синтетический тип зарегистрирован с приставкой
   * базового типа формы — сопоставляет одно с другим тот же резолвер, что типизирует
   * {@code ПолучитьФорму(<полное имя>)}. Где кончается имя формы и начинается путь внутри
   * неё, из самой строки не видно, поэтому имя примеряется от самого длинного к короткому,
   * а остаток читается как цепочка членов ({@link #walkMembers}).
   *
   * @param link     ссылка целиком.
   * @param owner    документ-владелец — резолвер основных форм смотрит на его метаданные.
   * @param fileType язык, на котором резолвятся имена членов.
   * @return тип по ссылке; {@link TypeSet#EMPTY}, если формы с таким именем нет.
   */
  private TypeSet resolveFormPath(String link, DocumentContext owner, FileType fileType) {
    var parts = link.split("\\.", -1);
    for (var nameLength = parts.length; nameLength >= 1; nameLength--) {
      var formName = String.join(".", List.of(parts).subList(0, nameLength));
      var formType = formByNameResolver.resolve(owner, formName).orElse(null);
      if (formType == null) {
        continue;
      }
      return nameLength == parts.length
        ? TypeSet.of(formType)
        : walkMembers(formType, parts, nameLength, fileType);
    }
    return TypeSet.EMPTY;
  }

  /**
   * Тип по ссылке в нотации конфигуратора: {@code Справочник.Товары.ЕдиницыИзмерения} —
   * табличная часть, {@code Справочник.Товары.ЕдиницыИзмерения.Единица} — её реквизит,
   * {@code Справочник.Товары.Артикул} — реквизит самого объекта.
   * <p>
   * Имена таких типов реестр складывает из того же вида объекта метаданных, что стоит
   * в начале ссылки, поэтому путь собирается прямо из её частей. Части, оставшиеся
   * после объекта метаданных, читаются как цепочка членов ({@link #walkMembers}).
   *
   * @param link     ссылка целиком.
   * @param fileType язык, на котором резолвятся имена.
   * @return тип по ссылке; {@link TypeSet#EMPTY}, если такого пути в метаданных нет.
   */
  private TypeSet resolveMetadataPath(String link, FileType fileType) {
    // Пустые части сохраняются (-1): «Справочник.Товары.ЕдиницыИзмерения.» — не ссылка
    // на саму табличную часть, а ссылка на её реквизит с пустым именем.
    var parts = link.split("\\.", -1);
    if (parts.length < MIN_METADATA_SEGMENTS) {
      return TypeSet.EMPTY;
    }
    var kind = parts[KIND_PART];
    var mdName = parts[NAME_PART];
    var childName = parts[CHILD_PART];

    var section = typeRegistry.resolve(kind + TABULAR_SECTION + mdName + "." + childName, fileType);
    if (section.isPresent()) {
      if (parts.length == MIN_METADATA_SEGMENTS) {
        return TypeSet.of(section.get());
      }
      // Реквизиты есть у строки табличной части, а не у неё самой.
      return typeRegistry.resolve(kind + TABULAR_SECTION_ROW + mdName + "." + childName, fileType)
        .map(rowRef -> walkMembers(rowRef, parts, ATTRIBUTE_PART, fileType))
        .orElse(TypeSet.EMPTY);
    }
    return typeRegistry.resolve(kind + OBJECT + mdName, fileType)
      .map(objectRef -> walkMembers(objectRef, parts, CHILD_PART, fileType))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Проход по цепочке членов: каждая следующая часть ссылки берётся как член типа,
   * полученного на предыдущей.
   * <p>
   * Цепочка обрывается, если члена с таким именем нет либо предыдущая часть дала
   * больше одного типа — продолжать неоднозначный путь не от чего.
   *
   * @param ref      тип, от которого идёт проход.
   * @param parts    части ссылки.
   * @param from     номер части, с которой начинается проход.
   * @param fileType язык, на котором ищутся члены.
   * @return типы последней части; {@link TypeSet#EMPTY}, если цепочка оборвалась.
   */
  private TypeSet walkMembers(TypeRef ref, String[] parts, int from, FileType fileType) {
    var current = memberTypes(ref, parts[from], fileType);
    for (var i = from + 1; i < parts.length && !current.isEmpty(); i++) {
      var refs = current.refs();
      if (refs.size() != 1) {
        return TypeSet.EMPTY;
      }
      current = memberTypes(refs.iterator().next(), parts[i], fileType);
    }
    return current;
  }

  /**
   * Типы члена по имени.
   *
   * @param ref      тип-владелец.
   * @param name     имя члена.
   * @param fileType язык, на котором ищется член.
   * @return типы члена; {@link TypeSet#EMPTY}, если члена с таким именем нет.
   */
  private TypeSet memberTypes(TypeRef ref, String name, FileType fileType) {
    return typeRegistry.findMember(ref, MemberKind.PROPERTY, name, fileType)
      .map(MemberDescriptor::returnTypes)
      .orElse(TypeSet.EMPTY);
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
        // У коллекционной записи автор перечислил элементы сам — даже если они не
        // разрешились, подставлять вместо них умолчание реестра нельзя.
        resolved = attachDefaultElementTypes(resolved);
      }
      acc = acc.union(resolved);
    }
    return acc;
  }

  /**
   * Прикрепить к типам набора элементы-по-умолчанию из реестра — те, что известны
   * самому типу ({@code КлючИЗначение} у соответствия, строка у табличной части).
   * <p>
   * Делается на выходе разбора объявления, а не у каждого потребителя: объявленный
   * тип уходит и в переменную, и в возврат метода, и в параметр, и обход коллекции
   * должен видеть тип элемента везде одинаково. Уточнение, записанное в самом
   * объявлении ({@code Массив из Строка}), не перетирается — оно точнее.
   *
   * @param types набор объявленных типов.
   * @return тот же набор с элементами-по-умолчанию там, где своих не объявлено.
   */
  private TypeSet attachDefaultElementTypes(TypeSet types) {
    var result = types;
    for (var ref : types.refs()) {
      // Проверяется наличие объявленной записи, а не её значение: у ленивой ссылки
      // значение брать рано — она разрешается в момент чтения, а не индексации.
      if (!types.elementTypes().getOrDefault(ref, TypeSet.EMPTY).isEmpty()
        || types.lazyElements().containsKey(ref)) {
        continue;
      }
      var defaults = typeRegistry.getDefaultElementTypes(ref);
      if (!defaults.isEmpty()) {
        result = result.withElement(ref, defaults);
      }
    }
    return result;
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
      case SIMPLE -> resolveSimple(td, context);
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
    // У коллекции описанные звёздочками имена — это свойства её элемента, а не её самой:
    // обращений вида «Таблица.Цена» или «Соответствие.Ключ» в 1С нет. Собственные
    // свойства бывают только у структуроподобных типов — им поля и остаются.
    var elementRef = collectionElement(headRef, context.fileType());
    var fieldsRef = elementRef == null ? headRef : elementRef;
    var result = elementRef == null ? base : TypeSet.of(elementRef);
    for (var field : fields) {
      var eager = TypeSet.EMPTY;
      for (var fieldType : field.types()) {
        var localFunction = localFunctionSeeRef(fieldType, context);
        if (localFunction != null) {
          result = result.withLazyField(fieldsRef, field.name(),
            lazyReturnTypes(localFunction), fieldDescription(field));
        } else {
          eager = eager.union(resolveTypes(List.of(fieldType), context));
        }
      }
      if (!eager.isEmpty()) {
        result = result.withField(fieldsRef, field.name(), eager, fieldDescription(field));
      }
    }
    return elementRef == null ? result : base.withElement(headRef, result);
  }

  /**
   * Элемент коллекции — строка таблицы или дерева, элемент табличной части или
   * коллекции формы, пара «ключ и значение» соответствия.
   *
   * @param ref      тип, к которому относится описание полей.
   * @param fileType язык, на котором ищется член-коллекция строк.
   * @return тип элемента; {@code null} у типов, где описанные поля принадлежат самому
   *     типу, и у тех, элемент которых реестру неизвестен.
   */
  private @Nullable TypeRef collectionElement(TypeRef ref, FileType fileType) {
    if (!OpenDataObjectInference.isElementBearingCollection(ref.qualifiedName())) {
      return null;
    }
    var direct = firstRef(typeRegistry.getDefaultElementTypes(ref));
    if (direct != null) {
      return direct;
    }
    // Дерево значений само не обходится: его строки лежат в отдельной коллекции «Строки»,
    // и колонки описания принадлежат строке именно этой коллекции.
    return typeRegistry.findMember(ref, MemberKind.PROPERTY, ROWS, fileType)
      .map(MemberDescriptor::returnTypes)
      .map(SymbolTypeIndex::firstRef)
      .map(typeRegistry::getDefaultElementTypes)
      .map(SymbolTypeIndex::firstRef)
      .orElse(null);
  }

  private static @Nullable TypeRef firstRef(TypeSet types) {
    return types.refs().stream().findFirst().orElse(null);
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

  /**
   * Простое имя типа из описания. За именем определяемого типа стоит набор, поэтому
   * имя сперва спрашивается у реестра как набор; всё прочее — один тип, а незнакомое
   * имя остаётся пользовательским типом, как и было.
   */
  /**
   * Простой тип, возможно уточнённый ссылкой: {@code СтрокаТабличнойЧасти: См. Справочник.Товары.ЕдиницыИзмерения}.
   * <p>
   * Голова такой записи говорит, чем значение является, а ссылка указывает на коллекцию,
   * элементом которой оно служит: строку табличной части, элемент коллекции формы. Поэтому
   * у коллекции берётся её элемент — вместе с колонками, которые у него уже есть. Ссылка на
   * тип, коллекцией не являющийся, отдаётся как есть.
   *
   * @param td      описание типа.
   * @param context контекст разрешения.
   * @return тип; {@link TypeSet#EMPTY}, если не разрешился ни ссылкой, ни именем.
   */
  private TypeSet resolveSimple(TypeDescription td, ResolutionContext context) {
    var hyperlink = td.hyperlink();
    if (hyperlink == null) {
      return resolveSimple(td.name());
    }
    var linked = resolveSeeReference(hyperlink.link(), context.owner(), context.fileType(), context.visited());
    if (linked.isEmpty()) {
      return resolveSimple(td.name());
    }
    var element = elementOf(linked, context.fileType());
    return element.isEmpty() ? linked : element;
  }

  /**
   * Элемент коллекции: уточнённый по месту, иначе элемент из реестра, иначе тип
   * свойства {@code ТекущиеДанные}.
   * <p>
   * Третий источник нужен таблицам формы: элементов у них не заведено, а тип строки
   * объявлен свойством {@code ТекущиеДанные} — на него и опирается запись
   * «строка: {@code См.} таблица формы».
   *
   * @param types    типы коллекции.
   * @param fileType язык, на котором ищется член.
   * @return типы элемента; {@link TypeSet#EMPTY}, если ни одного из источников нет.
   */
  private TypeSet elementOf(TypeSet types, FileType fileType) {
    var result = TypeSet.EMPTY;
    for (var ref : types.refs()) {
      var attached = types.getElementTypes(ref);
      if (!attached.isEmpty()) {
        result = result.union(attached);
        continue;
      }
      var defaults = typeRegistry.getDefaultElementTypes(ref);
      result = result.union(defaults.isEmpty() ? currentDataOf(ref, fileType) : defaults);
    }
    return result;
  }

  /**
   * Типы свойства {@code ТекущиеДанные} у указанного типа — строки, которую отдаёт
   * таблица формы.
   *
   * @param ref      тип-владелец свойства.
   * @param fileType язык, на котором ищется член.
   * @return типы свойства; {@link TypeSet#EMPTY}, если такого свойства у типа нет.
   */
  private TypeSet currentDataOf(TypeRef ref, FileType fileType) {
    return typeRegistry.findMember(ref, MemberKind.PROPERTY, CURRENT_DATA, fileType)
      .map(MemberDescriptor::returnTypes)
      .orElse(TypeSet.EMPTY);
  }

  private TypeSet resolveSimple(String name) {
    if (name.isBlank()) {
      return TypeSet.EMPTY;
    }
    var byName = typeRegistry.resolveSet(name.trim());
    return byName.isEmpty()
      ? resolveOne(name).map(TypeSet::of).orElse(TypeSet.EMPTY)
      : byName;
  }
}
