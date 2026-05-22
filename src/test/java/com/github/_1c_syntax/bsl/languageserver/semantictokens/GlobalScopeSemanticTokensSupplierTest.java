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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.lsp4j.SemanticTokenModifiers.DefaultLibrary;

@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class GlobalScopeSemanticTokensSupplierTest extends AbstractServerContextAwareTest {

  @Autowired
  private GlobalScopeSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  // --- Cases ported from removed ModuleReferenceSemanticTokensSupplierTest ---

  @Test
  void commonModuleReferenceHighlightedAsNamespace() throws IOException {
    // given - конфигурация с общим модулем ПервыйОбщийМодуль, документ ссылается на него.
    initServerContext("src/test/resources/metadata/designer");
    var file = new File("src/test/resources/metadata/designer",
      "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");
    var uri = Absolute.uri(file);
    TestUtils.getDocumentContext(uri,
      FileUtils.readFileToString(file, StandardCharsets.UTF_8), context);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndex.bsl");

    // when
    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // then - `ПервыйОбщийМодуль` на line 2 (0-idx), col 4, len 17 → Namespace без модификатора.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 4, 17, SemanticTokenTypes.Namespace, "ПервыйОбщийМодуль")
    ));
  }

  @Test
  void variableTypedAsCommonModuleNotHighlightedAsNamespace() {
    // given - паттерн `Модуль = ОбщегоНазначения.ОбщийМодуль("X"); Модуль.Метод()`.
    // Переменная-носитель типа-модуля не должна получать Namespace-токен:
    // её идентификатор — это локальная переменная, перекрывает global scope.
    initServerContext("src/test/resources/metadata/designer");
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexCommonModuleVariable.bsl");

    // when
    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // then - переменная `МодульУправлениеДоступом` (line 7/10/13) — локальное имя,
    // поэтому никаких глобал-токенов на этих позициях этот сапплаер не выдаёт.
    assertThat(decoded)
      .filteredOn(t -> t.line() == 7 || t.line() == 10 || t.line() == 13)
      .isEmpty();
  }

  @Test
  void oScriptLibraryModuleHighlightedAsNamespace() {
    // given - OScript-библиотека `mylib` с модулем `MyModule`.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/mylib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    var content = "MyModule.ВывестиСообщение(\"Привет\");\n";
    var documentContext = TestUtils.getDocumentContext(
      TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    // when
    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // then - `MyModule` (line 0, col 0, len 8) → Namespace.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(0, 0, 8, SemanticTokenTypes.Namespace, "MyModule")
    ));
  }

  @Test
  void emitsNothingForBareLocalMethodCall() {
    // given - чистый BSL без global-scope-идентификаторов.
    String bsl = """
      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - этот сапплаер ничего не выдаёт (Сообщить — глобальная функция,
    // её рисует PlatformGlobalMethodSemanticTokensSupplier).
    assertThat(decoded).isEmpty();
  }

  // --- New cases (metadata roots, system enums) ---

  @Test
  void metadataCollectionRootAndMdoRefHighlighted() throws IOException {
    // given - ManagerModule с цепочкой `Справочники.Справочник1.ТестЭкспортная();`
    // на line 19 (0-idx). `Справочники` — корень коллекции метаданных,
    // `Справочник1` — конкретный mdo-ref.
    initServerContext(TestUtils.PATH_TO_METADATA);
    var dc = TestUtils.getDocumentContextFromFile(
      TestUtils.PATH_TO_METADATA + "/Catalogs/Справочник1/Ext/ManagerModule.bsl", context);

    // when
    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(dc));

    // then - корень `Справочники` (col 4, len 11) → Class+DefaultLibrary;
    //        mdo-ref `Справочник1` (col 16, len 11) → Class без модификатора.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(19, 4, 11, SemanticTokenTypes.Class,
        Set.of(DefaultLibrary), "Справочники"),
      new ExpectedToken(19, 16, 11, SemanticTokenTypes.Class, "Справочник1")
    ));
  }

  @Test
  void sourceDefinedMethodInChainNotPainted() throws IOException {
    // given - source-defined `ТестЭкспортная` на line 19 col 28 длиной 14.
    // Должен рисоваться MethodCallSemanticTokensSupplier'ом как Method+Static,
    // наш сапплаер его пропускает (sourceSymbol — SourceDefinedSymbol).
    initServerContext(TestUtils.PATH_TO_METADATA);
    var dc = TestUtils.getDocumentContextFromFile(
      TestUtils.PATH_TO_METADATA + "/Catalogs/Справочник1/Ext/ManagerModule.bsl", context);

    // when
    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(dc));

    // then - на позиции `ТестЭкспортная` (col 28) этот сапплаер токенов не выдаёт.
    assertThat(decoded)
      .filteredOn(t -> t.line() == 19 && t.start() == 28)
      .isEmpty();
  }
}
