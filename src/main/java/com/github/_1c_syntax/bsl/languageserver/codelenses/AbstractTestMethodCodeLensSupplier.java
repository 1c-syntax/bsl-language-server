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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.codelenses.testrunner.TestRunnerAdapter;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.configuration.codelens.TestRunnerAdapterOptions;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Базовый поставщик линз на каждом тестовом методе: линза на имени метода, по резолву — команда
 * клиента с командной строкой раннера тестов для этого метода.
 * <p>
 * Наследник задаёт идентификатор команды, аргументы раннера и тип данных линзы; заголовок и
 * подсказка берутся из ресурсов наследника (ключ заголовка — {@link #titleKey()}, подсказки —
 * {@code tooltip}).
 *
 * @param <T> тип данных линзы
 */
public abstract class AbstractTestMethodCodeLensSupplier<T extends CodeLensData & AbstractTestMethodCodeLensSupplier.TestMethodData>
  extends AbstractRunTestsCodeLensSupplier<T> {

  private final TestRunnerAdapter testRunnerAdapter;
  private final Resources resources;

  /**
   * @param configuration            Конфигурация language server.
   * @param clientCapabilitiesHolder Хранилище клиентских возможностей.
   * @param testSourcesProvider      Поставщик каталогов с исходниками тестов.
   * @param testRunnerAdapter        Адаптер внешнего раннера тестов.
   * @param resources                Локализованные ресурсы сервера.
   */
  protected AbstractTestMethodCodeLensSupplier(
    LanguageServerConfiguration configuration,
    ClientCapabilitiesHolder clientCapabilitiesHolder,
    TestSourcesProvider testSourcesProvider,
    TestRunnerAdapter testRunnerAdapter,
    Resources resources
  ) {
    super(configuration, clientCapabilitiesHolder, testSourcesProvider);
    this.testRunnerAdapter = testRunnerAdapter;
    this.resources = resources;
  }

  /**
   * Данные линзы тестового метода.
   */
  public interface TestMethodData {
    /**
     * @return идентификатор (имя) тестового метода
     */
    String getTestId();
  }

  /**
   * @return идентификатор команды клиента
   */
  protected abstract String commandId();

  /**
   * @return ключ ресурса с заголовком линзы
   */
  protected abstract String titleKey();

  /**
   * @param options настройки адаптера раннера тестов
   * @return шаблон аргументов раннера ({@code %s} — путь к файлу, затем идентификатор теста)
   */
  protected abstract String commandArguments(TestRunnerAdapterOptions options);

  /**
   * @param options настройки адаптера раннера тестов
   * @return показывать ли линзы при этих настройках
   */
  protected boolean isAvailable(TestRunnerAdapterOptions options) {
    return true;
  }

  /**
   * @param uri    URI документа
   * @param id     идентификатор линзы
   * @param testId идентификатор теста
   * @return данные линзы
   */
  protected abstract T createData(URI uri, String id, String testId);

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
    if (documentContext.getFileType() == FileType.BSL
      || !isAvailable(configuration.getCodeLensOptions().getTestRunnerAdapterOptions())) {
      return Collections.emptyList();
    }

    var symbolTree = documentContext.getSymbolTree();
    return testRunnerAdapter.getTestIds(documentContext).stream()
      .map(symbolTree::getMethodSymbol)
      .flatMap(Optional::stream)
      .map(methodSymbol -> toCodeLens(methodSymbol, documentContext))
      .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, T data) {
    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();
    var runText = (options.getExecutableForCurrentOS() + " " + commandArguments(options))
      .formatted(Path.of(documentContext.getUri()), data.getTestId());

    var command = new Command();
    command.setTitle(resources.getResourceString(getClass(), titleKey()));
    command.setTooltip(resources.getResourceString(getClass(), "tooltip"));
    command.setCommand(commandId());
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);
    return unresolved;
  }

  private CodeLens toCodeLens(MethodSymbol method, DocumentContext documentContext) {
    var codeLens = new CodeLens(method.getSubNameRange());
    codeLens.setData(createData(documentContext.getUri(), getId(), method.getName()));
    return codeLens;
  }
}
