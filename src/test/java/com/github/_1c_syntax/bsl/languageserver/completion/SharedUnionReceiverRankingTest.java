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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SharedUnionReceiverRankingTest {
  private static final TypeRef A=new TypeRef(TypeKind.CONFIGURATION,"A");
  private static final TypeRef B=new TypeRef(TypeKind.CONFIGURATION,"B");
  private static final Set<ExecutionContext> MODES=Set.of(ExecutionContext.SERVER,ExecutionContext.THIN_CLIENT);
  private record Fixture(DocumentContext caller,Position position,TypeService types,
                         CompilationContextResolver resolver,List<MemberDescriptor> first,
                         List<MemberDescriptor> second,DocumentContext ownerA,DocumentContext ownerB) {
    CompletionContextEvaluation evaluation() {return new CompletionContextEvaluation(caller,position,resolver,types);}
  }

  @Test
  void everyCombinationOfKnownUnknownAndUnavailableCallsMatchesTheContextByContextPath() {
    var choices=CallAvailability.values();
    for(int combination=0;combination<256;combination++) {
      var f=fixture();int code=combination;var cells=new CallAvailability[4];
      for(int i=0;i<cells.length;i++){cells[i]=choices[code%4];code/=4;}
      when(f.resolver().availability(any(),any(),any(),any(),any(),any())).thenAnswer(call->{
        int owner=call.getArgument(2)==f.ownerA()?0:1;
        return cells[owner*2+(call.getArgument(5)==ExecutionContext.SERVER?0:1)];
      });
      compare(f);
    }
  }

  @Test
  void typeRestrictionsAndLocalFieldsDoNotChangeMethodUnionResults() {
    var f=fixture();
    when(f.resolver().typeContexts(f.caller(),A)).thenReturn(Optional.of(Set.of(ExecutionContext.SERVER)));
    var decorated=TypeSet.of(A).union(TypeSet.of(B)).withField(A,"First",TypeSet.of(new TypeRef(TypeKind.PRIMITIVE,"String")));
    for(var mode:MODES)when(f.types().receiverTypesAt(f.caller(),f.position(),mode)).thenReturn(decorated);
    compare(f);
  }

  @Test
  void missingAndMultipleAlternativesKeepTheOriginalFallback() {
    var f=fixture();
    when(f.types().getMembers(B,FileType.BSL)).thenReturn(List.of(f.second().getFirst()));
    compare(f);
    when(f.types().getMembers(B,FileType.BSL)).thenReturn(List.of(f.second().getFirst(),
      f.second().get(1),f.second().get(1)));
    compare(f);
  }

  @Test
  void differentOrUnknownOwnersInAnotherContextKeepTheOriginalPath() {
    var f=fixture();
    for(var receiver:List.of(TypeSet.of(A),TypeSet.of(A).union(TypeSet.of(TypeRef.UNKNOWN)),
      TypeSet.of(A).union(TypeSet.of(TypeRef.ANY)),TypeSet.EMPTY)) {
      when(f.types().receiverTypesAt(f.caller(),f.position(),ExecutionContext.THIN_CLIENT)).thenReturn(receiver);
      compare(f);
    }
  }

  @Test
  void platformAlternativesAndChangesInAnotherRequestRemainVisible() {
    var f=fixture();
    when(f.types().getMembers(B,FileType.BSL)).thenReturn(List.of(MemberDescriptor.method("First"),MemberDescriptor.method("Second")));
    compare(f);
    when(f.resolver().availability(any(),any(),any(),any(),any(),any())).thenReturn(CallAvailability.UNKNOWN);
    compare(f);
  }

  private static void compare(Fixture fixture) {
    var old=fixture.evaluation();var current=fixture.evaluation();
    for(var member:List.of(fixture.first().getFirst(),fixture.first().get(1),fixture.first().get(1))) {
      var expected=new CompletionMethodHint();var actual=new CompletionMethodHint();
      assertThat(current.rankDotSourceMember(member.name(),member,actual))
        .isEqualTo(old.rankDotMember(member.name(),member,expected));
      for(var language:List.of(Language.RU,Language.EN)) {
        for(var labels:List.of(true,false)) {
          var renderer=new CompletionMethodHintRenderer(language,labels);
          var before=new CompletionItem("method");var after=new CompletionItem("method");
          assertThat(renderer.apply(actual,after)).isEqualTo(renderer.apply(expected,before));
          assertThat(after).isEqualTo(before);
        }
      }
    }
  }

  private static Fixture fixture() {
    var caller=mock(DocumentContext.class);var ownerA=mock(DocumentContext.class);var ownerB=mock(DocumentContext.class);
    when(caller.getUri()).thenReturn(URI.create("file:///caller.bsl"));
    when(ownerA.getUri()).thenReturn(URI.create("file:///a.bsl"));
    when(ownerB.getUri()).thenReturn(URI.create("file:///b.bsl"));
    when(caller.getFileType()).thenReturn(FileType.BSL);
    var position=new Position();var resolver=mock(CompilationContextResolver.class);var types=mock(TypeService.class);
    when(types.receiverTypesQuery(caller,position)).thenReturn(mode -> types.receiverTypesAt(caller,position,mode));
    var profile=new CompilationContextResolver.MethodContext(Optional.empty(),MODES);
    var declaration=new CompilationContextResolver.Declaration(Optional.empty(),MODES,true);
    when(resolver.contextAt(caller,position)).thenReturn(Optional.of(profile));
    when(resolver.automaticContexts(caller)).thenReturn(MODES);
    for(var owner:List.of(ownerA,ownerB)) {
      when(resolver.methodContext(owner,Optional.empty())).thenReturn(Optional.of(profile));
      when(resolver.declarations(owner)).thenReturn(Map.of("First",List.of(declaration),"Second",List.of(declaration)));
    }
    when(resolver.typeContexts(caller,A)).thenReturn(Optional.of(MODES));
    when(resolver.typeContexts(caller,B)).thenReturn(Optional.of(MODES));
    when(resolver.availability(any(),any(),any(),any(),any(),any())).thenReturn(CallAvailability.AVAILABLE);
    when(resolver.targetContext(any(),any(),any(),any())).thenReturn(ExecutionContext.SERVER);
    for(var mode:MODES)when(types.receiverTypesAt(caller,position,mode)).thenReturn(TypeSet.of(A).union(TypeSet.of(B)));
    var first=List.of(member(ownerA,"First"),member(ownerA,"Second"));
    var second=List.of(member(ownerB,"First"),member(ownerB,"Second"));
    when(types.getMembers(A,FileType.BSL)).thenReturn(first);when(types.getMembers(B,FileType.BSL)).thenReturn(second);
    return new Fixture(caller,position,types,resolver,first,second,ownerA,ownerB);
  }

  private static MemberDescriptor member(DocumentContext owner,String name) {
    var method=mock(MethodSymbol.class);when(method.getOwner()).thenReturn(owner);when(method.getName()).thenReturn(name);
    return MemberDescriptor.method(name).withSourceSymbol(method);
  }
}
