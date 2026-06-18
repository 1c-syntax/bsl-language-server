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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрывает развилку {@link TypeService#definingSymbol} для {@link TypeKind#USER}-типов
 * OneScript: для класса с конструктором цель — символ конструктора, для класса без
 * конструктора — символ-модуль, для не-модульного объявления — сам символ как есть,
 * для платформенного типа — {@code empty}.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeServiceDefiningSymbolTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/autumn-di").toAbsolutePath();

  @Autowired
  private TypeService typeService;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex index;

  private com.github._1c_syntax.bsl.languageserver.context.DocumentContext requestingContext;

  @BeforeEach
  void setup() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
    // Для USER-ветки requestingContext не используется — годится любой документ.
    requestingContext = TestUtils.getDocumentContextFromFile(
      FIXTURE_ROOT.resolve("src/Логгер.os").toString(), context);
  }

  @Test
  void classWithConstructorResolvesToConstructorSymbol() {
    // given: «Логгер» объявляет ПриСозданииОбъекта.
    var typeRef = typeService.resolve("Логгер", FileType.OS).orElseThrow();

    // when
    var symbol = typeService.definingSymbol(typeRef, requestingContext);

    // then
    assertThat(symbol)
      .as("класс с конструктором должен резолвиться в ConstructorSymbol")
      .get()
      .isInstanceOf(ConstructorSymbol.class);
  }

  @Test
  void classWithoutConstructorResolvesToModuleSymbol() {
    // given: «Лог» — класс без ПриСозданииОбъекта (ветка orElse в preferConstructor).
    var typeRef = typeService.resolve("Лог", FileType.OS).orElseThrow();

    // when
    var symbol = typeService.definingSymbol(typeRef, requestingContext);

    // then
    assertThat(symbol)
      .as("класс без конструктора должен резолвиться в ModuleSymbol")
      .get()
      .isInstanceOf(ModuleSymbol.class);
  }

  @Test
  void platformTypeHasNoDefiningSymbol() {
    // given: платформенный «Массив» объявляющего исходник-символа не имеет.
    var typeRef = typeService.resolve("Массив", FileType.OS).orElseThrow();

    // when
    var symbol = typeService.definingSymbol(typeRef, requestingContext);

    // then
    assertThat(symbol)
      .as("платформенный тип не имеет объявляющего символа в исходниках")
      .isEmpty();
  }

  @Test
  void nonModuleDeclarationIsReturnedAsIs() {
    // given: defensive-ветка preferConstructor — объявление типа не ModuleSymbol.
    // На практике registerUserType всегда получает ModuleSymbol, поэтому собираем
    // искусственный USER-тип, объявление которого — метод класса «Лог».
    var logContext = TestUtils.getDocumentContextFromFile(
      FIXTURE_ROOT.resolve("src/Лог.os").toString(), context);
    MethodSymbol method = logContext.getSymbolTree().getMethods().getFirst();
    var typeRef = typeRegistry.registerUserType("ИскусственныйНемодульныйТип", method, FileType.OS);

    // when
    var symbol = typeService.definingSymbol(typeRef, requestingContext);

    // then
    assertThat(symbol)
      .as("не-модульное объявление возвращается без изменений")
      .get()
      .isSameAs(method);
  }
}
