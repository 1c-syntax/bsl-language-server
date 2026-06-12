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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

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

  private static final Set<VariableKind> SUPPORTED_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    var pattern = compilePattern(queryString);

    // Search for symbols in all workspace contexts
    return serverContextProvider.getAllContexts().values().stream()
      .flatMap(serverContext -> serverContext.getDocuments().values().stream())
      .flatMap(SymbolProvider::getSymbolEntries)
      .filter(symbolEntry -> queryString.isEmpty() || pattern.matcher(symbolEntry.symbol().getName()).find())
      .map(SymbolProvider::createWorkspaceSymbol)
      .collect(Collectors.toList());
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
      return Pattern.compile(Pattern.quote(queryString), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
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

  private static WorkspaceSymbol createWorkspaceSymbol(SymbolEntry symbolEntry) {
    var uri = symbolEntry.uri();
    var symbol = symbolEntry.symbol();
    var location = new Location(uri.toString(), symbol.getRange());

    var workspaceSymbol = new WorkspaceSymbol();
    workspaceSymbol.setName(symbol.getName());
    workspaceSymbol.setKind(symbol.getSymbolKind());
    workspaceSymbol.setLocation(Either.forLeft(location));
    workspaceSymbol.setTags(symbol.getTags());
    getContainerName(symbol, symbolEntry.scriptVariant()).ifPresent(workspaceSymbol::setContainerName);

    return workspaceSymbol;
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
}
