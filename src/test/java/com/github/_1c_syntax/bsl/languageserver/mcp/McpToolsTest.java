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
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.AnalyzeFileTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.CallHierarchyTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.DefinitionTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.DocumentSymbolsTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.FindReferencesTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.GlobalMemberInfoTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.GlobalMethodsTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.HoverTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.TypeAtPositionTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.TypeInfoTool;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import io.modelcontextprotocol.spec.McpSchema.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Проверяет MCP-инструменты поверх общего {@code ServerContextProvider}.
 * MCP-сервер не поднимается (autoconfig выключен) — тестируются сами бины инструментов.
 */
@SpringBootTest(properties = {
  "spring.ai.mcp.server.enabled=false",
  "spring.ai.mcp.server.annotation-scanner.enabled=false",
  "spring.main.web-application-type=none"
})
@ActiveProfiles("mcp")
@CleanupContextBeforeClassAndAfterEachTestMethod
class McpToolsTest {

  private static final String SRC_DIR = "src/test/resources/providers";
  private static final String FILE = SRC_DIR + "/callHierarchy.bsl";
  private static final String WORKSPACE_ROOT = Absolute.path(SRC_DIR).toUri().toString();

  // Объявление ПерваяФункция и место её вызова в callHierarchy.bsl.
  private static final int DECLARATION_LINE = 6;
  private static final int DECLARATION_CHARACTER = 10;
  private static final int CALL_LINE = 1;
  private static final int CALL_CHARACTER = 15;

  @Autowired
  private McpWorkspaceBootstrap workspaceBootstrap;
  @Autowired
  private AnalyzeFileTool analyzeFileTool;
  @Autowired
  private DocumentSymbolsTool documentSymbolsTool;
  @Autowired
  private FindReferencesTool findReferencesTool;
  @Autowired
  private CallHierarchyTool callHierarchyTool;
  @Autowired
  private HoverTool hoverTool;
  @Autowired
  private DefinitionTool definitionTool;
  @Autowired
  private TypeInfoTool typeInfoTool;
  @Autowired
  private TypeAtPositionTool typeAtPositionTool;
  @Autowired
  private GlobalMemberInfoTool globalMemberInfoTool;
  @Autowired
  private GlobalMethodsTool globalMethodsTool;
  @Autowired
  private McpRootsChangeConsumer rootsChangeConsumer;

  @BeforeEach
  void indexWorkspace() {
    workspaceBootstrap.index(Absolute.path(SRC_DIR));
  }

  @Test
  void analyzeFileReturnsDiagnosticsList() {
    var result = analyzeFileTool.analyzeFile(FILE);

    assertThat(result.file()).isEqualTo(FILE);
    assertThat(result.diagnostics()).isNotNull();
    assertThat(result.diagnosticsCount()).isEqualTo(result.diagnostics().size());
  }

  @Test
  void documentSymbolsReturnsSymbolTree() {
    var result = documentSymbolsTool.documentSymbols(FILE);

    assertThat(result.file()).isEqualTo(FILE);
    assertThat(result.symbols()).isNotEmpty();
  }

  @Test
  void findReferencesResolvesMethodUsages() {
    var result = findReferencesTool.findReferences(FILE, DECLARATION_LINE, DECLARATION_CHARACTER);

    assertThat(result.referencesCount()).isPositive();
    assertThat(result.references()).hasSize(result.referencesCount());
  }

  @Test
  void callHierarchyReturnsIncomingAndOutgoingCalls() {
    var result = callHierarchyTool.callHierarchy(FILE, DECLARATION_LINE, DECLARATION_CHARACTER);

    assertThat(result.target()).isNotNull();
    assertThat(result.target().name()).isEqualTo("ПерваяФункция");
    assertThat(result.incoming()).isNotEmpty();
    assertThat(result.outgoing()).isNotEmpty();
  }

  @Test
  void hoverReturnsMarkdownForSymbol() {
    var result = hoverTool.hover(FILE, CALL_LINE, CALL_CHARACTER);

    assertThat(result.contents()).isNotNull().contains("ПерваяФункция");
    assertThat(result.range()).isNotNull();
  }

  @Test
  void hoverReturnsEmptyWhenNoSymbolAtPosition() {
    // Строка 5 (0-based) — пустая строка между процедурами: подсказки нет.
    var result = hoverTool.hover(FILE, 5, 0);

    assertThat(result.contents()).isNull();
    assertThat(result.range()).isNull();
  }

  @Test
  void definitionResolvesToDeclaration() {
    var result = definitionTool.definition(FILE, CALL_LINE, CALL_CHARACTER);

    assertThat(result.definitions()).isNotEmpty();
    assertThat(result.definitions().get(0).selectionRange().startLine()).isEqualTo(DECLARATION_LINE);
  }

