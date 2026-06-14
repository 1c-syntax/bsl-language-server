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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессы для вычисления {@link SymbolKind} символа метода в {@link MethodSymbolComputer}.
 * <p>
 * Методы модулей без состояния (общий модуль BSL, модуль OneScript) получают
 * {@link SymbolKind#Function}, методы модулей со состоянием (модуль объекта, менеджера
 * и т. п.) — {@link SymbolKind#Method}, конструкторы OneScript-классов —
 * {@link SymbolKind#Constructor}. Вид фиксируется в модели символов, поэтому един
 * для всех потребителей.
 */
class MethodSymbolKindTest extends AbstractServerContextAwareTest {

  /**
   * Метод общего модуля BSL — функция без состояния, поэтому символ метода
   * получает {@link SymbolKind#Function}.
   */
  @Test
  void commonModuleMethodHasFunctionKind() {
    // given — общий модуль конфигурации (модуль без состояния)
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
    context.getConfiguration();
    var documentContext = context
      .getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule)
      .orElseThrow();

    // when
    var method = documentContext.getSymbolTree()
      .getMethodSymbol("НеУстаревшаяФункция")
      .orElseThrow();

    // then
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Function);
  }

  /**
   * Метод модуля объекта BSL — член объекта со состоянием, поэтому символ метода
   * остаётся {@link SymbolKind#Method}.
   */
  @Test
  void objectModuleMethodHasMethodKind() {
    // given — модуль объекта справочника (модуль со состоянием)
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
    context.getConfiguration();
    var documentContext = context
      .getDocument("Catalog.Справочник1", ModuleType.ObjectModule)
      .orElseThrow();

    // when
    var method = documentContext.getSymbolTree()
      .getMethodSymbol("Тест")
      .orElseThrow();

    // then
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }

  /**
   * Метод модуля OneScript — функция без состояния, поэтому символ метода
   * получает {@link SymbolKind#Function}.
   */
  @Test
  void oneScriptModuleMethodHasFunctionKind() {
    // given — модуль OneScript (.os, модуль без состояния)
    var documentContext = TestUtils.getDocumentContext(
      TestUtils.FAKE_OSCRIPT_DOCUMENT_URI,
      """
        Процедура Тест() Экспорт
        КонецПроцедуры""");

    // when
    var method = documentContext.getSymbolTree()
      .getMethodSymbol("Тест")
      .orElseThrow();

    // then
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Function);
  }

  /**
   * Конструктор OneScript-класса остаётся {@link SymbolKind#Constructor}, вычисление
   * вида метода модуля без состояния его не затрагивает.
   */
  @Test
  void oneScriptClassConstructorKeepsConstructorKind() {
    // given — OneScript-класс с процедурой ПриСозданииОбъекта
    var fixtureDir = Path.of("src/test/resources/oscript-libraries/internal-classes-test").toAbsolutePath();
    var classWithCtor = fixtureDir
      .resolve("oscript_modules/internal-classes-lib/src/Классы/PublicEntity.os")
      .toString();
    initServerContext(fixtureDir, true);
    var documentContext = TestUtils.getDocumentContextFromFile(classWithCtor, context);

    // when
    var constructor = documentContext.getSymbolTree().getConstructor().orElseThrow();

    // then
    assertThat(constructor.getSymbolKind()).isEqualTo(SymbolKind.Constructor);
  }

  /**
   * Обычный (не конструктор) метод OneScript-класса — член инстанцируемого объекта
   * со состоянием, поэтому символ метода остаётся {@link SymbolKind#Method}, а не
   * {@link SymbolKind#Function}.
   */
  @Test
  void oneScriptClassMethodHasMethodKind() {
    // given — OneScript-класс (модуль со состоянием) с функцией Echo
    var fixtureDir = Path.of("src/test/resources/oscript-libraries/internal-classes-test").toAbsolutePath();
    var classFile = fixtureDir
      .resolve("oscript_modules/internal-classes-lib/src/Классы/PublicEntity.os")
      .toString();
    initServerContext(fixtureDir, true);
    var documentContext = TestUtils.getDocumentContextFromFile(classFile, context);

    // when
    var method = documentContext.getSymbolTree()
      .getMethodSymbol("Echo")
      .orElseThrow();

    // then
    assertThat(documentContext.getModuleType()).isEqualTo(ModuleType.OScriptClass);
    assertThat(method.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }
}
