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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Провайдер для получения структуры символов документа.
 * <p>
 * Обрабатывает запросы {@code textDocument/documentSymbol}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol">Document Symbols Request specification</a>
 */
@Component
public final class DocumentSymbolProvider {

  /**
   * Идентификатор источника символов документа.
   */
  public static final String LABEL = "BSL Language Server";

  private static final Set<VariableKind> supportedVariableKinds = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.LOCAL,
    VariableKind.GLOBAL
  );

  /**
   * Получить иерархическую структуру символов документа.
   *
   * @param documentContext Контекст документа
   * @return Список символов верхнего уровня документа
   */
  public List<DocumentSymbol> getDocumentSymbols(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getChildren().stream()
      .filter(DocumentSymbolProvider::isSupported)
      .map(DocumentSymbolProvider::toDocumentSymbol)
      .collect(Collectors.toList());
  }

  private static DocumentSymbol toDocumentSymbol(SourceDefinedSymbol symbol) {
    var documentSymbol = new DocumentSymbol(
      symbol.getName(),
      symbolKind(symbol),
      symbol.getRange(),
      symbol.getSelectionRange()
    );

    List<DocumentSymbol> children = symbol.getChildren().stream()
      .filter(DocumentSymbolProvider::isSupported)
      .map(DocumentSymbolProvider::toDocumentSymbol)
      .collect(Collectors.toList());

    documentSymbol.setTags(symbol.getTags());
    documentSymbol.setChildren(children);

    if (symbol instanceof MethodSymbol methodSymbol) {
      documentSymbol.setDetail(buildMethodDetail(methodSymbol));
    }

    return documentSymbol;
  }

  private static String buildMethodDetail(MethodSymbol methodSymbol) {
    return methodSymbol.getParameters().stream()
      .map(DocumentSymbolProvider::parameterSignature)
      .collect(Collectors.joining(", ", "(", ")"));
  }

  private static String parameterSignature(ParameterDefinition parameter) {
    if (parameter.isOptional()) {
      return parameter.getName() + "?";
    }
    return parameter.getName();
  }

  public static boolean isSupported(Symbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    if (symbolKind == SymbolKind.Variable) {
      return supportedVariableKinds.contains(((VariableSymbol) symbol).getKind());
    }
    return true;
  }

  /**
   * Определить вид символа для ответа на запрос структуры документа.
   * <p>
   * Метод модуля без состояния (модуль OneScript либо общий модуль BSL) отдаётся как
   * {@link SymbolKind#Function}: его методы — самостоятельные функции, а не члены объекта.
   * Методы модулей со состоянием (объект, менеджер, форма и т. п.) остаются
   * {@link SymbolKind#Method}, конструкторы и все прочие символы — без изменений.
   *
   * @param symbol символ структуры документа
   * @return вид символа: {@link SymbolKind#Function} для методов модулей без состояния,
   *   иначе исходный {@link Symbol#getSymbolKind()}
   */
  private static SymbolKind symbolKind(SourceDefinedSymbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    if (symbolKind == SymbolKind.Method && isStatelessModule(symbol.getOwner())) {
      return SymbolKind.Function;
    }
    return symbolKind;
  }

  /**
   * Проверить, что модуль не хранит состояние: модуль OneScript либо общий модуль BSL.
   * <p>
   * Методы таких модулей — самостоятельные функции, а не члены объекта со состоянием,
   * поэтому отображаются как {@link SymbolKind#Function}.
   *
   * @param documentContext контекст документа модуля
   * @return {@code true}, если модуль не хранит состояние
   */
  private static boolean isStatelessModule(DocumentContext documentContext) {
    return documentContext.getFileType() == FileType.OS
      || documentContext.getModuleType() == ModuleType.CommonModule;
  }
}
