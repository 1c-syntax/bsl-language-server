/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CallHierarchyProviderTest {

  @Autowired
  private CallHierarchyProvider provider;

  private DocumentContext documentContext;

  private final Position firstProcedureDeclarationPosition = new Position(0, 15);
  private final Position firstFunctionCallPosition = new Position(1, 15);
  private final Position secondFunctionDeclarationPosition = new Position(14, 15);
  private final Position secondFunctionCallPosition = new Position(2, 15);

  @BeforeEach
  void init() {
    String filePath = "./src/test/resources/providers/callHierarchy.bsl";
    documentContext = TestUtils.getDocumentContextFromFile(filePath);
  }

  @Test
  void testPrepareHierarchyEmptyCall() {

    // given
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());
    var emptyPosition = new Position();

    CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
    params.setTextDocument(textDocument);
    params.setPosition(emptyPosition);

    // when
    List<CallHierarchyItem> items = provider.prepareCallHierarchy(documentContext, params);

    // then
    assertThat(items).isEmpty();

  }

  @Test
  void testPrepareHierarchyLocalMethodCall() {

    // given
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
    params.setTextDocument(textDocument);
    params.setPosition(firstFunctionCallPosition);

    // when
    List<CallHierarchyItem> items = provider.prepareCallHierarchy(documentContext, params);

    // then
    assertThat(items)
      .hasSize(1)
      .allMatch(callHierarchyItem -> callHierarchyItem.getName().equals("ПерваяФункция"))
      .allMatch(callHierarchyItem -> callHierarchyItem.getKind().equals(SymbolKind.Method))
      .allMatch(callHierarchyItem -> callHierarchyItem.getTags().isEmpty())
    ;

  }

  @Test
  void testPrepareHierarchyLocalDeprecatedMethodCall() {

    // given
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
    params.setTextDocument(textDocument);
    params.setPosition(secondFunctionCallPosition);

    // when
    List<CallHierarchyItem> items = provider.prepareCallHierarchy(documentContext, params);

    // then
    assertThat(items)
      .hasSize(1)
      .allMatch(callHierarchyItem -> callHierarchyItem.getName().equals("ВтораяФункция"))
      .allMatch(callHierarchyItem -> callHierarchyItem.getKind().equals(SymbolKind.Method))
      .allMatch(callHierarchyItem -> callHierarchyItem.getTags().contains(SymbolTag.Deprecated))
    ;

  }

  @Test
  void testPrepareHierarchyOnDeclaration() {

    // given
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
    params.setTextDocument(textDocument);
    params.setPosition(firstProcedureDeclarationPosition);

    // when
    List<CallHierarchyItem> items = provider.prepareCallHierarchy(documentContext, params);

    // then
    assertThat(items)
      .hasSize(1)
      .allMatch(callHierarchyItem -> callHierarchyItem.getName().equals("ПерваяПроцедура"))
      .allMatch(callHierarchyItem -> callHierarchyItem.getKind().equals(SymbolKind.Method))
      .allMatch(callHierarchyItem -> callHierarchyItem.getTags().isEmpty())
    ;

  }

  @Test
  void testIncomingCallsLocalMethodCall() {

    // given
    var item = getCallHierarchyItem(firstFunctionCallPosition);
    var params = new CallHierarchyIncomingCallsParams(item);

    // when
    List<CallHierarchyIncomingCall> incomingCalls = provider.incomingCalls(documentContext, params);

    // then
    assertThat(incomingCalls)
      .hasSize(2);

    assertThat(incomingCalls)
      .filteredOn(incomingCall -> incomingCall.getFrom().getName().equals("ПерваяПроцедура"))
      .flatExtracting(CallHierarchyIncomingCall::getFromRanges)
      .hasSize(2)
    ;

    assertThat(incomingCalls)
      .filteredOn(incomingCall -> incomingCall.getFrom().getName().equals("ВтораяПроцедура"))
      .flatExtracting(CallHierarchyIncomingCall::getFromRanges)
      .hasSize(1)
    ;
  }

  @Test
  void testIncomingCallsToMethodCalledFromModuleEntry() {

    // given
    var item = getCallHierarchyItem(secondFunctionDeclarationPosition);
    var params = new CallHierarchyIncomingCallsParams(item);

    // when
    List<CallHierarchyIncomingCall> incomingCalls = provider.incomingCalls(documentContext, params);

    // then
    assertThat(incomingCalls)
      .hasSize(3);

    assertThat(incomingCalls)
      .filteredOn(incomingCall -> incomingCall.getFrom().getName().equals("ПерваяПроцедура"))
      .flatExtracting(CallHierarchyIncomingCall::getFromRanges)
      .hasSize(1)
    ;

    assertThat(incomingCalls)
      .filteredOn(incomingCall -> incomingCall.getFrom().getName().equals("ПерваяФункция"))
      .flatExtracting(CallHierarchyIncomingCall::getFromRanges)
      .hasSize(1)
    ;

    assertThat(incomingCalls)
      .filteredOn(incomingCall -> incomingCall.getFrom().getKind().equals(SymbolKind.Module))
      .flatExtracting(CallHierarchyIncomingCall::getFromRanges)
      .hasSize(1)
    ;
  }

  @Test
  void testOutgoingCallsLocalMethodCall() {

    // given
    var item = getCallHierarchyItem(firstFunctionCallPosition);
    var params = new CallHierarchyOutgoingCallsParams(item);

    // when
    List<CallHierarchyOutgoingCall> outgoingCalls = provider.outgoingCalls(documentContext, params);

    // then
    assertThat(outgoingCalls)
      .hasSize(2);

    assertThat(outgoingCalls)
      .filteredOn(outgoingCall -> outgoingCall.getTo().getName().equals("ВтораяПроцедура"))
      .flatExtracting(CallHierarchyOutgoingCall::getFromRanges)
      .hasSize(2)
    ;
  }

  @Test
  void testOutgoingCallsOnMethodDeclaration() {

    // given
    var item = getCallHierarchyItem(firstProcedureDeclarationPosition);
    var params = new CallHierarchyOutgoingCallsParams(item);

    // when
    List<CallHierarchyOutgoingCall> outgoingCalls = provider.outgoingCalls(documentContext, params);

    // then
    assertThat(outgoingCalls)
      .hasSize(2);

    assertThat(outgoingCalls)
      .filteredOn(outgoingCall -> outgoingCall.getTo().getName().equals("ПерваяФункция"))
      .flatExtracting(CallHierarchyOutgoingCall::getFromRanges)
      .hasSize(2)
    ;

    assertThat(outgoingCalls)
      .filteredOn(outgoingCall -> outgoingCall.getTo().getName().equals("ВтораяФункция"))
      .flatExtracting(CallHierarchyOutgoingCall::getFromRanges)
      .hasSize(1)
    ;

  }

  private CallHierarchyItem getCallHierarchyItem(Position position) {
    var textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
    params.setTextDocument(textDocument);
    params.setPosition(position);

    List<CallHierarchyItem> items = provider.prepareCallHierarchy(documentContext, params);

    return items.get(0);
  }
}