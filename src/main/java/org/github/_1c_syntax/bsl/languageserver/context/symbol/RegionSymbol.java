/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver.context.symbol;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder(access = AccessLevel.PUBLIC)
public class RegionSymbol implements Symbol {
  private final String name;
  private final BSLParser.RegionNameContext nameNode;

  private final BSLParser.RegionStartContext startNode;
  private final int startLine;

  private final BSLParser.RegionEndContext endNode;
  private final int endLine;

  @Singular
  private final List<RegionSymbol> children;
  private final List<MethodSymbol> methods = new ArrayList<>();

  private final BSLParserRuleContext node;
}
