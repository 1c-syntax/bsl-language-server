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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.AssignmentCommentCache;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignmentCommentRequestLocalFastTest {
  @Test
  void sameCommentIsParsedOnceAndLinkedTypesAreResolvedTwice() {
    var tokenizer = new BSLTokenizer("""
      Procedure Probe()
        Value = 1; // см. Remote.Factory
      EndProcedure
      """);
    var assignment = assignmentOf(tokenizer);
    var tokens = new ArrayList<>(tokenizer.getTokens());
    var original = Trees.getTrailingComment(tokens, assignment.getStop()).orElseThrow();
    assertThat(tokens.get(assignment.getStop().getTokenIndex())).isSameAs(assignment.getStop());
    var readText = new AtomicInteger();
    var observed = mock(Token.class, delegatesTo(original));
    doAnswer(call -> { readText.incrementAndGet(); return original.getText(); }).when(observed).getText();
    tokens.set(original.getTokenIndex(), observed);
    var owner = mock(DocumentContext.class);
    when(owner.getFileType()).thenReturn(FileType.BSL);
    var sourceReads = new AtomicInteger();
    doAnswer(call -> { sourceReads.incrementAndGet(); return tokens; }).when(owner).getTokens();

    var index = mock(SymbolTypeIndex.class);
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var text = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var incomplete = new AtomicInteger();
    when(index.linkAwaitsUnparsedModule("Remote.Factory", owner)).thenReturn(false, true);
    when(index.resolveSeeReference("Remote.Factory", owner, FileType.BSL))
      .thenReturn(number).thenAnswer(call -> {
        assertThat(incomplete.get()).isEqualTo(1);
        return text;
      });
    var comments = new AssignmentCommentCache();
    var resolver = new CommentTypeResolver(mock(TypeRegistry.class), index);

    assertThat(resolver.ofAssignment(owner, assignment, incomplete::incrementAndGet, comments)).isEqualTo(number);
    var readsAfterParsing = readText.get();
    assertThat(readsAfterParsing).isPositive();
    assertThat(resolver.ofAssignment(owner, assignment, incomplete::incrementAndGet, comments)).isEqualTo(text);
    assertThat(readText.get()).isEqualTo(readsAfterParsing);
    assertThat(sourceReads.get()).isEqualTo(2);
  }

  @Test
  void changedSnapshotOrCommentTokenDoesNotReuseSyntax() {
    var initial = """
      Procedure Probe()
        Value = 1; // Число -
      EndProcedure
      """;
    var tokenizer = new BSLTokenizer(initial);
    var assignment = assignmentOf(tokenizer);
    var stop = assignment.getStop();
    var current = new ArrayList<>(tokenizer.getTokens());
    var differentSnapshot = new ArrayList<>(new BSLTokenizer(initial.replace("Число", "Строка")).getTokens());
    differentSnapshot.set(stop.getTokenIndex(), stop);
    var owner = mock(DocumentContext.class);
    when(owner.getFileType()).thenReturn(FileType.BSL);
    when(owner.getTokens()).thenReturn(current).thenReturn(differentSnapshot);
    var types = mock(TypeRegistry.class);
    when(types.resolve("Число", FileType.BSL)).thenReturn(Optional.of(new TypeRef(TypeKind.PRIMITIVE, "Число")));
    when(types.resolve("Строка", FileType.BSL)).thenReturn(Optional.of(new TypeRef(TypeKind.PRIMITIVE, "Строка")));
    var resolver = new CommentTypeResolver(types, mock(SymbolTypeIndex.class));
    var comments = new AssignmentCommentCache();

    assertThat(resolver.ofAssignment(owner, assignment, null, comments).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Число");
    assertThat(resolver.ofAssignment(owner, assignment, null, comments).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Строка");
    var original = Trees.getTrailingComment(current, stop).orElseThrow();
    differentSnapshot.set(original.getTokenIndex(), original);
    assertThat(resolver.ofAssignment(owner, assignment, null, comments).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Число");
  }

  @Test
  void syntaxMemoLivesWithTheOriginalRequest() {
    var document = mock(DocumentContext.class);
    var request = InferenceContext.neutral(document);
    var nested = request.nested(document, null);
    var refinement = nested.forRefinement();
    var grandchild = refinement.forRefinement();
    var comments = request.assignmentComments();

    assertThat(nested.assignmentComments()).isSameAs(comments);
    assertThat(refinement.assignmentComments()).isSameAs(comments);
    assertThat(grandchild.assignmentComments()).isSameAs(comments);
    assertThat(refinement.contextualReturns).isNotSameAs(request.contextualReturns);
    assertThat(refinement.flowSession).isNotSameAs(request.flowSession);
    request.contextualReturns.invalidate();
    assertThat(request.assignmentComments()).isSameAs(comments);
    assertThat(InferenceContext.neutral(document).assignmentComments()).isNotSameAs(comments);
  }

  private static BSLParser.AssignmentContext assignmentOf(BSLTokenizer tokenizer) {
    return Trees.findAllRuleNodes(tokenizer.getAst(), BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast).findFirst().orElseThrow();
  }
}
