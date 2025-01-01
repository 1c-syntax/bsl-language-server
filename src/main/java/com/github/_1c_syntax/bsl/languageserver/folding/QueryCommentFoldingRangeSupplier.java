/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import com.github._1c_syntax.bsl.parser.Tokenizer;
import org.antlr.v4.runtime.Token;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сапплаер областей сворачивания блоков комментариев в тексте запроса.
 */
@Component
public class QueryCommentFoldingRangeSupplier extends AbstractCommentFoldingRangeSupplier {

  @Override
  protected List<Token> getComments(DocumentContext documentContext) {
    return documentContext.getQueries().stream()
      .map(Tokenizer::getTokens)
      .flatMap(Collection::stream)
      .filter(token -> token.getType() == SDBLLexer.LINE_COMMENT)
      .sorted(Comparator.comparing(Token::getLine))
      .collect(Collectors.toList());
  }

}
