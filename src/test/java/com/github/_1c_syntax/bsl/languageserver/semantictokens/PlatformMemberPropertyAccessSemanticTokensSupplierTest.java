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
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SemanticTokensTestHelper.class)
class PlatformMemberPropertyAccessSemanticTokensSupplierTest extends AbstractServerContextAwareTest {

  private static final Range DUMMY_RANGE = Ranges.create();

  @Autowired
  private PlatformMemberPropertyAccessSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Test
  void testPlatformPropertyOnTypedVariable() {
    // given — таблица значений с известным свойством Колонки из платформы.
    String bsl = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          К = ТЗ.Колонки;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Колонки подсвечивается как Property+DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 11, 7, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Колонки")
    ));
  }

  @Test
  void testPropertyChain() {
    // given — обращение к свойству в середине цепочки accessProperty.
    String bsl = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          Колво = ТЗ.Колонки.Количество();
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Колонки (accessProperty) подсвечен; Количество (accessCall) — нет.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 15, 7, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Колонки")
    ));
    // Количество начинается на line 2, char 23 (после "ТЗ.Колонки.").
    assertThat(decoded)
      .as("Количество — это accessCall, не accessProperty; данный сапплаер его не выдаёт")
      .noneMatch(token -> token.line() == 2 && token.start() == 23);
  }

  @Test
  void testStructureDynamicFieldHighlighted() {
    // given — кейс из issue: поле Структуры, накопленное из конструктора.
    String bsl = """
      Процедура Тест()
          Стр = Новый Структура("Имя", "Иван");
          А = Стр.Имя;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — Имя подсвечивается как Property+DefaultLibrary.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(2, 12, 3, SemanticTokenTypes.Property,
        Set.of(SemanticTokenModifiers.DefaultLibrary), "Имя")
    ));
  }

  @Test
  void testMethodCallNotHighlightedAsProperty() {
    // given — вызов метода платформенного типа (accessCall, со скобками).
    String bsl = """
      Процедура Тест()
          М = Новый Массив;
          М.Добавить(1);
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — accessCall этим сапплаером не подсвечивается.
    assertThat(decoded).isEmpty();
  }

  @Test
  void testUnknownTypeReceiverProducesNoToken() {
    // given — переменная без type inference: тип не резолвится → свойство не подсвечивается.
    String bsl = """
      Процедура Тест(НеТипизированный)
          А = НеТипизированный.НеизвестноеСвойство;
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then — пусто (свойство неизвестно).
    assertThat(decoded).isEmpty();
  }

  @Test
  void testHighlightablePlatformProperty() {
    // given — обычное платформенное свойство (Колонки), returnType не из конфигурации.
    var owner = new TypeRef(TypeKind.PLATFORM, "ТаблицаЗначений");
    var returnType = new TypeRef(TypeKind.PLATFORM, "КоллекцияКолонокТаблицыЗначений");
    var member = new TypedMember(owner, MemberDescriptor.property("Колонки", returnType), DUMMY_RANGE);

    // when
    var result = PlatformMemberPropertyAccessSemanticTokensSupplier.isHighlightableProperty(member);

    // then — красим как Property.
    assertThat(result).isTrue();
  }

  @Test
  void testMethodNotHighlightable() {
    // given
    var owner = new TypeRef(TypeKind.PLATFORM, "Массив");
    var member = new TypedMember(owner, MemberDescriptor.method("Добавить"), DUMMY_RANGE);

    // when
    var result = PlatformMemberPropertyAccessSemanticTokensSupplier.isHighlightableProperty(member);

    // then — метод не является property.
    assertThat(result).isFalse();
  }

  @Test
  void testConfigurationReferenceMemberSkipped() {
    // given — Справочники.ПрофилиГруппДоступа: returnType — ссылка на метаобъект.
    // Такой член красит GlobalScope как Class; мы его пропускаем.
    var owner = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");
    var returnType = new TypeRef(TypeKind.CONFIGURATION, "СправочникСсылка.ПрофилиГруппДоступа");
    var member = new TypedMember(owner, MemberDescriptor.property("ПрофилиГруппДоступа", returnType), DUMMY_RANGE);

    // when
    var result = PlatformMemberPropertyAccessSemanticTokensSupplier.isHighlightableProperty(member);

    // then — не красим (домен GlobalScope, иначе конфликт Class vs Property).
    assertThat(result).isFalse();
  }

  @Test
  void testSelfTypedEnumMemberSkipped() {
    // given — КодировкаТекста.UTF8: owner совпадает с returnType (значение перечисления).
    // Такой член красит GlobalScope как EnumMember; мы его пропускаем.
    var enumType = new TypeRef(TypeKind.PLATFORM, "КодировкаТекста");
    var member = new TypedMember(enumType, MemberDescriptor.property("UTF8", enumType), DUMMY_RANGE);

    // when
    var result = PlatformMemberPropertyAccessSemanticTokensSupplier.isHighlightableProperty(member);

    // then — не красим (домен GlobalScope, иначе конфликт EnumMember vs Property).
    assertThat(result).isFalse();
  }
}
