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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationTypesProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Значение функции, читающей переменную модуля, не должно зависеть от того, успел ли
 * разобраться модуль, из которого эту переменную заполняют.
 * <p>
 * Переменная модуля {@code Состав} получает таблицу из другого модуля и колонку сверх неё.
 * Её ячейку считает первая же функция, которой она понадобилась, и запоминает на весь
 * документ. Если в этот момент модуль-поставщик ещё не разобран, ячейка выходит неполной —
 * и так же неполно всё, что посчитано на ней, в том числе у следующих функций.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class ModuleVariableCellsParseOrderTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE = Path.of("src/test/resources/metadata/moduleVariableCells").toAbsolutePath();

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void valueOnCellComputedBeforeSupplierIsParsedIsIncomplete() {
    // given: разобран только потребитель — поставщик ещё в очереди. Ячейку переменной
    // посчитала первая функция, `ВесьСостав`, и её значение честно неполное.
    var consumer = parse("Потребитель");
    var whole = inferencer.computeReturnTypes(method(consumer, "ВесьСостав"));

    // when: значение второй функции, читающей ту же переменную.
    var copy = inferencer.computeReturnTypes(method(consumer, "КопияСостава"));

    // then: оно тоже неполное — и будет пересчитано, когда разберётся поставщик. Иначе функция
    // навсегда осталась бы с таблицей без колонок, а досталось ли ей полное значение,
    // решал бы порядок разбора.
    assertThat(whole.incomplete()).isTrue();
    assertThat(copy.incomplete()).isTrue();

    // and: поставщик разобрался — пересчёт в том же контексте берёт уже полное значение, а не
    // ячейку, посчитанную без него.
    parse("Поставщик");
    var recomputed = inferencer.computeReturnTypes(method(consumer, "КопияСостава"));
    assertThat(recomputed.incomplete()).isFalse();
    assertThat(recomputed.types().getElementTypes().getAllFieldNames()).contains("Имя", "ПолноеИмя");
  }

  @Test
  void valueOnCellComputedAfterSupplierIsParsedHasAllColumns() {
    // given
    parse("Поставщик");
    var consumer = parse("Потребитель");

    // when
    var copy = inferencer.computeReturnTypes(method(consumer, "КопияСостава"));

    // then: у строки и колонка поставщика, и добавленная в модуле.
    assertThat(copy.incomplete()).isFalse();
    var rows = copy.types().getElementTypes();
    assertThat(rows.getAllFieldNames()).contains("Имя", "ПолноеИмя");
  }

  @BeforeEach
  void registerConfiguration() {
    initServerContext(FIXTURE, false);
    context.getConfiguration();
    provider.tryRegister();
  }

  private DocumentContext parse(String module) {
    var documentContext = context.addDocument(moduleUri(module));
    context.rebuildDocument(documentContext);
    return documentContext;
  }

  private static MethodSymbol method(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getMethodSymbol(name).orElseThrow();
  }

  private static URI moduleUri(String name) {
    return Absolute.uri(FIXTURE.resolve("CommonModules").resolve(name).resolve("Ext").resolve("Module.bsl").toUri());
  }
}
