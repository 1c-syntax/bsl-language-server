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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.MemberLookupSession;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@CleanupContextBeforeClassAndAfterClass
class ReturnBodyMemberLookupReuseTest extends AbstractServerContextAwareTest {
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");
  @Autowired private ExpressionTypeInferencer inferencer;

  @BeforeEach
  void prepareWorkspace() {
    initServerContext();
  }

  @Test
  void aReturnBodyComputationReusesDescriptorSelections() {
    var fixture = fixture();
    var members = new CountingMembers();
    withSource(fixture, members, () -> {
      assertThat(inferencer.computeReturnTypes(fixture.method()).types()).isEqualTo(TypeSet.of(NUMBER));
      assertThat(members.scans.get()).isEqualTo(1);
    });
  }

  @Test
  void anExistingSessionRemainsSharedByTheReturnBodyComputation() {
    var fixture = fixture();
    var members = new CountingMembers();
    try (var scope = new MemberLookupSession().open()) {
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle")).hasSize(1);
      withSource(fixture, members, () -> {
        assertThat(inferencer.computeReturnTypes(fixture.method()).types()).isEqualTo(TypeSet.of(NUMBER));
        assertThat(members.scans.get()).isEqualTo(1);
      });
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      assertThat(members.scans.get()).isEqualTo(1);
    }
  }

  @Test
  void theReturnBodyComputationDoesNotLeaveASessionOnItsThread() {
    var fixture = fixture();
    var members = new CountingMembers();
    withSource(fixture, members, () ->
      assertThat(inferencer.computeReturnTypes(fixture.method()).types()).isEqualTo(TypeSet.of(NUMBER)));
    members.scans.set(0);
    MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
    MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
    assertThat(members.scans.get()).isEqualTo(2);
  }

  @Test
  void tabularRefinementReusesTheSameDescriptorSelection() {
    var members = new CountingMembers();
    var registry = mock(TypeRegistry.class);
    when(registry.getMembers(STRUCTURE, FileType.BSL)).thenReturn(members);
    var refinement = new TableCollectionInference(registry);
    try (var scope = new MemberLookupSession().open()) {
      assertThat(refinement.infer(TypeSet.of(STRUCTURE), "Needle", null, FileType.BSL)).isNull();
      assertThat(refinement.infer(TypeSet.of(STRUCTURE), "Needle", null, FileType.BSL)).isNull();
    }
    assertThat(members.scans.get()).isEqualTo(1);
  }

  @Test
  void aContextualCallReusesSelectionsInsideItsReturnBody() {
    var fixture = fixture("""
      Function Probe(P)
        Return P;
      EndFunction
      Function Caller()
        Return Probe(1);
      EndFunction
      """);
    var document = fixture.method().getOwner();
    var expression = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_expression).stream()
      .map(BSLParser.ExpressionContext.class::cast).filter(node -> node.getText().equals("Probe(1)"))
      .findFirst().orElseThrow();
    var members = new CountingMembers();
    withSource(fixture, members, () -> {
      assertThat(inferencer.infer(ExpressionTreeBuildingVisitor.buildExpressionTree(expression), document, ExecutionContext.THIN_CLIENT))
        .isEqualTo(TypeSet.of(NUMBER));
      assertThat(members.scans.get()).isEqualTo(1);
    });
  }

  @Test
  void documentReindexingSharesSelectionsAcrossItsExportedFunctions() {
    var members = new CountingMembers();
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(any(VariableSymbol.class))).thenAnswer(call -> {
      VariableSymbol variable = call.getArgument(0);
      if (variable.getKind() != VariableKind.PARAMETER) return TypeSet.EMPTY;
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      return TypeSet.of(NUMBER);
    });
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = ReflectionTestUtils.getField(target, "variableTypeSources");
    ReflectionTestUtils.setField(target, "variableTypeSources", List.of(provider));
    try {
      var document = TestUtils.getDocumentContext("""
        Function First(P) Export
          Return P;
        EndFunction
        Function Second(P) Export
          Return P;
        EndFunction
        """, context);
      assertThat(inferencer.inferReturnTypes(document.getSymbolTree().getMethodSymbol("First").orElseThrow()))
        .isEqualTo(TypeSet.of(NUMBER));
      assertThat(inferencer.inferReturnTypes(document.getSymbolTree().getMethodSymbol("Second").orElseThrow()))
        .isEqualTo(TypeSet.of(NUMBER));
      assertThat(members.scans.get()).isEqualTo(1);
      members.scans.set(0);
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      assertThat(members.scans.get()).isEqualTo(2);
    } finally {
      ReflectionTestUtils.setField(target, "variableTypeSources", original);
    }
  }

  private Fixture fixture() {
    return fixture("""
      Function Probe(P)
        Return P;
      EndFunction
      """);
  }

  private Fixture fixture(String source) {
    var document = TestUtils.getDocumentContext(source, context);
    var method = document.getSymbolTree().getMethodSymbol("Probe").orElseThrow();
    var parameter = document.getSymbolTree().getVariables().stream()
      .filter(variable -> variable.getName().equals("P")).findFirst().orElseThrow();
    return new Fixture(method, parameter);
  }

  private void withSource(Fixture fixture, CountingMembers members, Runnable action) {
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenAnswer(call -> {
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Needle");
      return TypeSet.of(NUMBER);
    });
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = ReflectionTestUtils.getField(target, "variableTypeSources");
    ReflectionTestUtils.setField(target, "variableTypeSources", List.of(provider));
    try {
      action.run();
    } finally {
      ReflectionTestUtils.setField(target, "variableTypeSources", original);
    }
  }

  private record Fixture(MethodSymbol method, VariableSymbol parameter) { }

  private static final class CountingMembers extends AbstractList<MemberDescriptor> {
    private final List<MemberDescriptor> values;
    private final AtomicInteger scans = new AtomicInteger();

    private CountingMembers() {
      var members = new ArrayList<MemberDescriptor>();
      for (var index = 0; index < 9; index++) members.add(MemberDescriptor.property("Other" + index));
      members.add(MemberDescriptor.property("Needle", NUMBER));
      values = List.copyOf(members);
    }

    @Override
    public MemberDescriptor get(int index) { return values.get(index); }

    @Override
    public int size() { return values.size(); }

    @Override
    public Iterator<MemberDescriptor> iterator() {
      scans.incrementAndGet();
      return values.iterator();
    }
  }
}
