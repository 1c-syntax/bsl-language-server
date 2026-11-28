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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Скомпилированные охраняющие условия запоминаются по документу, а имя типа в проверке
 * {@code ТипЗнч(Х) = Тип("…")} разрешается при компиляции. Поэтому после регистрации
 * конфигурационных типов условия компилируются заново.
 */
@CleanupContextBeforeClassAndAfterClass
class GuardConditionNarrowingCacheTest extends AbstractServerContextAwareTest {

  @Autowired
  private GuardConditionNarrowing guardConditionNarrowing;

  @Test
  void conditionsAreRecompiledAfterConfigurationTypesRegistration() {
    // given — условие скомпилировано и запомнено
    initServerContext();
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест(Значение)
        Если Значение <> Неопределено Тогда
          Внутри = Значение;
        КонецЕсли;
      КонецПроцедуры
      """, context);
    var ifBranch = (BSLParser.IfBranchContext) Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_ifBranch)
      .iterator().next();
    var condition = ifBranch.expression();
    var compiled = guardConditionNarrowing.compile(condition, documentContext);
    assertThat(compiled).isNotSameAs(GuardConditionNarrowing.CompiledGuard.NONE);
    assertThat(guardConditionNarrowing.compile(condition, documentContext)).isSameAs(compiled);

    // when — зарегистрированы конфигурационные типы
    guardConditionNarrowing.handleConfigurationTypesRegistered(new ConfigurationTypesRegisteredEvent(context));

    // then — условие компилируется заново, а не берётся из запомненного
    var recompiled = guardConditionNarrowing.compile(condition, documentContext);
    assertThat(recompiled).isNotSameAs(compiled);

    // when — рабочая область наполнена (индекс ссылок полон)
    guardConditionNarrowing.handleServerContextPopulated(
      new com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent(context));

    // then — и после этого условие компилируется заново
    assertThat(guardConditionNarrowing.compile(condition, documentContext)).isNotSameAs(recompiled);
  }
}
