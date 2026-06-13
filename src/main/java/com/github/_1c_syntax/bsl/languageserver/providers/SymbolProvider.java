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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.databind.URITypeAdapter;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import com.google.gson.annotations.JsonAdapter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Провайдер для поиска символов в рабочей области.
 * <p>
 * Обрабатывает запросы {@code workspace/symbol}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_symbol">Workspace Symbols Request specification</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolProvider {

  private final ServerContextProvider serverContextProvider;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final JsonMapper jsonMapper;

  private static final Set<VariableKind> SUPPORTED_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  /**
   * Закэшированный признак поддержки клиентом дорезолвливания символов рабочей области
   * ({@code workspaceSymbol/resolve}). Вычисляется один раз при получении
   * {@link LanguageServerInitializeRequestReceivedEvent}, чтобы не читать
   * {@link ClientCapabilitiesHolder} на каждый запрос {@code workspace/symbol}.
   */
  private boolean resolveSupported;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Один раз определяет и кэширует признак поддержки клиентом дорезолвливания диапазона
   * символа: в момент события {@link ClientCapabilitiesHolder} уже содержит возможности клиента.
   *
   * @param event Событие получения запроса инициализации.
   */
  @EventListener
  public void handleInitializeEvent(LanguageServerInitializeRequestReceivedEvent event) {
    resolveSupported = clientCapabilitiesHolder.isWorkspaceSymbolResolveSupported();
  }

  /**
   * Выполняет поиск символов рабочей области по запросу {@code workspace/symbol} с поддержкой отмены.
   * <p>
   * Отмена проверяется на границе каждого документа: если клиент отменил запрос
   * (например, прислав новый запрос при следующем нажатии клавиши), обход прерывается
   * исключением {@link java.util.concurrent.CancellationException}.
   *
   * @param params        Параметры запроса {@code workspace/symbol}, в т.ч. строка запроса
   * @param cancelChecker Проверяющий отмену запроса; вызывается на границе каждого документа
   * @return Список найденных символов рабочей области
   */
  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params, CancelChecker cancelChecker) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    var pattern = compilePattern(queryString);

    // Search for symbols in all workspace contexts
    return serverContextProvider.getAllContexts().values().stream()
      .flatMap(serverContext -> serverContext.getDocuments().values().stream())
      .peek(documentContext -> cancelChecker.checkCanceled())
      .flatMap(SymbolProvider::getSymbolEntries)
      .filter(symbolEntry -> queryString.isEmpty() || pattern.matcher(symbolEntry.symbol().getName()).find())
      .map(this::createWorkspaceSymbol)
      .collect(Collectors.toList());
  }

  /**
   * Дорезолвливает символ рабочей области, достраивая точное местоположение
   * ({@link Location} с диапазоном) по запросу {@code workspaceSymbol/resolve} (LSP 3.17).
   * <p>
   * Облегчённый символ, отданный в {@link #getSymbols}, несёт местоположение в виде
   * {@link WorkspaceSymbolLocation} (только {@code uri}, без диапазона) и поле
   * {@link WorkspaceSymbol#getData()} с данными для восстановления. По этим данным
   * символ повторно отыскивается в дереве символов документа и его диапазон
   * подставляется в местоположение. Если символ уже несёт полное местоположение
   * или данные восстановить не удалось, символ возвращается без изменений.
   *
   * @param workspaceSymbol Символ рабочей области, подлежащий дорезолвливанию.
   * @return Символ с полным местоположением, либо исходный символ, если дорезолвить нечем.
   */
  public WorkspaceSymbol resolveSymbol(WorkspaceSymbol workspaceSymbol) {
    var data = extractData(workspaceSymbol.getData());
    if (data == null) {
      return workspaceSymbol;
    }

    findSymbolRange(data)
      .ifPresent(range -> workspaceSymbol.setLocation(Either.forLeft(new Location(data.uri().toString(), range))));
    workspaceSymbol.setData(null);

    return workspaceSymbol;
  }

  /**
   * Компилирует запрос {@code workspace/symbol} в шаблон для сопоставления имён символов.
   * <p>
   * Запрос трактуется как регулярное выражение. Если оно невалидно, спецификация LSP допускает
   * "расслабленную" обработку, поэтому вместо возврата пустого результата выполняется откат на
   * буквальное сопоставление подстроки.
   *
   * @param queryString строка запроса пользователя
   * @return скомпилированный шаблон с флагами {@code CASE_INSENSITIVE} и {@code UNICODE_CASE}
   */
  private static Pattern compilePattern(String queryString) {
    try {
      return CaseInsensitivePattern.compile(queryString);
    } catch (PatternSyntaxException e) {
      LOGGER.debug(e.getMessage(), e);
      return CaseInsensitivePattern.compile(Pattern.quote(queryString));
    }
  }

  private static Stream<SymbolEntry> getSymbolEntries(DocumentContext documentContext) {
    var scriptVariant = scriptVariantOf(documentContext);
    return documentContext.getSymbolTree().getChildrenFlat().stream()
      .filter(SymbolProvider::isSupported)
      .map(symbol -> new SymbolEntry(documentContext.getUri(), symbol, scriptVariant));
  }

  private static boolean isSupported(Symbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    return switch (symbolKind) {
      case Method, Constructor -> true;
      case Variable -> SUPPORTED_VARIABLE_KINDS.contains(((VariableSymbol) symbol).getKind());
      default -> false;
    };
  }

  private WorkspaceSymbol createWorkspaceSymbol(SymbolEntry symbolEntry) {
    var uri = symbolEntry.uri();
    var symbol = symbolEntry.symbol();
    var containerName = getContainerName(symbol, symbolEntry.scriptVariant());

    var workspaceSymbol = new WorkspaceSymbol();
    workspaceSymbol.setName(symbol.getName());
    workspaceSymbol.setKind(symbol.getSymbolKind());
    workspaceSymbol.setTags(symbol.getTags());
    containerName.ifPresent(workspaceSymbol::setContainerName);

    if (resolveSupported) {
      // Облегчённый символ: только uri без точного диапазона. Диапазон будет достроен
      // в workspaceSymbol/resolve, чтобы не вычислять его для всех символов сразу.
      workspaceSymbol.setLocation(Either.forRight(new WorkspaceSymbolLocation(uri.toString())));
      workspaceSymbol.setData(new WorkspaceSymbolData(uri, symbol.getName(), containerName.orElse(null)));
    } else {
      workspaceSymbol.setLocation(Either.forLeft(new Location(uri.toString(), symbol.getRange())));
    }

    return workspaceSymbol;
  }

  /**
   * Извлекает данные облегчённого символа из поля {@link WorkspaceSymbol#getData()}.
   * <p>
   * При получении запроса {@code workspaceSymbol/resolve} клиент возвращает поле {@code data}
   * сериализованным (JSON-объект), поэтому при необходимости выполняется его десериализация
   * в {@link WorkspaceSymbolData}.
   *
   * @param rawData Сырое значение поля {@code data} символа.
   * @return Данные облегчённого символа, либо {@code null}, если поле отсутствует.
   */
  @SneakyThrows
  private @Nullable WorkspaceSymbolData extractData(@Nullable Object rawData) {
    if (rawData == null) {
      return null;
    }
    if (rawData instanceof WorkspaceSymbolData data) {
      return data;
    }
    return jsonMapper.readValue(rawData.toString(), WorkspaceSymbolData.class);
  }

  /**
   * Отыскивает точный диапазон символа в дереве символов документа-источника по данным
   * облегчённого символа.
   *
   * @param data Данные облегчённого символа (uri, имя, имя контейнера).
   * @return Диапазон символа, либо {@link Optional#empty()}, если документ или символ не найдены.
   */
  private Optional<Range> findSymbolRange(WorkspaceSymbolData data) {
    var uri = Absolute.uri(data.uri());
    var expectedContainerName = Optional.ofNullable(data.containerName());
    return serverContextProvider.getServerContext(uri)
      .map(serverContext -> serverContext.getDocument(uri))
      .map(SymbolProvider::getSymbolEntries)
      .orElseGet(Stream::empty)
      .filter(symbolEntry -> symbolEntry.symbol().getName().equals(data.name()))
      .filter(symbolEntry -> getContainerName(symbolEntry.symbol(), symbolEntry.scriptVariant())
        .equals(expectedContainerName))
      .map(symbolEntry -> symbolEntry.symbol().getRange())
      .findFirst();
  }

  /**
   * Формирует человекочитаемое имя контейнера символа на основе связанного объекта метаданных.
   * <p>
   * Представление ссылки на объект метаданных (например, {@code ОбщийМодуль.ПервыйОбщийМодуль}
   * или {@code CommonModule.ПервыйОбщийМодуль}) берётся в варианте встроенного языка проекта,
   * что позволяет различать одноимённые символы из разных объектов конфигурации.
   *
   * @param symbol        Символ, для которого формируется имя контейнера
   * @param scriptVariant Вариант встроенного языка проекта, в котором формируется представление ссылки
   * @return Имя контейнера, либо {@link Optional#empty()}, если документ не связан с объектом метаданных
   */
  private static Optional<String> getContainerName(SourceDefinedSymbol symbol, ScriptVariant scriptVariant) {
    return symbol.getOwner().getMdObject()
      .map(MD::getMdoReference)
      .map(mdoReference -> mdoReference.getMdoRef(scriptVariant));
  }

  /**
   * Определяет вариант встроенного языка проекта для формирования представления ссылок.
   *
   * @param documentContext Контекст документа, для которого определяется вариант языка
   * @return Вариант встроенного языка проекта
   */
  private static ScriptVariant scriptVariantOf(DocumentContext documentContext) {
    var language = documentContext.getScriptVariantLanguage();
    return language == Language.EN
      ? ScriptVariant.ENGLISH
      : ScriptVariant.RUSSIAN;
  }

  /**
   * Символ рабочей области вместе с разрешёнными атрибутами документа-источника.
   *
   * @param uri           Идентификатор документа, в котором определён символ
   * @param symbol        Символ исходного кода
   * @param scriptVariant Вариант встроенного языка проекта документа-источника
   */
  private record SymbolEntry(URI uri, SourceDefinedSymbol symbol, ScriptVariant scriptVariant) {
  }

  /**
   * Данные облегчённого символа рабочей области для восстановления точного диапазона
   * в запросе {@code workspaceSymbol/resolve}.
   * <p>
   * Передаётся клиенту в поле {@link WorkspaceSymbol#getData()} и возвращается сервером
   * без изменений при дорезолвливании.
   *
   * @param uri           Идентификатор документа, в котором определён символ.
   * @param name          Имя символа.
   * @param containerName Имя контейнера символа, либо {@code null}, если контейнера нет.
   */
  record WorkspaceSymbolData(
    @JsonAdapter(URITypeAdapter.class) URI uri,
    String name,
    @Nullable String containerName
  ) {
  }
}
