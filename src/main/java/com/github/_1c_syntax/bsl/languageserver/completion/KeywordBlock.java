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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.parser.BSLLexer;

/** Состояние заголовка и веток одного незавершённого блока BSL. */
final class KeywordBlock {
  private enum Kind {IF, FOR, WHILE, TRY}
  private final Kind kind;
  private boolean body;
  private boolean lastBranch;
  private boolean each;
  private boolean assigned;
  private boolean delimiter;

  private KeywordBlock(Kind kind) {
    this.kind = kind;
    body = kind == Kind.TRY;
  }

  /** Создать блок для одного из четырёх открывающих ключевых слов. */
  static KeywordBlock open(int type) {
    var kind = switch (type) {
      case BSLLexer.IF_KEYWORD -> Kind.IF;
      case BSLLexer.FOR_KEYWORD -> Kind.FOR;
      case BSLLexer.WHILE_KEYWORD -> Kind.WHILE;
      case BSLLexer.TRY_KEYWORD -> Kind.TRY;
      default -> throw new IllegalArgumentException("Not an opening keyword: " + type);
    };
    return new KeywordBlock(kind);
  }

  boolean body() { return body; }
  boolean activeLoop() { return loop() && body; }
  private boolean loop() { return kind == Kind.FOR || kind == Kind.WHILE; }

  boolean closedBy(int type) {
    return switch (type) {
      case BSLLexer.ENDIF_KEYWORD -> kind == Kind.IF;
      case BSLLexer.ENDDO_KEYWORD -> loop();
      case BSLLexer.ENDTRY_KEYWORD -> kind == Kind.TRY;
      default -> false;
    };
  }

  void accept(int type) {
    switch (kind) {
      case IF -> acceptIf(type);
      case FOR -> acceptFor(type);
      case WHILE -> acceptWhile(type);
      case TRY -> acceptTry(type);
    }
  }

  private void acceptIf(int type) {
    switch (type) {
      case BSLLexer.THEN_KEYWORD -> body = true;
      case BSLLexer.ELSIF_KEYWORD -> body = false;
      case BSLLexer.ELSE_KEYWORD -> {
        body = true;
        lastBranch = true;
      }
      default -> { /* остальные токены не меняют ветку */ }
    }
  }

  private void acceptFor(int type) {
    if (type == BSLLexer.DO_KEYWORD) {
      body = true;
      return;
    }
    if (body) {
      return;
    }
    switch (type) {
      case BSLLexer.EACH_KEYWORD -> each = true;
      case BSLLexer.ASSIGN -> assigned = true;
      case BSLLexer.TO_KEYWORD, BSLLexer.IN_KEYWORD -> delimiter = true;
      default -> { /* выражения заголовка не меняют его разделители */ }
    }
  }

  private void acceptWhile(int type) {
    if (type == BSLLexer.DO_KEYWORD) { body = true; }
  }

  private void acceptTry(int type) {
    if (type == BSLLexer.EXCEPT_KEYWORD) {
      body = true;
      lastBranch = true;
    }
  }

  /** Применимо ли ключевое слово к этому блоку и уже прочитанной части заголовка. */
  boolean allows(int type, int previous, boolean expressionEnd) {
    return switch (kind) {
      case IF -> allowsIf(type, expressionEnd);
      case FOR -> allowsFor(type, previous, expressionEnd);
      case WHILE -> allowsWhile(type, expressionEnd);
      case TRY -> allowsTry(type);
    };
  }

  private boolean allowsIf(int type, boolean expressionEnd) {
    return switch (type) {
      case BSLLexer.ELSE_KEYWORD, BSLLexer.ELSIF_KEYWORD -> body && !lastBranch;
      case BSLLexer.ENDIF_KEYWORD -> body;
      case BSLLexer.THEN_KEYWORD -> !body && expressionEnd;
      default -> false;
    };
  }

  private boolean allowsFor(int type, int previous, boolean expressionEnd) {
    return switch (type) {
      case BSLLexer.EACH_KEYWORD -> !body && previous == BSLLexer.FOR_KEYWORD;
      case BSLLexer.IN_KEYWORD -> allowsIn(previous);
      case BSLLexer.TO_KEYWORD -> allowsTo(expressionEnd);
      case BSLLexer.DO_KEYWORD -> !body && delimiter && expressionEnd;
      case BSLLexer.ENDDO_KEYWORD -> body;
      default -> false;
    };
  }

  private boolean allowsIn(int previous) {
    return !body && each && !delimiter && previous == BSLLexer.IDENTIFIER;
  }

  private boolean allowsTo(boolean expressionEnd) {
    return !body && !each && assigned && !delimiter && expressionEnd;
  }

  private boolean allowsWhile(int type, boolean expressionEnd) {
    return switch (type) {
      case BSLLexer.ENDDO_KEYWORD -> body;
      case BSLLexer.DO_KEYWORD -> !body && expressionEnd;
      default -> false;
    };
  }

  private boolean allowsTry(int type) {
    return switch (type) {
      case BSLLexer.EXCEPT_KEYWORD -> !lastBranch;
      case BSLLexer.ENDTRY_KEYWORD -> lastBranch;
      default -> false;
    };
  }
}
