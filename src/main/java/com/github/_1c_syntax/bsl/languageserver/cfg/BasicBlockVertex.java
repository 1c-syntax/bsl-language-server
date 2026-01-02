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
package com.github._1c_syntax.bsl.languageserver.cfg;


import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BasicBlockVertex extends CfgVertex {

  private final List<ParserRuleContext> statements = new ArrayList<>();

  public List<ParserRuleContext> statements() {
    return statements;
  }

  public void addStatement(ParserRuleContext statement) {
    statements.add(statement);
  }

  @Override
  public Optional<ParserRuleContext> getAst() {
    if(statements.isEmpty()) {
      return super.getAst();
    }

    return Optional.of(statements.get(0));
  }

  @Override
  public String toString() {
    if (statements.isEmpty())
      return "<empty block>";

    return super.toString();
  }
}
