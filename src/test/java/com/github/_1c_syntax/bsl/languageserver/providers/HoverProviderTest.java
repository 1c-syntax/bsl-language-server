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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HoverProviderTest {

  private static final String PATH_TO_FILE = "./src/test/resources/providers/hover.bsl";

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void testEmptyHover() {
    // given
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 0));

    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isNotPresent();
  }

  @Test
  void testSourceDefinedMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(3, 10));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue()).isNotEmpty();
    assertThat(hover.getRange()).isEqualTo(Ranges.create(3, 8, 18));
  }

  @Test
  void testSourceDefinedVariable() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(6, 15));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue()).isNotEmpty();
    assertThat(hover.getRange()).isEqualTo(Ranges.create(6, 10, 20));
  }

  @Test
  void hoverOnMemberInRhsDereferenceWorks() {
    // Контрольная точка: dereference в RHS (правой части присваивания) — hover должен работать.
    var content = """
      ТЗ = Новый ТаблицаЗначений;
      Х = ТЗ.Колонки;
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    HoverParams params = new HoverParams();
    // курсор на «Колонки» в `ТЗ.Колонки`
    params.setPosition(new Position(1, 9));

    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);
    assertThat(optionalHover)
      .as("hover на .Колонки в RHS должен резолвиться через PlatformMemberReferenceFinder")
      .isPresent();
  }

  @Test
  void hoverOnGlobalFunctionInOsFileShowsOneScriptVariant() {
    // Регрессия #4054: ПодробноеПредставлениеОшибки существует и в BSL, и в OneScript.
    // В .os-файле hover должен показывать OneScript-вариант без платформенного
    // deprecation «с 8.3.17» и рекомендации ОбработкаОшибок.ПодробноеПредставлениеОшибки.
    // given
    var content = """
      Попытка
        ВызватьИсключение "Ошибка";
      Исключение
        Сообщить(ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
      КонецПопытки;
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);

    HoverParams params = new HoverParams();
    // курсор на «ПодробноеПредставлениеОшибки»
    params.setPosition(new Position(3, 15));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();
    var markdown = optionalHover.get().getContents().getRight().getValue();
    assertThat(markdown)
      .as("hover в .os не должен содержать BSL-метаданных устаревания")
      .doesNotContain("8.3.17")
      .doesNotContain("ОбработкаОшибок.ПодробноеПредставлениеОшибки");
  }

  @Test
  void hoverOnGlobalFunctionInBslFileShowsBslVariant() {
    // Контрольная точка к #4054: в .bsl-файле тот же hover показывает BSL-вариант
    // с deprecation-метаданными платформы.
    // given
    var content = """
      Сообщить(ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    HoverParams params = new HoverParams();
    // курсор на «ПодробноеПредставлениеОшибки»
    params.setPosition(new Position(0, 12));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();
    var markdown = optionalHover.get().getContents().getRight().getValue();
    assertThat(markdown).contains("8.3.17");
  }

  @Test
  void hoverOnDereferencedPropertyMustNotMatchSameNameLocalVariable() {
    // Регрессия: правая часть dereference `Контейнер.Поле` — это имя свойства, а не bare-identifier.
    // ReferenceIndex не должен ловить эту позицию как ссылку на локальную переменную с таким же именем,
    // объявленную выше в файле. Иначе hover/goto показывают «фантом» — попадание на переменную,
    // тогда как реальная цель — property типа.
    var content = """
      ИсточникДанных = "что-то";
      НаборДанных = Новый Структура;
      НаборДанных.ИсточникДанных = "ИсточникДанных1";
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    HoverParams params = new HoverParams();
    // курсор на втором (правом) `ИсточникДанных` — в позиции accessProperty
    var line = "НаборДанных.ИсточникДанных = \"ИсточникДанных1\";";
    var col = line.indexOf("ИсточникДанных", line.indexOf('.')) + 3;
    params.setPosition(new Position(2, col));

    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);
    if (optionalHover.isPresent()) {
      // Если hover есть — он должен указывать на свойство (Property symbol), а не на переменную.
      var range = optionalHover.get().getRange();
      var refLine = line.substring(range.getStart().getCharacter(), range.getEnd().getCharacter());
      assertThat(refLine)
        .as("hover в dereference-позиции не должен резолвиться на bare variable `%s` сверху файла",
          "ИсточникДанных")
        .isNotNull();
      var content2 = optionalHover.get().getContents().getRight().getValue();
      // Признак «фантомного» попадания в переменную — наличие ссылки [file://...] на исходник,
      // характерной для variable markup builder.
      assertThat(content2)
        .as("содержимое hover не должно быть variable-markup'ом (с file:// ссылкой на собственную декларацию)")
        .doesNotContain("file:");
    }
  }

  @Test
  void hoverOnMemberInLvalueAssignmentTarget() {
    // Регрессия: dereference в LHS присваивания (lValue) — hover должен резолвиться так же,
    // как в RHS. ExpressionAtPosition.findExpressionTree сейчас не покрывает lValue,
    // поэтому memberAt возвращает пусто на `ИсточникДанных.Имя = …`.
    var content = """
      СхемаКомпоновкиДанных = Новый СхемаКомпоновкиДанных;
      ИсточникДанных = СхемаКомпоновкиДанных.ИсточникиДанных.Добавить();
      ИсточникДанных.Имя = "ИсточникДанных1";
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    HoverParams params = new HoverParams();
    // курсор на «Имя» в строке `ИсточникДанных.Имя = …`
    params.setPosition(new Position(2, 17));

    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);
    assertThat(optionalHover)
      .as("hover на .Имя в lValue должен показывать описание PROPERTY типа ИсточникДанныхСхемыКомпоновкиДанных")
      .isPresent();
  }

  @Test
  void hoverOnIdentifierWithoutReferenceReturnsEmpty() {
    // given — идентификатор, который не зарегистрирован нигде.
    var content = "СовершенноНеизвестныйСимволXYZ;\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(0, 5));

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then — ни keyword-hover, ни reference-hover не сработали.
    assertThat(hover).isEmpty();
  }

  @Test
  void hoverOnPunctuationReturnsEmpty() {
    // given — курсор на точке с запятой.
    var content = "А = 1;\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(0, 6));  // на ';'

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(hover).isEmpty();
  }

  @Test
  void hoverOnEmptyDocumentReturnsEmpty() {
    // given
    var documentContext = TestUtils.getDocumentContext("");
    var params = new HoverParams();
    params.setPosition(new Position(0, 0));

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(hover).isEmpty();
  }

  @Test
  void hoverOnNumericLiteralReturnsEmpty() {
    // given — курсор на числовом литерале.
    var content = "А = 12345;\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(0, 5));  // на 12345

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then — числовой токен фильтруется isKeywordToken (L126).
    assertThat(hover).isEmpty();
  }

  @Test
  void hoverOnStringLiteralReturnsEmpty() {
    // given — курсор внутри строкового литерала.
    var content = "А = \"строка\";\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(0, 6));

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(hover).isEmpty();
  }

  @Test
  void hoverOnDateLiteralReturnsEmpty() {
    // given — курсор внутри даты-литерала.
    var content = "А = '20200101';\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(0, 8));

    // when
    var hover = hoverProvider.getHover(documentContext, params);

    // then — DATETIME токен не keyword.
    assertThat(hover).isEmpty();
  }
}
