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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.references.ReferencesOptions;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonModuleByNameInferenceTest {

  @Test
  void accessorsFollowSettingsChangedInPlaceAndReplaced() {
    // given
    var options = new ReferencesOptions();
    options.setCommonModuleAccessors(new ArrayList<>(List.of("ОбщийМодуль")));
    var configuration = mock(LanguageServerConfiguration.class);
    when(configuration.getReferencesOptions()).thenReturn(options);
    var moduleRef = new TypeRef(TypeKind.CONFIGURATION, "ОбщийМодуль.Модуль1");
    var typeRegistry = mock(TypeRegistry.class);
    when(typeRegistry.resolve("Модуль1", FileType.BSL)).thenReturn(Optional.of(moduleRef));
    var inference = new CommonModuleByNameInference(configuration, typeRegistry);
    var call = callOf("А = ОбщийМодуль(\"Модуль1\");");

    // when, then: метод доступа из настроек распознаётся
    assertThat(inference.localCallTypes(call, FileType.BSL)).isEqualTo(TypeSet.of(moduleRef));

    // список поменяли на месте — разобранный по старому списку больше не годится
    options.getCommonModuleAccessors().set(0, "ДругойМетод");
    assertThat(inference.localCallTypes(call, FileType.BSL)).isNull();

    // список заменили целиком
    options.setCommonModuleAccessors(List.of("ОбщийМодуль"));
    assertThat(inference.localCallTypes(call, FileType.BSL)).isEqualTo(TypeSet.of(moduleRef));
  }

  private static MethodCallNode callOf(String code) {
    var ast = new BSLTokenizer(code).getAst();
    var expression = Trees.findAllRuleNodes(ast, BSLParser.RULE_expression).stream()
      .map(BSLParser.ExpressionContext.class::cast)
      .findFirst()
      .orElseThrow();
    return (MethodCallNode) ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
  }
}
