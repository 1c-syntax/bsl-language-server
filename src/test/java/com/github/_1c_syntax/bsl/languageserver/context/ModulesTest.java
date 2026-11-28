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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModulesTest {

  @Test
  void identifierIsThisObjectInBothLanguagesIgnoringCase() {
    assertThat(Modules.isThisObject(identifier("ЭтотОбъект"))).isTrue();
    assertThat(Modules.isThisObject(identifier("этотобъект"))).isTrue();
    assertThat(Modules.isThisObject(identifier("THISOBJECT"))).isTrue();
  }

  @Test
  void otherOrMissingIdentifierIsNotThisObject() {
    assertThat(Modules.isThisObject(identifier("ЭтотОбъектДругой"))).isFalse();
    assertThat(Modules.isThisObject(identifier("Объект"))).isFalse();
    assertThat(Modules.isThisObject((TerminalNode) null)).isFalse();
  }

  private static TerminalNode identifier(String text) {
    return new TerminalNodeImpl(new CommonToken(BSLParser.IDENTIFIER, text));
  }
}
