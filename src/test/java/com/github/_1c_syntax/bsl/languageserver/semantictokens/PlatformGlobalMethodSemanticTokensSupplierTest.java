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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class PlatformGlobalMethodSemanticTokensSupplierTest {

  @Autowired
  private PlatformGlobalMethodSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void emitsDefaultLibraryForPlatformGlobal() {
    // Сообщить — платформенная глобальная функция → Method+DefaultLibrary.
    String bsl = """
      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    var decoded = helper.getDecodedTokens(bsl, supplier);

    var expected = List.of(
      new ExpectedToken(1, 2, 8, SemanticTokenTypes.Function,
        SemanticTokenModifiers.DefaultLibrary, "Сообщить")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void emitsForMultipleGlobalsAndSkipsUnknownNames() {
    // Сообщить и СтрДлина известны как платформенные → подсвечиваются.
    // НеизвестнаяФункция в реестре нет → пропускается.
    String bsl = """
      Процедура Тест()
        Сообщить("a");
        Длина = СтрДлина("hello");
        НеизвестнаяФункция();
      КонецПроцедуры
      """;

    var decoded = helper.getDecodedTokens(bsl, supplier).stream().sorted().toList();

    var expected = List.of(
      new ExpectedToken(1, 2, 8, SemanticTokenTypes.Function,
        SemanticTokenModifiers.DefaultLibrary, "Сообщить"),
      new ExpectedToken(2, 10, 8, SemanticTokenTypes.Function,
        SemanticTokenModifiers.DefaultLibrary, "СтрДлина")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void skipsLocallyDefinedMethodShadowingGlobal() {
    // Локальная процедура с тем же именем что и платформенный глобал должна выиграть:
    // этот supplier игнорирует её, MethodCallSemanticTokensSupplier подсветит как обычный Method.
    String bsl = """
      Процедура Сообщить(Текст)
      КонецПроцедуры

      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    var decoded = helper.getDecodedTokens(bsl, supplier);

    assertThat(decoded).isEmpty();
  }
}
