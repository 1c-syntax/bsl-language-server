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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignmentCommentReadReuseTest extends AbstractServerContextAwareTest {
  @Autowired private ExpressionTypeInferencer inferencer;
  @Autowired private TypeRegistry typeRegistry;

  @Test
  void startupReadinessAndTypesReadTheAssignmentTokensOnce() throws Exception {
    assertSingleRead(false);
  }

  @Test
  void aPopulatedWorkspaceStillReadsTheAssignmentTokensOnce() throws Exception {
    assertSingleRead(true);
  }

  @Test
  void incompleteReferenceIsMarkedBeforeItsTypesAreResolved() throws Exception {
    initServerContext();
    var document = TestUtils.getDocumentContext("""
      Procedure Probe()
        Value = 1; // см. Remote.Factory
      EndProcedure
      """, context);
    var assignment = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast).findFirst().orElseThrow();
    var reads = new AtomicInteger();
    var owner = mock(DocumentContext.class, delegatesTo(document));
    doAnswer(call -> { reads.incrementAndGet(); return document.getTokens(); }).when(owner).getTokens();
    var calculation = new InferenceContext(document, null);
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var index = mock(SymbolTypeIndex.class);
    when(index.linkAwaitsUnparsedModule("Remote.Factory", owner)).thenReturn(true);
    when(index.resolveSeeReference("Remote.Factory", owner, document.getFileType())).thenAnswer(call -> {
      assertThat(calculation.sawMissing).isTrue();
      return number;
    });
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previousPopulation = ReflectionTestUtils.getField(target, "workspacePopulated");
    var previousResolver = ReflectionTestUtils.getField(target, "commentTypeResolver");
    var operation = ExpressionTypeInferencer.class.getDeclaredMethod("typesOfAssignmentComment",
      DocumentContext.class, BSLParser.AssignmentContext.class, InferenceContext.class);
    operation.setAccessible(true);
    ReflectionTestUtils.setField(target, "workspacePopulated", false);
    ReflectionTestUtils.setField(target, "commentTypeResolver", new CommentTypeResolver(typeRegistry, index));
    try {
      assertThat((TypeSet) operation.invoke(target, owner, assignment, calculation)).isEqualTo(number);
      assertThat(calculation.sawMissing).isTrue();
      assertThat(reads.get()).isEqualTo(1);
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", previousPopulation);
      ReflectionTestUtils.setField(target, "commentTypeResolver", previousResolver);
    }
  }

  private void assertSingleRead(boolean populated) throws Exception {
    initServerContext();
    var document = TestUtils.getDocumentContext("""
      Procedure Probe()
        Value = 1;
      EndProcedure
      """, context);
    var assignment = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast).findFirst().orElseThrow();
    var reads = new AtomicInteger();
    var owner = mock(DocumentContext.class, delegatesTo(document));
    doAnswer(call -> {
      reads.incrementAndGet();
      return document.getTokens();
    }).when(owner).getTokens();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previous = ReflectionTestUtils.getField(target, "workspacePopulated");
    var calculation = new InferenceContext(document, null);
    var operation = ExpressionTypeInferencer.class.getDeclaredMethod("typesOfAssignmentComment",
      DocumentContext.class, BSLParser.AssignmentContext.class, InferenceContext.class);
    operation.setAccessible(true);
    ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    try {
      assertThat((TypeSet) operation.invoke(target, owner, assignment, calculation)).isEqualTo(TypeSet.EMPTY);
      assertThat(calculation.sawMissing).isFalse();
      assertThat(reads.get()).isEqualTo(1);
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", previous);
    }
  }
}
