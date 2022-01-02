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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Сапплаер областей сворачивания импортов библиотек OneScript (<code>#Использовать ...</code>).
 */
@Component
public class UseFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    BSLParser.FileContext fileContext = documentContext.getAst();
    ParseTree[] uses = Trees.findAllRuleNodes(fileContext, BSLParser.RULE_use).toArray(new ParseTree[0]);

    if (uses.length <= 1) {
      return Collections.emptyList();
    }

    int start = ((BSLParser.UseContext) uses[0]).getStart().getLine();
    int stop = ((BSLParser.UseContext) uses[uses.length - 1]).getStop().getLine();

    FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
    foldingRange.setKind(FoldingRangeKind.Imports);

    return Collections.singletonList(foldingRange);
  }

}