  @Test
  void typeInfoReturnsMethodsAndPropertiesOfPlatformType() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.name()).isEqualTo("Массив");
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Добавить", "Количество");
  }

  @Test
  void typeInfoReturnsMethodsAndPropertiesWithOsFileType() {
    var result = typeInfoTool.typeInfo("Массив", FileType.OS, WORKSPACE_ROOT, null);

    assertThat(result.name()).isEqualTo("Массив");
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Добавить", "Количество");
  }

  @Test
  void typeInfoThrowsForUnknownType() {
    assertThatThrownBy(() -> typeInfoTool.typeInfo("НетТакогоТипа", FileType.BSL, WORKSPACE_ROOT, null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void typeInfoReturnsConstructorsForPlatformClass() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.constructors()).isNotEmpty();
    assertThat(result.constructors().get(0).parameters()).isNotNull();
  }

  @Test
  void typeInfoExposesEventsListEvenIfEmpty() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.events()).isNotNull();
    // У стандартных коллекций событий нет — но поле всегда присутствует.
    assertThat(result.events()).allSatisfy(event -> assertThat(event.kind()).isEqualTo("EVENT"));
  }

  @Test
  void typeInfoAcceptsExplicitLanguageParameter() {
    var ru = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, Language.RU);
    var en = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, Language.EN);

    assertThat(ru.methods()).isNotEmpty();
    assertThat(en.methods()).isNotEmpty();
    // В обоих локалях имя типа корректное (хоть оно может быть только в одной локали).
    assertThat(ru.name()).isNotBlank();
    assertThat(en.name()).isNotBlank();
  }

  @Test
  void typeInfoReturnsNullDefinedAtForPlatformType() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.definedAt()).isNull();
  }

  @Test
  void globalMemberInfoResolvesPlatformFunction() {
    var result = globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
    assertThat(result.member().kind()).isEqualTo("METHOD");
    assertThat(result.member().name()).isEqualTo("Сообщить");
    assertThat(result.member().signatures()).isNotNull();
  }

  @Test
  void globalMemberInfoResolvesByEnglishAlias() {
    var result = globalMemberInfoTool.globalMemberInfo("Message", FileType.BSL, WORKSPACE_ROOT, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
    assertThat(result.member().kind()).isEqualTo("METHOD");
  }

  @Test
  void globalMemberInfoThrowsForUnknownName() {
    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("НетТакогоИмени", FileType.BSL, WORKSPACE_ROOT, null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void globalMemberInfoAcceptsOscriptFileType() {
    var result = globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.OS, WORKSPACE_ROOT, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
  }

  @Test
  void globalMethodsListsGlobalContextFunctions() {
    var result = globalMethodsTool.globalMethods(FileType.BSL, WORKSPACE_ROOT, null, null);

    assertThat(result.count()).isEqualTo(result.methods().size());
    assertThat(result.methods()).isNotEmpty();
    assertThat(result.methods()).allSatisfy(method -> assertThat(method.kind()).isEqualTo("METHOD"));
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Сообщить");
  }

  @Test
  void globalMethodsFiltersByNameSubstring() {
    var result = globalMethodsTool.globalMethods(FileType.BSL, WORKSPACE_ROOT, "Сообщ", null);

    assertThat(result.methods()).isNotEmpty();
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Сообщить");
    assertThat(result.methods()).allSatisfy(method ->
      assertThat(method.name().toLowerCase()).contains("сообщ"));
  }

  @Test
  void globalMethodsFiltersByEnglishSubstring() {
    var unfiltered = globalMethodsTool.globalMethods(FileType.BSL, WORKSPACE_ROOT, null, null);
    var filtered = globalMethodsTool.globalMethods(FileType.BSL, WORKSPACE_ROOT, "Message", null);

    // EN-подстрока сужает выборку относительно полного списка, но что-то находит.
    assertThat(filtered.methods()).isNotEmpty();
    assertThat(filtered.count()).isLessThan(unfiltered.count());
  }

  @Test
  void globalMethodsReturnsEmptyForUnmatchedFilter() {
    var result = globalMethodsTool.globalMethods(FileType.BSL, WORKSPACE_ROOT, "НетТакогоМетода", null);

    assertThat(result.count()).isZero();
    assertThat(result.methods()).isEmpty();
  }

  @Test
  void globalMethodsAcceptsOscriptFileType() {
    var result = globalMethodsTool.globalMethods(FileType.OS, WORKSPACE_ROOT, null, null);

    assertThat(result.methods()).isNotEmpty();
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Сообщить");
  }

  @Test
  void globalMethodsThrowsWhenRootIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> globalMethodsTool.globalMethods(FileType.BSL, unknownRoot, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace matches root");
  }

  @Test
  void globalMethodsThrowsWhenRootIsMissing() {
    assertThatThrownBy(() -> globalMethodsTool.globalMethods(FileType.BSL, null, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace root is required");
  }

  @Test
  void typeInfoThrowsWhenRootIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, unknownRoot, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace matches root");
  }

  @Test
  void globalMemberInfoThrowsWhenRootIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, unknownRoot, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace matches root");
  }

  @Test
  void typeInfoThrowsWhenRootIsMissing() {
    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, "  ", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace root is required");
  }

  @Test
  void globalMemberInfoThrowsWhenRootIsMissing() {
    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace root is required");
  }

  @Test
  void typeAtPositionInfersNewExpressionType() {
    var typesFile = "src/test/resources/mcp/types.bsl";
    workspaceBootstrap.index(Absolute.path("src/test/resources/mcp"));

    // Позиция на `Новый Массив` (строка 1, символ 19 — начало слова «Массив»).
    var result = typeAtPositionTool.typeAtPosition(typesFile, 1, 20);

    assertThat(result.types()).contains("Массив");
    assertThat(result.members()).extracting(TypeMemberDto::name).contains("Добавить");
  }

  @Test
  void mcpRootsRegisterAndRemoveWorkspaces() {
    var cliDir = Absolute.path("src/test/resources/cli");
    var root = new Root(cliDir.toUri().toString(), "cli");

    // Client declares a root -> the directory is indexed as a workspace.
    rootsChangeConsumer.accept(null, List.of(root));

    var analysis = analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl");
    assertThat(analysis.diagnostics()).isNotEmpty();

    // Root removed -> workspace is gone, the file is no longer part of a registered workspace.
    rootsChangeConsumer.accept(null, List.of());

    assertThatThrownBy(() -> analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl"))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
