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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class FlowConvergenceTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;

  @ParameterizedTest
  @ValueSource(ints = {12, 24, 48})
  void nestedAcyclicBranchesPreserveEveryAssignedType(int depth) {
    var source = "Procedure Check(Condition)\n  Value = 1;\n"
      + "  If Condition Then\n".repeat(depth)
      + "    Value = \"text\";\n"
      + "  Else\n    Value = CurrentDate();\n  EndIf;\n".repeat(depth)
      + "  Result = Value;\nEndProcedure\n";
    var document = TestUtils.getDocumentContext(source, context);
    var line = (int) source.substring(0, source.indexOf("Result =")).lines().count() - 1;
    var position = new Position(line, "  Result = ".length());

    assertThat(typeService.expressionTypesAt(document, position).refs())
      .extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder("Строка", "Дата");
  }
}
