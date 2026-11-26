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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterClass
class PreparedReceiverQueryTest extends AbstractServerContextAwareTest {
  @Autowired private TypeService types;
  private record Fixture(DocumentContext document,Position position,String source) { }

  @Test
  void completeAndDanglingMembersPreserveDecoratedReceiverTypesInEveryMode() {
    for(var member:List.of("A|","|")) {
      var f=fixture("Procedure Probe()\nValue = New Structure(\"A\", New Array);\nResult = Value."+member+";\nEndProcedure\n");
      compareAllModes(f);
      var result=types.receiverTypesQuery(f.document(),f.position()).apply(ExecutionContext.SERVER);
      assertThat(result.refs()).isNotEmpty();
      assertThat(result.refs().stream().anyMatch(ref -> !result.getLocalFields(ref).isEmpty())).isTrue();
    }
  }

  @Test
  void callResultAndPreprocessorBranchesKeepContextSpecificInference() {
    for(var source:List.of(
      "Function Factory()\nReturn New Structure(\"A\",1);\nEndFunction\nProcedure Probe()\nResult = Factory().A|;\nEndProcedure\n",
      "Procedure Probe()\n#If Server Then\nValue = New Structure;\n#Else\nValue = New Array;\n#EndIf\nResult = Value.Count|;\nEndProcedure\n")) {
      compareAllModes(fixture(source));
    }
  }

  @Test
  void positionsWithoutAReceiverKeepTheExistingFallback() {
    for(var source:List.of("Procedure Probe()\nValue| = 1;\nEndProcedure\n",
      "Procedure Probe()\nUnknown.Value|;\nEndProcedure\n")) compareAllModes(fixture(source));
  }

  @Test
  void aPreparedExpressionNeverCachesTheInferredValueEvenInTheSameMode() {
    var f=fixture("Procedure Probe()\nValue = 1;\nResult = Value.A|;\nEndProcedure\n");
    var inference=mock(ExpressionTypeInferencer.class);
    var first=TypeSet.of(new TypeRef(TypeKind.PRIMITIVE,"Number"));
    var second=TypeSet.of(new TypeRef(TypeKind.PRIMITIVE,"String"));
    when(inference.infer(any(),same(f.document()),any())).thenReturn(first,second);
    var service=new TypeService(mock(TypeRegistry.class),mock(SymbolTypeIndex.class),inference,
      mock(ReferenceResolver.class),mock(GlobalScopeProvider.class),mock(DereferenceMemberMatcher.class));
    var query=service.receiverTypesQuery(f.document(),f.position());
    assertThat(query.apply(ExecutionContext.SERVER)).isSameAs(first);
    assertThat(query.apply(ExecutionContext.SERVER)).isSameAs(second);
    verify(inference,times(2)).infer(any(),same(f.document()),same(ExecutionContext.SERVER));
  }

  @Test
  void replacingTheAstAtTheSameVersionRejectsThePreparedExpression() {
    var f=fixture("Procedure Probe()\nValue = New Structure;\nResult = Value.A|;\nEndProcedure\n");
    var query=types.receiverTypesQuery(f.document(),f.position());
    var ast=f.document().getAst();var version=f.document().getVersion();
    context.rebuildDocument(f.document(),f.source().replace("New Structure","New Array"),version);
    assertThat(f.document().getAst()).isNotSameAs(ast);
    assertThat(f.document().getVersion()).isEqualTo(version);
    var expected=types.receiverTypesAt(f.document(),f.position(),ExecutionContext.SERVER);
    assertThat(query.apply(ExecutionContext.SERVER)).isEqualTo(expected);
    assertThat(expected.refs()).extracting(TypeRef::qualifiedName).containsExactly("Массив");
  }

  @Test
  void aPreparedQueryOwnsItsPositionAndSeesLaterDocumentVersions() {
    var f=fixture("Procedure Probe()\nValue = New Structure;\nResult = Value.A|;\nEndProcedure\n");
    var original=new Position(f.position().getLine(),f.position().getCharacter());
    var query=types.receiverTypesQuery(f.document(),f.position());
    f.position().setCharacter(0);
    assertThat(query.apply(ExecutionContext.SERVER))
      .isEqualTo(types.receiverTypesAt(f.document(),original,ExecutionContext.SERVER));
    context.rebuildDocument(f.document(),f.source().replace("New Structure","New Array"),f.document().getVersion()+1);
    assertThat(query.apply(ExecutionContext.SERVER))
      .isEqualTo(types.receiverTypesAt(f.document(),original,ExecutionContext.SERVER));
  }

  private void compareAllModes(Fixture fixture) {
    var query=types.receiverTypesQuery(fixture.document(),fixture.position());
    for(var mode:ExecutionContext.ALL) {
      assertThat(query.apply(mode)).as("receiver in %s",mode)
        .isEqualTo(types.receiverTypesAt(fixture.document(),fixture.position(),mode));
    }
    assertThat(query.apply(null)).isEqualTo(types.receiverTypesAt(fixture.document(),fixture.position(),null));
  }

  private Fixture fixture(String marked) {
    var root=Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root,false);
    int marker=marked.indexOf('|');var prefix=marked.substring(0,marker);
    var position=new Position((int)prefix.chars().filter(c->c=='\n').count(),marker-prefix.lastIndexOf('\n')-1);
    var source=marked.replace("|","");
    var document=TestUtils.getDocumentContext(root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(),source,context);
    context.rebuildDocument(document,source,0);
    return new Fixture(document,position,source);
  }
}
