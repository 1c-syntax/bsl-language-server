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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Голые (без {@code ЭтотОбъект.}) self-члены индексируются {@code ReferenceIndexFiller}
 * как обращения к {@code PlatformMemberSymbol}, а {@link SymbolsSemanticTokensSupplier}
 * красит их из индекса — отдельного сапплаера нет. Проверяем именно этот путь:
 * реквизит → {@code Property}, платформенный метод → {@code Method}+{@code DefaultLibrary}.
 */
@CleanupContextBeforeClassAndAfterClass
@Import(SemanticTokensTestHelper.class)
class SelfMemberIndexColoringTest extends AbstractServerContextAwareTest {

  @Autowired
  private SymbolsSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Autowired
  private TypeService typeService;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void bareSelfMembersAreColoredBySymbolsSupplierFromIndex() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        Записать();
        Реквизит1 = "А";
        Х = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

      helper.assertContainsTokens(decoded, List.of(
        // Записать — платформенный метод объекта (self-метод) → Method+DefaultLibrary.
        new ExpectedToken(1, 2, 8, SemanticTokenTypes.Method,
          SemanticTokenModifiers.DefaultLibrary, "Записать"),
        // Реквизит1 — собственный реквизит конфигурации → Property без DefaultLibrary,
        // и на записи, и на чтении.
        new ExpectedToken(2, 2, 9, SemanticTokenTypes.Property, "Реквизит1"),
        new ExpectedToken(3, 6, 9, SemanticTokenTypes.Property, "Реквизит1")
      ));
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

  @Test
  void bareSelfMethodInManagerModuleIsColoredStatic() {
    // Модуль менеджера — static-модуль: вызов платформенного self-метода менеджера
    // (СоздатьЭлемент) размечается Method + DefaultLibrary + Static, как и объявленные
    // методы такого модуля — в отличие от объектного модуля выше, где self-метод без Static.
    // Тип менеджера с этим методом берётся из JSON-fallback (СправочникМенеджер.<Имя>),
    // так что HBK не нужен.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ManagerModule.bsl").toUri();
    var content = """
      Процедура Тест()
        СоздатьЭлемент();
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

      helper.assertContainsTokens(decoded, List.of(
        new ExpectedToken(1, 2, 14, SemanticTokenTypes.Method,
          Set.of(SemanticTokenModifiers.DefaultLibrary, SemanticTokenModifiers.Static), "СоздатьЭлемент")
      ));
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

  /**
   * Регресс на гонку «документ открыт на старте»: дерево символов строится внутри
   * {@code DocumentContext.rebuild} ДО того, как {@code register} наполнит
   * {@code moduleTypeRefByUri} (он бежит на событии, публикуемом уже после сборки).
   * Значит на первой сборке кэша self-типа ещё нет — и присваиваемый реквизит
   * ({@code Реквизит1 = …}) обязан распознаваться как self-член напрямую из
   * метаданных, иначе {@code VariableSymbolComputer} заведёт на него фантомную
   * DYNAMIC-переменную, затеняющую реквизит до первой правки файла.
   */
  @Test
  void assignedSelfMemberIsRecognizedWithoutModuleTypeCache() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var documentContext = TestUtils.getDocumentContext(uri, "Процедура Тест()\nКонецПроцедуры", context);
    try {
      // Симулируем момент первой сборки дерева: кэш ещё не наполнен register()'ом.
      globalScopeProvider.removeModuleType(uri);
      assertThat(globalScopeProvider.moduleTypeRefByUri(uri)).isEmpty();

      // Реквизит конфигурации распознаётся как self-член из метаданных, а не как
      // самостоятельная переменная — фантом не заводится.
      assertThat(typeService.isBareSelfProperty(documentContext, "Реквизит1")).isTrue();
      // Заведомо не-член остаётся обычной переменной.
      assertThat(typeService.isBareSelfProperty(documentContext, "ПеременнаяКоторойНет")).isFalse();
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }
}
