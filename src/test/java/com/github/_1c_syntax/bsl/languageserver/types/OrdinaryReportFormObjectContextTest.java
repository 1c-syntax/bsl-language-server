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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контекст объекта в модуле обычной формы отчёта: основная и дополнительная формы работают
 * с самим отчётом, поэтому его реквизиты и экспортные переменные модуля объекта доступны в
 * модуле формы без квалификации.
 */
@CleanupContextBeforeClassAndAfterClass
class OrdinaryReportFormObjectContextTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_REPORT = "src/test/resources/metadata/reportSelfMembers";
  private static final String DEFAULT_FORM = "Reports/Отчет1/Forms/ОсновнаяФорма/Ext/Form/Module.bsl";
  private static final String FORM_WITHOUT_ROLE = "Reports/Отчет1/Forms/ДопФорма/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    initServerContext(PATH_TO_REPORT);
  }

  @Test
  void defaultFormSeesObjectAttribute() {
    // given
    var form = reparsed(DEFAULT_FORM);

    // when
    var types = typeService.expressionTypesAt(form, positionOf(form, "ИзРеквизита = Период"));

    // then: реквизит отчёта в модуле его основной формы — без квалификации.
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Строка");
  }

  @Test
  void assignmentToObjectExportVariableCreatesNoVariableInDefaultForm() {
    // given: `мВерсияФормы` объявлена «Перем … Экспорт» в модуле объекта, а в теле модуля
    // формы ей только присваивают.
    var form = reparsed(DEFAULT_FORM);

    // when
    var variables = variablesNamed(form, "мВерсияФормы");

    // then: это запись в переменную отчёта, а не своя переменная формы.
    assertThat(variables).isEmpty();
    assertThat(typeService.isBareSelfProperty(form, "мВерсияФормы")).isTrue();
  }

  @Test
  void formWithoutRoleGetsNoObjectContext() {
    // given: форма не назначена ни основной, ни дополнительной — чьи данные она показывает,
    // из метаданных не видно.
    var form = reparsed(FORM_WITHOUT_ROLE);

    // when
    var variables = variablesNamed(form, "мВерсияФормы");

    // then: как и прежде — контекста объекта у неё нет, присваивание заводит переменную.
    assertThat(variables).extracting(VariableSymbol::getKind).containsExactly(VariableKind.DYNAMIC);
    assertThat(typeService.isBareSelfProperty(form, "Период")).isFalse();
  }

  /**
   * Документ, разобранный заново после наполнения рабочей области. Экспортные переменные
   * модуля объекта становятся членами его типа только после разбора самого модуля, а
   * наполнение разбирает документы в произвольном порядке.
   */
  private DocumentContext reparsed(String relativePath) {
    var uri = Absolute.uri(Path.of(PATH_TO_REPORT, relativePath).toUri());
    var documentContext = context.getDocument(uri);
    assertThat(documentContext).as(relativePath).isNotNull();
    context.rebuildDocument(documentContext);
    return documentContext;
  }

  private static List<VariableSymbol> variablesNamed(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> variable.getName().equalsIgnoreCase(name))
      .toList();
  }

  private static Position positionOf(DocumentContext documentContext, String marker) {
    var lines = documentContext.getContentList();
    for (var line = 0; line < lines.length; line++) {
      var column = lines[line].indexOf(marker);
      if (column >= 0) {
        return new Position(line, column + marker.length() - 1);
      }
    }
    throw new AssertionError("маркер '" + marker + "' не найден");
  }
}
