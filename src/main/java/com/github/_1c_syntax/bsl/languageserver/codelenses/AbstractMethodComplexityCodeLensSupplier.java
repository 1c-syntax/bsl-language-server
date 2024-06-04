/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.commands.complexity.AbstractToggleComplexityInlayHintsCommandSupplier;
import com.github._1c_syntax.bsl.languageserver.commands.complexity.ToggleComplexityInlayHintsCommandArguments;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Базовый класс для реализации линз, показывающих сложность методов в документе.
 * <p>
 * Конкретный сапплаер должен иметь ресурс-бандл со свойством {@code title}, имеющим один числовой параметр {@code %d}.
 */
@RequiredArgsConstructor
public abstract class AbstractMethodComplexityCodeLensSupplier
  implements CodeLensSupplier<AbstractMethodComplexityCodeLensSupplier.ComplexityCodeLensData> {

  private static final String TITLE_KEY = "title";
  private static final int DEFAULT_COMPLEXITY_THRESHOLD = -1;

  protected final LanguageServerConfiguration configuration;
  private final AbstractToggleComplexityInlayHintsCommandSupplier commandSupplier;

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
    var complexityThreshold = getComplexityThreshold();
    var methodsComplexity = getMethodsComplexity(documentContext);
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> methodsComplexity.get(methodSymbol) >= complexityThreshold)
      .map(methodSymbol -> toCodeLens(methodSymbol, documentContext))
      .collect(Collectors.toList());
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, ComplexityCodeLensData data) {
    var methodName = data.getMethodName();
    var methodsComplexity = getMethodsComplexity(documentContext);
    documentContext.getSymbolTree().getMethodSymbol(methodName).ifPresent((MethodSymbol methodSymbol) -> {
      int complexity = methodsComplexity.get(methodSymbol);

      var title = Resources.getResourceString(configuration.getLanguage(), getClass(), TITLE_KEY, complexity);
      var arguments = new ToggleComplexityInlayHintsCommandArguments(
        commandSupplier.getId(),
        data
      );

      var command = commandSupplier.createCommand(title, arguments);

      unresolved.setCommand(command);
    });

    return unresolved;
  }

  @Override
  public Class<ComplexityCodeLensData> getCodeLensDataClass() {
    return ComplexityCodeLensData.class;
  }

  /**
   * Получить данные о сложности в разрезе символов.
   *
   * @param documentContext Документ, для которого нужно рассчитать информацию о сложностях методов.
   * @return Данные о сложности методов.
   */
  protected abstract Map<MethodSymbol, Integer> getMethodsComplexity(DocumentContext documentContext);

  private int getComplexityThreshold() {
    var parameters = configuration.getCodeLensOptions().getParameters().getOrDefault(getId(), Either.forLeft(true));
    if (parameters.isLeft()) {
      return DEFAULT_COMPLEXITY_THRESHOLD;
    } else {
      return (int) parameters.getRight().getOrDefault("complexityThreshold", DEFAULT_COMPLEXITY_THRESHOLD);
    }
  }

  private CodeLens toCodeLens(MethodSymbol methodSymbol, DocumentContext documentContext) {
    var data = new ComplexityCodeLensData(documentContext.getUri(), getId(), methodSymbol.getName());

    var codeLens = new CodeLens(methodSymbol.getSubNameRange());
    codeLens.setData(data);

    return codeLens;
  }

  /**
   * DTO для хранения данных линз о сложности методов в документе.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class ComplexityCodeLensData extends DefaultCodeLensData {
    /**
     * Имя метода.
     */
    String methodName;

    @ConstructorProperties({"uri", "id", "methodName"})
    public ComplexityCodeLensData(URI uri, String id, String methodName) {
      super(uri, id);
      this.methodName = methodName;
    }
  }
}
