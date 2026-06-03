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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SemanticTokensTestHelper.class)
class PlatformMemberMethodCallSemanticTokensSupplierTest extends AbstractServerContextAwareTest {

  @Autowired
  private PlatformMemberMethodCallSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testPlatformMethodOnTypedVariable() {
    // given — массив с известным методом Добавить из платформы.
    String bsl = """
      Процедура Тест()
          М = Новый Массив;
          М.Добавить(1);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Добавить подсвечивается как Method+DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 6, 8, SemanticTokenTypes.Method,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Добавить")
    ));
  }

  // Range-scoping — общее поведение базы AbstractPlatformMemberSemanticTokensSupplier,
  // покрыто в PlatformMemberPropertyAccessSemanticTokensSupplierTest (один сапплаер
  // на общий путь, чтобы не дублировать тест).

  @Test
  void testPlatformMethodAfterAwait() {
    // given — реальный кейс: вызов метода платформенного типа после Ждать.
    // Воспроизводит юзер-репорт «не красится метод после Ждать».
    String bsl = """
      Асинх Процедура Тест()
          М = Новый Массив;
          Ждать М.Добавить(1);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Добавить подсвечен даже внутри Ждать-выражения.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 12, 8, SemanticTokenTypes.Method,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Добавить")
    ));
  }

  @Test
  void testAccessPropertyIgnored() {
    // given — property access (без скобок) этим сапплаером не подсвечивается:
    // мы обходим только accessCall (с doCall), а accessProperty — нет.
    String bsl = """
      Процедура Тест()
          Стр = Новый Структура("Имя, Возраст", "Иван", 30);
          А = Стр.Имя;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — никаких токенов для accessProperty.
    assertThat(decoded).isEmpty();
  }

  @Test
  void testModifiersForAsyncDescriptor() {
    // given
    var asyncMethod = MemberDescriptor.method("ИнициализироватьАсинх").withAsync(true);

    // when
    var mods = PlatformMemberMethodCallSemanticTokensSupplier.modifiers(asyncMethod);

    // then — async-метод платформы получает DefaultLibrary + Async.
    assertThat(mods).containsExactly(SemanticTokenModifiers.DefaultLibrary, SemanticTokenModifiers.Async);
  }

  @Test
  void testModifiersForRegularDescriptor() {
    // given
    var regularMethod = MemberDescriptor.method("Добавить");

    // when
    var mods = PlatformMemberMethodCallSemanticTokensSupplier.modifiers(regularMethod);

    // then — обычный метод платформы получает только DefaultLibrary.
    assertThat(mods).containsExactly(SemanticTokenModifiers.DefaultLibrary);
  }

  @Test
  void testUnknownTypeReceiverProducesNoToken() {
    // given — переменная без type inference: тип не резолвится → метод не подсвечивается.
    String bsl = """
      Процедура Тест(НеТипизированный)
          НеТипизированный.НеизвестныйМетод(1);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — пусто (метод неизвестен).
    assertThat(decoded).isEmpty();
  }
}
