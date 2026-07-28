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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.LocationRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.StringInterner;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Индекс ссылок на символы в проекте.
 * <p>
 * Управляет индексацией и поиском ссылок на символы (методы, переменные)
 * во всех документах workspace.
 */
@Component
@RequiredArgsConstructor
public class ReferenceIndex {

  /**
   * Маркер {@code scopeName} для вхождений неквалифицированных self-членов
   * (реквизитов/платформенных методов self-типа модуля). По нему
   * {@link #buildReference} реконструирует {@code PlatformMemberSymbol} через
   * {@link SelfMemberResolver}, а не ищет source-defined символ в дереве.
   * Не пересекается с реальными именами методов (у source-символов scopeName — имя
   * охватывающего метода либо пусто).
   */
  public static final String SELF_MEMBER_SCOPE = "$self";

  private final ServerContextProvider serverContextProvider;
  private final StringInterner stringInterner;
  private final LocationRepository locationRepository;
  private final SymbolOccurrenceRepository symbolOccurrenceRepository;
  private final SelfMemberResolver selfMemberResolver;

  /**
   * Получить ссылки на символ.
   *
   * @param symbol Символ, для которого необходимо осуществить поиск ссылок.
   * @return Список ссылок на символ.
   */
  public List<Reference> getReferencesTo(SourceDefinedSymbol symbol) {
    var mdoRef = symbol.getOwner().getMdoRef();
    var moduleType = symbol.getOwner().getModuleType();
    var symbolName = symbol.getName().toLowerCase(Locale.ENGLISH);
    var scopeName = "";

    if (symbol.getSymbolKind() == SymbolKind.Variable) {
      scopeName = symbol.getRootParent(MethodSymbol.class)
        .map(SourceDefinedSymbol::getName)
        .map(name -> name.toLowerCase(Locale.ENGLISH))
        .orElse("");
    }

    var symbolDto = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName(scopeName)
      .symbolKind(indexedKindOf(symbol.getSymbolKind()))
      .symbolName(symbolName)
      .build();

    return symbolOccurrenceRepository.getAllBySymbol(symbolDto)
      .stream()
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск символа по позиции курсора.
   *
   * @param uri      URI документа, в котором необходимо осуществить поиск.
   * @param position позиция курсора.
   * @return данные ссылки.
   */
  public Optional<Reference> getReference(URI uri, Position position) {
    return locationRepository.findByPosition(uri, position).flatMap(this::buildReference);
  }

  /**
   * Поиск ссылок на символы в документе.
   *
   * @param uri URI документа, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(URI uri) {
    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск ссылок на символы в документе.
   *
   * @param uri URI документа, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(URI uri, SymbolKind kind) {
    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .filter(s -> s.symbol().symbolKind() == kind)
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .collect(Collectors.toList());
  }

  /**
   * Поиск ссылок на символы в символе.
   *
   * @param symbol Символ, в котором нужно найти ссылки на другие символы.
   * @return Список ссылок на символы.
   */
  public List<Reference> getReferencesFrom(SourceDefinedSymbol symbol) {
    return locationRepository.getSymbolOccurrencesByLocationUri(symbol.getOwner().getUri())
      .map(this::buildReference)
      .flatMap(Optional::stream)
      .filter(reference -> reference.from().equals(symbol))
      .collect(Collectors.toList());
  }

  /**
   * Очистить ссылки из/на текущий документ.
   *
   * @param uri URI документа.
   */
  public void clearReferences(URI uri) {
    var symbolOccurrences = locationRepository.getSymbolOccurrencesByLocationUri(uri);
    symbolOccurrenceRepository.deleteAll(symbolOccurrences.collect(Collectors.toSet()));
    locationRepository.delete(uri);
  }

  /**
   * Атомарно заменить все обращения к символам, расположенные в документе, на новый набор.
   * <p>
   * В отличие от пары {@code clearReferences + addXxx} не оставляет окна, в котором
   * индекс документа пуст: сначала добавляются недостающие вхождения, затем удаляются
   * устаревшие (разность старого и нового наборов). Конкурентные читатели в любой момент
   * видят как минимум пересечение старого и нового наборов — «пропажа» ссылок на
   * существующие символы (и, как следствие, ложные срабатывания диагностик
   * неиспользуемых переменных/методов) исключена.
   *
   * @param uri            URI документа, чьи вхождения заменяются.
   * @param newOccurrences Новый набор вхождений (дубликаты допускаются:
   *                       повторная запись вхождения — идемпотентный no-op).
   */
  public void replaceReferences(URI uri, Iterable<SymbolOccurrence> newOccurrences) {
    var stale = locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .collect(Collectors.toCollection(HashSet::new));
    var replacement = new LinkedHashSet<SymbolOccurrence>();
    var newBySymbol = new HashMap<Symbol, List<SymbolOccurrence>>();
    for (var occurrence : newOccurrences) {
      if (!replacement.add(occurrence)) {
        continue; // дубликат в пачке документа — обрабатываем ровно один раз
      }
      // Успешное удаление из stale означает, что вхождение уже есть в индексе —
      // недостающие группируем по символу и добавляем пачкой (одно копирование
      // массива символа на документ вместо копирования на каждое обращение).
      if (!stale.remove(occurrence)) {
        newBySymbol.computeIfAbsent(occurrence.symbol(), symbol -> new ArrayList<>()).add(occurrence);
      }
    }
    newBySymbol.forEach(symbolOccurrenceRepository::saveAll);
    if (!stale.isEmpty()) {
      symbolOccurrenceRepository.deleteAll(stale);
    }
    // Сторона расположений заменяется целиком одним атомарным swap-ом полного
    // набора вхождений документа — плотнее (массив вместо concurrent-множества)
    // и без «пустого окна» для читателей.
    locationRepository.replaceOccurrences(uri, replacement);
  }

  /**
   * Канонический вид callable-символа для построения/поиска ключа индекса вхождений
   * ({@code Symbol.symbolKind}), в отличие от display-{@link SymbolKind}, возвращаемого
   * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol#getSymbolKind()}.
   * <p>
   * На месте вызова (например, {@code Новый ИмяКласса(...)}) неизвестно без резолва
   * документа-получателя, является ли цель обычным методом или конструктором OneScript-класса
   * ({@link com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol}) —
   * поэтому все callable-вхождения индексируются {@link #methodCallOccurrence} под единым
   * {@link SymbolKind#Method} независимо от истинного вида цели (см. и
   * {@code ReferenceIndexFiller#tryRegisterLibraryClassReference}, который знает, что вызывает
   * именно конструктор, но всё равно идёт тем же общим путём индексации). Читающая сторона
   * ({@link #getReferencesTo}) обязана канонизировать так же — иначе ключ, построенный по
   * настоящему {@code getSymbolKind()} символа-цели ({@code Constructor}), никогда не совпадёт с
   * реально сохранённым ({@code Method}), и Find References/Rename/Call Hierarchy incoming
   * молча не находят ни одной ссылки на конструктор.
   * <p>
   * Так же канонизируется {@link SymbolKind#Event}: объявленные обработчики платформенных
   * событий попадают в дерево символов как {@code EventMethodSymbol} (display-вид {@code Event}),
   * но их прямые вызовы индексируются {@link #methodCallOccurrence} под {@code Method} — без
   * канонизации Find References/Call Hierarchy не нашли бы ни одного прямого вызова обработчика.
   * И так же — {@link SymbolKind#Function}: методы модулей без состояния (общих модулей BSL и
   * модулей OneScript) отдаются в дереве символов как {@code Function}
   * (см. {@code RegularMethodSymbol#getSymbolKind()}), но их вызовы индексируются под {@code Method}.
   * Точка расширения при появлении новых callable-{@link SymbolKind} — сюда достаточно добавить
   * очередной случай.
   *
   * @param symbolKind реальный (display) вид искомого/индексируемого символа.
   * @return канонический вид для ключа индекса.
   */
  private static SymbolKind indexedKindOf(SymbolKind symbolKind) {
    if (symbolKind == SymbolKind.Constructor
      || symbolKind == SymbolKind.Event
      || symbolKind == SymbolKind.Function) {
      return SymbolKind.Method;
    }
    return symbolKind;
  }

  /**
   * Построить вхождение «вызов метода» без записи в индекс.
   * <p>
   * Кладётся под каноническим {@link SymbolKind#Method} независимо от истинного вида цели
   * (обычный метод или конструктор OneScript-класса) — см. {@link #indexedKindOf}.
   *
   * @param uri        URI документа, откуда произошел вызов.
   * @param mdoRef     Ссылка на объект-метаданных, к которому происходит обращение (например, CommonModule.ОбщийМодуль1).
   * @param moduleType Тип модуля, к которому происходит обращение (например, {@link ModuleType#CommonModule}).
   * @param symbolName Имя символа, к которому происходит обращение.
   * @param range      Диапазон, в котором происходит обращение к символу.
   * @return построенное вхождение.
   */
  protected SymbolOccurrence methodCallOccurrence(URI uri, String mdoRef, ModuleType moduleType,
                                                  String symbolName, Range range) {
    var symbolNameCanonical = stringInterner.intern(symbolName.toLowerCase(Locale.ENGLISH));

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName("")
      .symbolKind(SymbolKind.Method)
      .symbolName(symbolNameCanonical)
      .build()
      .intern();

    return SymbolOccurrence.of(OccurrenceType.REFERENCE, symbol, uri, range);
  }

  /**
   * Построить вхождение «ссылка на модуль» без записи в индекс.
   * <p>
   * Имя символа вычисляется детерминированно из {@code mdoRef} и {@code moduleType}
   * ({@link ModuleSymbol#nameOf}) и совпадает с {@link ModuleSymbol#getName()}, поэтому
   * запись и поиск ({@link #getReferencesTo}) симметричны без какой-либо спецобработки.
   *
   * @param uri        URI документа, откуда произошло обращение к модулю.
   * @param mdoRef     Ссылка на объект-метаданных модуля (например, CommonModule.ОбщийМодуль1).
   * @param moduleType Тип модуля (например, {@link ModuleType#CommonModule}).
   * @param range      Диапазон, в котором происходит обращение к модулю.
   * @return построенное вхождение.
   */
  protected SymbolOccurrence moduleReferenceOccurrence(URI uri, String mdoRef, ModuleType moduleType, Range range) {
    var symbolName = stringInterner.intern(
      ModuleSymbol.nameOf(mdoRef, moduleType).toLowerCase(Locale.ENGLISH)
    );

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName("")
      .symbolKind(SymbolKind.Module)
      .symbolName(symbolName)
      .build()
      .intern();

    return SymbolOccurrence.of(OccurrenceType.REFERENCE, symbol, uri, range);
  }

  /**
   * Построить вхождение «обращение к переменной» без записи в индекс.
   * Вид вхождения ({@link OccurrenceType}) задаётся явно.
   *
   * @param uri            URI документа, откуда произошел вызов.
   * @param mdoRef         Ссылка на объект-метаданных, к которому происходит обращение (например, CommonModule.ОбщийМодуль1).
   * @param moduleType     Тип модуля, к которому происходит обращение (например, {@link ModuleType#CommonModule}).
   * @param methodName     Имя метода, к которому относится переменная. Пустой, если переменная относится к модулю.
   * @param variableName   Имя переменной, к которой происходит обращение.
   * @param range          Диапазон, в котором происходит обращение к символу.
   * @param occurrenceType Вид обращения к символу.
   * @return построенное вхождение.
   */
  protected SymbolOccurrence variableOccurrence(URI uri,
                                                String mdoRef,
                                                ModuleType moduleType,
                                                String methodName,
                                                String variableName,
                                                Range range,
                                                OccurrenceType occurrenceType) {
    var methodNameCanonical = stringInterner.intern(methodName.toLowerCase(Locale.ENGLISH));
    var variableNameCanonical = stringInterner.intern(variableName.toLowerCase(Locale.ENGLISH));

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName(methodNameCanonical)
      .symbolKind(SymbolKind.Variable)
      .symbolName(variableNameCanonical)
      .build()
      .intern();

    return SymbolOccurrence.of(occurrenceType, symbol, uri, range);
  }

  /**
   * Построить вхождение «обращение к неквалифицированному self-члену» (реквизиту/
   * платформенному методу self-типа модуля) без записи в индекс. Ключ помечается
   * {@link #SELF_MEMBER_SCOPE}, по которому {@link #buildReference} реконструирует
   * {@code PlatformMemberSymbol} через {@link SelfMemberResolver}.
   *
   * @param uri            URI документа, где встречен self-член.
   * @param mdoRef         mdoRef документа (self-тип — это его собственный тип модуля).
   * @param moduleType     тип модуля документа.
   * @param symbolKind     вид: {@link SymbolKind#Method} (вызов) / {@link SymbolKind#Property} (ссылка).
   * @param name           имя self-члена.
   * @param range          диапазон обращения.
   * @param occurrenceType вид обращения.
   * @return построенное вхождение.
   */
  protected SymbolOccurrence selfMemberOccurrence(URI uri, String mdoRef, ModuleType moduleType,
                                                  SymbolKind symbolKind, String name, Range range,
                                                  OccurrenceType occurrenceType) {
    var nameCanonical = stringInterner.intern(name.toLowerCase(Locale.ENGLISH));

    var symbol = Symbol.builder()
      .mdoRef(mdoRef)
      .moduleType(moduleType)
      .scopeName(SELF_MEMBER_SCOPE)
      .symbolKind(symbolKind)
      .symbolName(nameCanonical)
      .build()
      .intern();

    return SymbolOccurrence.of(occurrenceType, symbol, uri, range);
  }

  private Optional<Reference> buildReference(
    SymbolOccurrence symbolOccurrence
  ) {

    var uri = symbolOccurrence.uri();

    var serverContextOpt = serverContextProvider.getServerContext(uri);
    if (serverContextOpt.isEmpty()) {
      return Optional.empty();
    }
    var serverContext = serverContextOpt.get();

    if (SELF_MEMBER_SCOPE.equals(symbolOccurrence.symbol().scopeName())) {
      return buildSelfMemberReference(serverContext, symbolOccurrence);
    }

    return getSourceDefinedSymbol(serverContext, symbolOccurrence.symbol())
      .map((SourceDefinedSymbol symbol) -> {
        var from = getFromSymbol(serverContext, symbolOccurrence);
        var range = symbolOccurrence.range();
        var occurrenceType = symbolOccurrence.occurrenceType();
        return new Reference(from, symbol, uri, range, occurrenceType);
      })
      .filter(ReferenceAccessibility::isAccessible);
  }

  /**
   * Реконструировать {@code Reference} для вхождения self-члена: цель — синтетический
   * {@code PlatformMemberSymbol}, собранный {@link SelfMemberResolver} по
   * self-типу документа и имени/виду члена. Если self-типа нет или член не найден
   * (конфигурационные типы ещё не зарегистрированы) — empty; корректная подсветка/
   * резолв восстановятся при переиндексации на {@code ConfigurationTypesRegisteredEvent}.
   */
  private Optional<Reference> buildSelfMemberReference(ServerContext serverContext, SymbolOccurrence occurrence) {
    var document = serverContext.getDocumentNoLock(occurrence.uri());
    if (document == null) {
      return Optional.empty();
    }
    return selfMemberResolver
      .resolveSelfMember(document, occurrence.symbol().symbolKind(), occurrence.symbol().symbolName())
      .map(memberSymbol -> new Reference(
        getFromSymbol(serverContext, occurrence),
        memberSymbol,
        occurrence.uri(),
        occurrence.range(),
        occurrence.occurrenceType()));
  }

  private static Optional<SourceDefinedSymbol> getSourceDefinedSymbol(ServerContext serverContext, Symbol symbolEntity) {
    var mdoRef = symbolEntity.mdoRef();
    var moduleType = symbolEntity.moduleType();
    var symbolName = symbolEntity.symbolName();

    var maybeDocument = serverContext.getDocument(mdoRef, moduleType);

    if (symbolEntity.symbolKind() == SymbolKind.Variable) {
      return maybeDocument
        .map(DocumentContext::getSymbolTree)
        .flatMap(symbolTree -> symbolTree.getMethodSymbol(symbolEntity.scopeName())
          .flatMap(method -> symbolTree.getVariableSymbol(symbolName, method))
          .or(() -> symbolTree.getVariableSymbol(symbolName, symbolTree.getModule())));
    }

    if (symbolEntity.symbolKind() == SymbolKind.Module) {
      return maybeDocument
        .map(DocumentContext::getSymbolTree)
        .map(SymbolTree::getModule);
    }

    return maybeDocument
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol(symbolName));
  }

  private static SourceDefinedSymbol getFromSymbol(ServerContext serverContext, SymbolOccurrence symbolOccurrence) {
    var uri = symbolOccurrence.uri();
    var position = symbolOccurrence.startPosition();

    return Optional.ofNullable(serverContext.getDocumentNoLock(uri))
      .map(DocumentContext::getSymbolTree)
      .map(symbolTree -> symbolTree.getSymbolAtPosition(position))
      .orElseThrow();
  }

}
