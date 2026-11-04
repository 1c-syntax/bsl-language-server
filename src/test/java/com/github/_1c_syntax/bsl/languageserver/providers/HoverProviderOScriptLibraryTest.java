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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class HoverProviderOScriptLibraryTest extends AbstractServerContextAwareTest {

  @Autowired
  private HoverProvider hoverProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void hoverOnLibraryModuleMethodInStandaloneCall() {
    // Регресс: `ВременныеФайлы.НовоеИмяФайла();` — standalone call statement
    // (без присваивания), курсор на имени метода должен показать hover
    // от метода библ. модуля.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/dualmod").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = "#Использовать dualmod\nВременныеФайлы.НовоеИмяФайла();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    int colMethod = content.indexOf("НовоеИмяФайла") - content.indexOf('\n') - 1;
    var params = new HoverParams();
    params.setPosition(new Position(1, colMethod + 2));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на методе библ. модуля в standalone call statement должен быть непустым")
      .isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text).contains("НовоеИмяФайла");
  }

  @Test
  void hoverOnLibraryModuleReceiverInStandaloneCall() {
    // На самом identifier модуля в standalone call — должен быть hover на модуль.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/dualmod").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = "#Использовать dualmod\nВременныеФайлы.НовоеИмяФайла();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new HoverParams();
    params.setPosition(new Position(1, 2));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover)
      .as("hover на имени модуля в standalone call statement должен быть непустым")
      .isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text).contains("ВременныеФайлы");
  }

  @Test
  void hoverOnTypeNameInNewExpressionForClassWithCtorIsConstructorStyleAndSingleBlock() {
    // given: класс с явным ПриСозданииОбъекта(Параметр).
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/internal-classes-test").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = "#Использовать internal-classes-lib\nХ = Новый PublicEntity();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    int typeNameStart = content.indexOf("PublicEntity") - content.indexOf('\n') - 1;
    var params = new HoverParams();
    params.setPosition(new Position(1, typeNameStart + 2));

    // when
    var hover = hoverProvider.getHover(dc, params);

    // then
    assertThat(hover).as("hover на typeName c явным конструктором должен быть").isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text)
      .as("hover constructor-стиля содержит сигнатуру `Новый ИмяКласса(...)`")
      .contains("Новый PublicEntity(");
    // Защита от регресса "hover задублирован": ровно один заголовок,
    // ровно одна секция Параметры (если параметры есть).
    int titleCount = text.split("Новый PublicEntity\\(", -1).length - 1;
    assertThat(titleCount).as("заголовок не должен дублироваться, контент: %s", text).isEqualTo(1);
  }

  @Test
  void hoverOnTypeNameInNewExpressionForClassWithoutCtorIsConstructorStyle() {
    // given: класс БЕЗ ПриСозданииОбъекта — hover всё равно constructor-стиля
    // через ModuleSymbolMarkupContentBuilder → OScriptClassConstructorRenderer.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/internal-classes-test").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = "#Использовать internal-classes-lib\nХ = Новый ClassWithoutCtor();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    int typeNameStart = content.indexOf("ClassWithoutCtor") - content.indexOf('\n') - 1;
    var params = new HoverParams();
    params.setPosition(new Position(1, typeNameStart + 2));

    // when
    var hover = hoverProvider.getHover(dc, params);

    // then
    assertThat(hover).isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text)
      .as("hover для класса без конструктора — всё равно constructor-стиля")
      .contains("Новый ClassWithoutCtor(");
  }

  @Test
  void hoverOnUserTypeNameInMethodDescriptionResolves() {
    // given: имя пользовательского (OneScript) типа в описании метода. Резолвится
    // тем же DescriptionTypeReferenceFinder, что и платформенные типы — никакой
    // отдельной обработки для USER-типов нет.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/internal-classes-test").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var content = """
      #Использовать internal-classes-lib
      // Возвращаемое значение:
      //  PublicEntity - результат
      Функция Тест() Экспорт
        Возврат Неопределено;
      КонецФункции
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new HoverParams();
    params.setPosition(new Position(2, 8)); // курсор внутри «PublicEntity» в описании

    // when
    var hover = hoverProvider.getHover(dc, params);

    // then
    assertThat(hover)
      .as("hover по имени пользовательского типа в описании метода должен резолвиться")
      .isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text)
      .contains("PublicEntity")
      .doesNotContain("Новый");
  }
}
