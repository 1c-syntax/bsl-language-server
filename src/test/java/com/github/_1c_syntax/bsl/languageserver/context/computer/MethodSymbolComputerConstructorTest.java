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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegularMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессы для распознавания конструктора OneScript-класса
 * {@link com.github._1c_syntax.bsl.languageserver.utils.Methods#isOscriptClassConstructorName}
 * в {@link MethodSymbolComputer}: процедура с именем {@code ПриСозданииОбъекта}
 * или {@code OnObjectCreate} в {@code .os}-файле создаётся как
 * {@link ConstructorSymbol}, в {@code .bsl}-модуле — как обычный
 * {@link RegularMethodSymbol}.
 */
@CleanupContextBeforeClassAndAfterClass
class MethodSymbolComputerConstructorTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/internal-classes-test";
  private static final String CLASS_WITH_CTOR =
    FIXTURE_DIR + "/oscript_modules/internal-classes-lib/src/Классы/PublicEntity.os";
  private static final String INTERNAL_CLASS =
    FIXTURE_DIR + "/oscript_modules/internal-classes-lib/src/internal/Классы/InternalEntity.os";
  private static final String CLASS_WITHOUT_CTOR =
    FIXTURE_DIR + "/oscript_modules/internal-classes-lib/src/Классы/ClassWithoutCtor.os";

  @Test
  void osClassWithOnObjectCreateBuildsConstructorSymbol() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);
    var documentContext = TestUtils.getDocumentContextFromFile(CLASS_WITH_CTOR, context);

    // when
    var constructor = documentContext.getSymbolTree().getConstructor();

    // then
    assertThat(constructor)
      .as("у класса с ПриСозданииОбъекта должен быть ConstructorSymbol в symbol tree")
      .isPresent();
    assertThat(constructor.orElseThrow().getName()).isEqualToIgnoringCase("ПриСозданииОбъекта");
  }

  @Test
  void implicitOsClassWithoutExportOnConstructorBuildsConstructorSymbol() {
    // given: InternalEntity.os — implicit-класс из src/internal/Классы, конструктор без `Экспорт`
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);
    var documentContext = TestUtils.getDocumentContextFromFile(INTERNAL_CLASS, context);

    // when
    var constructor = documentContext.getSymbolTree().getConstructor();

    // then
    assertThat(constructor)
      .as("implicit OScript-класс с конструктором без `Экспорт` всё равно должен дать ConstructorSymbol")
      .isPresent();
    assertThat(constructor.orElseThrow().isExport())
      .as("раз в исходнике нет `Экспорт`, ConstructorSymbol.isExport() == false")
      .isFalse();
  }

  @Test
  void osClassWithoutConstructorDoesNotProduceConstructorSymbol() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);
    var documentContext = TestUtils.getDocumentContextFromFile(CLASS_WITHOUT_CTOR, context);

    // when
    var constructor = documentContext.getSymbolTree().getConstructor();

    // then
    assertThat(constructor)
      .as("у класса без ПриСозданииОбъекта не должно быть ConstructorSymbol")
      .isEmpty();
  }

  @Test
  void bslModuleWithSameNamedProcedureProducesRegularMethod() {
    // given: .bsl-файл, в нём процедура с именем ПриСозданииОбъекта — это просто
    // совпадение имени, не семантически конструктор OScript-класса.
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);
    var bslContent = """
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_DOCUMENT_URI, bslContent, context);

    // when
    var symbolTree = dc.getSymbolTree();

    // then
    assertThat(symbolTree.getConstructor())
      .as(".bsl-модуль с процедурой ПриСозданииОбъекта не должен порождать ConstructorSymbol")
      .isEmpty();
    var method = symbolTree.getMethodSymbol("ПриСозданииОбъекта").orElseThrow();
    assertThat(method)
      .as("в .bsl это обычный RegularMethodSymbol")
      .isInstanceOf(RegularMethodSymbol.class);
  }
}
