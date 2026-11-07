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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompilationContextContractsTest extends AbstractServerContextAwareTest {
  @Autowired
  private CompilationContextResolver resolver;
  @Autowired
  private TypeRegistry registry;

  @BeforeEach
  void workspace() {
    initServerContext(TestUtils.PATH_TO_METADATA);
  }

  @ParameterizedTest
  @CsvSource({
    "AT_CLIENT, AT_CLIENT, THIN_CLIENT, AVAILABLE",
    "AT_CLIENT, AT_SERVER, THIN_CLIENT, AVAILABLE",
    "AT_CLIENT, AT_SERVER_NO_CONTEXT, WEB_CLIENT, AVAILABLE",
    "AT_SERVER, AT_CLIENT, SERVER, METHOD_UNAVAILABLE",
    "AT_SERVER_NO_CONTEXT, AT_SERVER, SERVER, METHOD_UNAVAILABLE",
    "AT_SERVER_NO_CONTEXT, AT_CLIENT_AT_SERVER_NO_CONTEXT, SERVER, AVAILABLE",
    "AT_CLIENT_AT_SERVER_NO_CONTEXT, AT_CLIENT, THIN_CLIENT, METHOD_UNAVAILABLE",
    "AT_CLIENT_AT_SERVER_NO_CONTEXT, AT_SERVER, SERVER, METHOD_UNAVAILABLE",
    "AT_SERVER, AT_SERVER_NO_CONTEXT, MOBILE_APPLICATION_SERVER, AVAILABLE"
  })
  void callsRespectFormContextAndServerTransitions(CompilerDirectiveKind caller, CompilerDirectiveKind callee,
                                                  ExecutionContext mode, CallAvailability expected) {
    var document = owner(ModuleType.FormModule, "form");
    var callerProfile = resolver.methodContext(document, Optional.of(caller)).orElseThrow();
    var declaration = new Declaration(Optional.of(callee), ExecutionContext.ALL, true);
    var target = resolver.methodContext(document, declaration.directive());

    assertThat(resolver.availability(document, callerProfile, document, declaration, target, mode)).isEqualTo(expected);
  }

  @Test
  void unavailableModuleUnknownProfileAndExcludedDeclarationHaveDifferentResults() {
    var caller = owner(ModuleType.FormModule, "caller");
    var profile = resolver.methodContext(caller, Optional.of(CompilerDirectiveKind.AT_CLIENT)).orElseThrow();
    var owner = common("server", false, false);
    var declaration = new Declaration(Optional.empty(), ExecutionContext.ALL, true);
    var method = resolver.methodContext(owner, Optional.empty());
    assertThat(resolver.availability(caller, profile, owner, declaration, method, ExecutionContext.THIN_CLIENT))
      .isEqualTo(CallAvailability.MODULE_UNAVAILABLE);
    owner = common("rpc", true, false);
    method = resolver.methodContext(owner, Optional.empty());
    assertThat(resolver.availability(caller, profile, owner, declaration, method, ExecutionContext.THIN_CLIENT))
      .isEqualTo(CallAvailability.AVAILABLE);
    assertThat(resolver.availability(caller, profile, owner,
      new Declaration(Optional.empty(), Set.of(), true), method, ExecutionContext.THIN_CLIENT))
      .isEqualTo(CallAvailability.METHOD_UNAVAILABLE);
    assertThat(resolver.availability(caller, profile, owner,
      new Declaration(Optional.empty(), null, true), method, ExecutionContext.THIN_CLIENT))
      .isEqualTo(CallAvailability.UNKNOWN);
    assertThat(resolver.availability(caller, profile, owner, declaration, Optional.empty(), ExecutionContext.THIN_CLIENT))
      .isEqualTo(CallAvailability.UNKNOWN);
  }

  @ParameterizedTest
  @CsvSource({"THIN_CLIENT, SERVER", "MOBILE_CLIENT, SERVER", "MOBILE_APPLICATION_CLIENT, MOBILE_APPLICATION_SERVER",
    "THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_MANAGED_FILE_SERVER",
    "THICK_CLIENT_ORDINARY_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_SERVER"})
  void formCallsChooseTheCorrespondingServerSide(ExecutionContext callerMode, ExecutionContext targetMode) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    var form = owner(ModuleType.FormModule, "form");
    var server = resolver.methodContext(form, Optional.of(CompilerDirectiveKind.AT_SERVER_NO_CONTEXT)).orElseThrow();

    assertThat(resolver.targetContext(form, form, server, callerMode)).isEqualTo(targetMode);
    assertThat(CompilationContextResolver.isServerTransition(callerMode, targetMode)).isTrue();
    assertThat(CompilationContextResolver.isServerTransition(targetMode, targetMode)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commonModuleExportAndLocalCompilationDetermineServerCalls(boolean localClient) {
    var caller = owner(ModuleType.FormModule, "caller");
    var owner = common("rpc", true, localClient);
    var profile = resolver.methodContext(owner, Optional.empty()).orElseThrow();
    assertThat(resolver.targetContext(caller, owner, profile, ExecutionContext.THIN_CLIENT))
      .isEqualTo(localClient ? ExecutionContext.THIN_CLIENT : ExecutionContext.SERVER);
    assertThat(resolver.targetContext(caller, owner, profile, ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT))
      .isEqualTo(ExecutionContext.THICK_CLIENT_MANAGED_FILE);
    var method = mock(MethodSymbol.class);
    when(method.getOwner()).thenReturn(owner);
    when(method.getCompilerDirectiveKind()).thenReturn(Optional.empty());
    assertThat(resolver.serverCallContexts(method, profile.contexts())).isEmpty();
    when(method.isExport()).thenReturn(true);
    assertThat(resolver.serverCallContexts(method, profile.contexts()).contains(ExecutionContext.THIN_CLIENT))
      .isEqualTo(!localClient);
    assertThat(resolver.serverCallContexts(method, profile.contexts()))
      .doesNotContain(ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT);
  }

  @Test
  void declarationConditionsSurviveReleasedAstAndControlAdvertisedTransitions() {
    var document = form("""
      #If Server Then
      &AtServerNoContext
      Procedure Target() Export
      EndProcedure
      #EndIf
      """);
    var method = document.getSymbolTree().getMethodSymbol("Target").orElseThrow();
    var declared = resolver.declarationContexts(method).orElseThrow();
    assertThat(declared).contains(ExecutionContext.SERVER).doesNotContain(ExecutionContext.THIN_CLIENT);
    assertThat(resolver.serverCallContexts(method, declared)).contains(ExecutionContext.THIN_CLIENT);
    document.freezeComputedData();
    assertThat(context.tryClearDocument(document)).isTrue();
    assertThat(resolver.declarationContexts(method)).contains(declared);
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
  }

  @Test
  void positionsRespectAlternativeBranchesAndRebuilds() {
    var source = """
      &AtClientAtServerNoContext
      Procedure P()
        #If Server Then
          ServerValue = 1;
        #ElsIf WebClient Then
          WebValue = 2;
        #Else
          ClientValue = 3;
        #EndIf
        Outside = 4;
      EndProcedure
      """;
    var document = form(source);
    var node = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_assignment).iterator().next();
    assertThat(resolver.contextAt(document, at(source, "ServerValue")).orElseThrow().contexts())
      .contains(ExecutionContext.SERVER).doesNotContain(ExecutionContext.THIN_CLIENT);
    assertThat(resolver.contextAt(document, at(source, "WebValue")).orElseThrow().contexts())
      .containsExactly(ExecutionContext.WEB_CLIENT);
    assertThat(resolver.contextAt(document, at(source, "ClientValue")).orElseThrow().contexts())
      .contains(ExecutionContext.THIN_CLIENT).doesNotContain(ExecutionContext.SERVER, ExecutionContext.WEB_CLIENT);
    assertThat(resolver.possibleContexts(document, node).orElseThrow())
      .contains(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
    assertThat(resolver.narrowedContextsAt(document, node, at(source, "ServerValue")).orElseThrow())
      .contains(ExecutionContext.SERVER).doesNotContain(ExecutionContext.THIN_CLIENT);
    assertThat(resolver.narrowedContextsAt(document, node, at(source, "Outside"))).isEmpty();
    assertThat(resolver.contextAt(document, at(source, "#If Server"))).isEmpty();
    context.rebuildDocument(document, source.replace("WebClient", "ThinClient"), 1);
    assertThat(resolver.contextAt(document, at(source, "WebValue")).orElseThrow().contexts())
      .containsExactly(ExecutionContext.THIN_CLIENT);
  }

  @Test
  void unknownAndOneScriptConditionsDoNotInventAvailability() {
    var source = "&AtClient\nProcedure P()\n#If Windows Then\nValue = 1;\n#EndIf\nEndProcedure";
    var document = form(source);
    var node = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_assignment).iterator().next();
    assertThat(resolver.contextAt(document, at(source, "Value"))).isEmpty();
    assertThat(resolver.narrowedContextsAt(document, node, at(source, "Value"))).isEmpty();
    var os = TestUtils.getDocumentContext(URI.create("file:///model.os"), source, context);
    assertThat(resolver.contextAt(os, at(source, "Value"))).isEmpty();
    assertThat(resolver.possibleContexts(os, os.getAst())).isEmpty();
    assertThat(resolver.narrowedContextsAt(os, os.getAst(), at(source, "Value"))).isEmpty();
    assertThat(resolver.declarationContexts(os.getSymbolTree().getMethodSymbol("P").orElseThrow())).isEmpty();
  }

  @Test
  void platformAndCommonModuleValuesHaveContextualAvailability() {
    var document = owner(ModuleType.FormModule, "caller");
    var platform = registry.intern(TypeKind.PLATFORM, "ServerOnlyContractType");
    registry.registerTypeMetadata(platform, new PlatformMetadata("", "", List.of(), Set.of(Availability.SERVER), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()), FileType.BSL);
    assertThat(resolver.typeContexts(document, platform).orElseThrow()).contains(ExecutionContext.SERVER)
      .doesNotContain(ExecutionContext.THIN_CLIENT);
    assertThat(resolver.typeContexts(document, TypeRef.ANY)).isEmpty();
    assertThat(resolver.typeContexts(document, TypeRef.UNKNOWN)).isEmpty();
    var server = mock(ServerContext.class);
    when(server.getLanguageServerConfiguration()).thenReturn(context.getLanguageServerConfiguration());
    when(server.findCommonModule("Remote")).thenReturn(Optional.of(
      CommonModule.builder().name("Remote").server(true).serverCall(true).build()));
    when(document.getServerContext()).thenReturn(server);
    assertThat(resolver.typeContexts(document, new TypeRef(TypeKind.CONFIGURATION, "Remote")).orElseThrow())
      .contains(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
    assertThat(resolver.platformContexts(Set.of(Availability.SERVER, Availability.THICK_CLIENT)))
      .contains(ExecutionContext.SERVER, ExecutionContext.THICK_CLIENT_MANAGED_FILE_SERVER);
  }

  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(Path.of(TestUtils.PATH_TO_METADATA)
      .resolve("Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private DocumentContext owner(ModuleType type, String name) {
    var document = mock(DocumentContext.class);
    when(document.getUri()).thenReturn(URI.create("file:///" + name + ".bsl"));
    when(document.getFileType()).thenReturn(FileType.BSL);
    when(document.getModuleType()).thenReturn(type);
    when(document.getMdObject()).thenReturn(Optional.empty());
    when(document.getServerContext()).thenReturn(context);
    return document;
  }

  private DocumentContext common(String name, boolean rpc, boolean client) {
    var document = owner(ModuleType.CommonModule, name);
    when(document.getMdObject()).thenReturn(Optional.of(CommonModule.builder().name(name).server(true)
      .serverCall(rpc).clientManagedApplication(client).build()));
    return document;
  }

  private static Position at(String source, String marker) {
    var before = source.substring(0, source.indexOf(marker));
    return new Position((int) before.chars().filter(ch -> ch == '\n').count(),
      before.length() - before.lastIndexOf('\n') - 1);
  }
}
