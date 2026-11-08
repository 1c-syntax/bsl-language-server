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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.*;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SharedPropertyRankingTest {
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE,"String");
  private static final TypeRef BOOLEAN = new TypeRef(TypeKind.PRIMITIVE,"Boolean");
  private record Fixture(DocumentContext document, Position position, CompilationContextResolver resolver,
                         TypeService types, MemberDescriptor first, MemberDescriptor second) {
    CompletionContextEvaluation query() { return new CompletionContextEvaluation(document, position, resolver, types); }
  }

  @Test void knownReceiverKeepsContextSpecificPropertyAvailability() {
    var f=fixture(false,false,false);
    assertSameRanks(f);
    var query=f.query();
    query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.PARTIAL);
    verify(f.types,times(6)).receiverTypesAt(any(),any(),any());
  }

  @ParameterizedTest
  @CsvSource({"true, false, false", "false, true, false", "false, false, true"})
  void alternativesUnknownReceiversAndLocalFieldsRemainAvailable(boolean alternative, boolean unknownClient,
                                                                boolean localField) {
    var f=fixture(alternative,unknownClient,localField);
    assertSameRanks(f);
    var query=f.query();query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.AVAILABLE);
  }

  @Test void ownerAndReturnTypeRestrictionsAreIntersectedInsideTheSameContext() {
    var f=fixture(false,false,false);
    when(f.resolver.typeContexts(f.document,TypeRef.UNDEFINED)).thenReturn(Optional.of(Set.of(ExecutionContext.SERVER)));
    when(f.resolver.typeContexts(f.document,STRING)).thenReturn(Optional.of(Set.of(ExecutionContext.THIN_CLIENT)));
    assertSameRanks(f);
    var query=f.query();query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.UNAVAILABLE);
  }

  @Test void differentKnownReceiversRetainThePerContextPath() {
    var f=fixture(false,false,false);
    when(f.types.receiverTypesAt(f.document,f.position,ExecutionContext.THIN_CLIENT)).thenReturn(TypeSet.of(BOOLEAN));
    when(f.types.getMembers(BOOLEAN,FileType.BSL)).thenReturn(List.of(f.first,member("Second",TypeSet.of(BOOLEAN))));
    assertSameRanks(f);
    var query=f.query();query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.AVAILABLE);
  }

  @Test void anyReceiverRetainsTheConservativePath() {
    var f=fixture(false,false,false);
    when(f.types.receiverTypesAt(f.document,f.position,ExecutionContext.THIN_CLIENT)).thenReturn(TypeSet.of(TypeRef.ANY));
    assertSameRanks(f);
    var query=f.query();query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.AVAILABLE);
  }

  @Test void absentScopeDoesNotRestrictProperties() {
    var f=fixture(false,false,false);
    when(f.resolver.contextAt(f.document,f.position)).thenReturn(Optional.empty());
    assertSameRanks(f);
    var query=f.query();query.rankDotPropertyMember("First",f.first);
    assertThat(query.rankDotPropertyMember("Second",f.second)).isEqualTo(CompletionPriority.AVAILABLE);
  }

  private static void assertSameRanks(Fixture f) {
    var original=f.query();var shared=f.query();
    for(var member:List.of(f.first,f.second)) {
      assertThat(shared.rankDotPropertyMember(member.name(),member))
        .isEqualTo(original.rankDotMember(member.name(),member,null));
    }
  }

  private static Fixture fixture(boolean alternative,boolean unknownClient,boolean localField) {
    var document=mock(DocumentContext.class);when(document.getFileType()).thenReturn(FileType.BSL);
    var position=new Position();var resolver=mock(CompilationContextResolver.class);
    var modes=Set.of(ExecutionContext.SERVER,ExecutionContext.THIN_CLIENT);
    when(resolver.contextAt(document,position)).thenReturn(Optional.of(new CompilationContextResolver.MethodContext(Optional.empty(),modes)));
    when(resolver.automaticContexts(document)).thenReturn(modes);
    when(resolver.typeContexts(document,TypeRef.UNDEFINED)).thenReturn(Optional.of(modes));
    when(resolver.typeContexts(document,STRING)).thenReturn(Optional.of(Set.of(ExecutionContext.SERVER)));
    when(resolver.typeContexts(document,BOOLEAN)).thenReturn(Optional.of(Set.of(ExecutionContext.THIN_CLIENT)));
    var types=mock(TypeService.class);
    when(types.receiverTypesQuery(document,position)).thenReturn(mode -> types.receiverTypesAt(document,position,mode));
    var receiver=TypeSet.of(TypeRef.UNDEFINED);
    if(localField)receiver=receiver.withField(TypeRef.UNDEFINED,"Second",TypeSet.of(BOOLEAN));
    when(types.receiverTypesAt(document,position,ExecutionContext.SERVER)).thenReturn(receiver);
    when(types.receiverTypesAt(document,position,ExecutionContext.THIN_CLIENT)).thenReturn(unknownClient?TypeSet.of(TypeRef.UNKNOWN):receiver);
    var first=member("First",TypeSet.EMPTY);var second=member("Second",TypeSet.of(STRING));
    when(types.getMembers(TypeRef.UNDEFINED,FileType.BSL)).thenReturn(alternative
      ?List.of(first,second,member("Second",TypeSet.of(BOOLEAN))):List.of(first,second));
    return new Fixture(document,position,resolver,types,first,second);
  }

  private static MemberDescriptor member(String name,TypeSet returns) {
    return new MemberDescriptor(BilingualString.of(name),MemberKind.PROPERTY,BilingualString.EMPTY,
      returns,List.of(),null,false,PlatformMetadata.EMPTY);
  }
}
