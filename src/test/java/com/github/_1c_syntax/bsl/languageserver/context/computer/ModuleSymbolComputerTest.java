/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ModuleSymbolComputerTest {

  @Test
  void testBasicCompute() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/ModuleSymbol.bsl");

    var computer = new ModuleSymbolComputer(documentContext);

    // when
    var moduleSymbol = computer.compute();

    // then
    assertThat(moduleSymbol.getOwner()).isEqualTo(documentContext);
    assertThat(moduleSymbol.getSymbolKind()).isEqualTo(SymbolKind.Module);
    assertThat(moduleSymbol.getName()).isEqualTo(documentContext.getUri().toString());
    assertThat(moduleSymbol.getSelectionRange()).isEqualTo(Ranges.create(0, 0, 0, 9));
    assertThat(Ranges.containsRange(moduleSymbol.getRange(), moduleSymbol.getSelectionRange())).isTrue();
  }

  @Test
  void testModuleName() {
    // given
    var documentContext = spy(TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/ModuleSymbol.bsl"));
    var computer = new ModuleSymbolComputer(documentContext);

    var mdoReference = mock(MdoReference.class);
    when(mdoReference.getMdoRef()).thenReturn("Document.Document1");

    AbstractMDObjectBase mdObject = mock(AbstractMDObjectBase.class);
    when(mdObject.getMdoReference()).thenReturn(mdoReference);

    doReturn(Optional.of(mdObject)).when(documentContext).getMdObject();

    // when-then pairs:

    // when
    doReturn(ModuleType.UNKNOWN).when(documentContext).getModuleType();
    var moduleSymbol = computer.compute();

    // then
    assertThat(moduleSymbol.getName()).isEqualTo("Document.Document1");

    // when
    doReturn(ModuleType.ObjectModule).when(documentContext).getModuleType();
    moduleSymbol = computer.compute();

    // then
    assertThat(moduleSymbol.getName()).isEqualTo("Document.Document1.ObjectModule");

    // when
    doReturn(ModuleType.ManagerModule).when(documentContext).getModuleType();
    moduleSymbol = computer.compute();

    // then
    assertThat(moduleSymbol.getName()).isEqualTo("Document.Document1.ManagerModule");
  }
}
