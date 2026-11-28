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
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;
import java.net.URI;

/**
 * Поставщик линз для отладки теста по конкретному тестовому методу. Линзы не показываются, пока
 * не заданы аргументы раннера для отладки.
 */
@Component
@Order(3)
public class DebugTestCodeLensSupplier
  extends AbstractTestMethodCodeLensSupplier<DebugTestCodeLensSupplier.DebugTestCodeLensData> {

  private static final String COMMAND_ID = "language-1c-bsl.languageServer.debugTest";

  /**
   * Конструктор поставщика линз отладки теста.
   *
   * @param configuration           Конфигурация language server.
   * @param clientCapabilitiesHolder Хранилище клиентских возможностей.
   * @param testSourcesProvider     Поставщик каталогов с исходниками тестов.
   * @param testRunnerAdapter       Адаптер внешнего раннера тестов.
   * @param resources               Локализованные ресурсы сервера.
   */
  public DebugTestCodeLensSupplier(
    LanguageServerConfiguration configuration,
    ClientCapabilitiesHolder clientCapabilitiesHolder,
    TestSourcesProvider testSourcesProvider,
    TestRunnerAdapter testRunnerAdapter,
    Resources resources
  ) {
    super(configuration, clientCapabilitiesHolder, testSourcesProvider, testRunnerAdapter, resources);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<DebugTestCodeLensData> getCodeLensDataClass() {
    return DebugTestCodeLensData.class;
  }

  @Override
  protected String commandId() {
    return COMMAND_ID;
  }

  @Override
  protected String titleKey() {
    return "title";
  }

  @Override
  protected String commandArguments(TestRunnerAdapterOptions options) {
    return options.getDebugTestArguments();
  }

  @Override
  protected boolean isAvailable(TestRunnerAdapterOptions options) {
    return !options.getDebugTestArguments().isEmpty();
  }

  @Override
  protected DebugTestCodeLensData createData(URI uri, String id, String testId) {
    return new DebugTestCodeLensData(uri, id, testId);
  }

  /**
   * DTO для хранения данных линз для отладки теста.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class DebugTestCodeLensData extends DefaultCodeLensData implements TestMethodData {
    /**
     * Имя метода.
     */
    String testId;

    /**
     * @param uri    URI документа.
     * @param id     Идентификатор линзы.
     * @param testId Идентификатор теста.
     */
    @ConstructorProperties({"uri", "id", "testId"})
    public DebugTestCodeLensData(URI uri, String id, String testId) {
      super(uri, id);
      this.testId = testId;
    }
  }
}
