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
import com.github._1c_syntax.bsl.languageserver.mcp.tools.GlobalMemberCategory;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.GlobalMemberInfoTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.GlobalMemberSearchTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.HoverTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.ListWorkspaceFoldersTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.RegisterWorkspaceFolderTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.TypeAtPositionTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.TypeInfoTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.UnregisterWorkspaceFolderTool;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceFolderDto;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import io.modelcontextprotocol.spec.McpSchema.Root;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
  private static final URI WORKSPACE_URI = Absolute.path(SRC_DIR).toUri();
  private static final String WORKSPACE_FOLDER = WORKSPACE_URI.toString();

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
  private GlobalMemberSearchTool globalMemberSearchTool;
  @Autowired
  private ListWorkspaceFoldersTool listWorkspaceFoldersTool;
  @Autowired
  private RegisterWorkspaceFolderTool registerWorkspaceFolderTool;
  @Autowired
  private UnregisterWorkspaceFolderTool unregisterWorkspaceFolderTool;
  @Autowired
  private McpRootsChangeConsumer rootsChangeConsumer;
  @Autowired
  private McpDocumentReader documentReader;
  @Autowired
  private TypeService typeService;
  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService diagnosticComputerExecutor;

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
  void analyzeDoesNotDeadlockWhenActionTriggersAutumnIndexBuild() {
    // Регресс на дедлок MCP-инструмента analyze_file на .os-классах фреймворка «ОСень».
    // Разбор держит блокировку документа на запись, пока считаются диагностики (на отдельном пуле).
    // Вывод типа внедрённого через ОСень бина запускает ленивую сборку Autumn-индекса, а она
    // реентрантно берёт блокировку того же документа на чтение — под чужой записью это вечный дедлок.
    // Кросс-поточность обязательна: на потоке-владельце записи то же чтение переиспользовало бы
    // блокировку и баг бы не проявился. Пул берём тот же, чтобы пробросить контекст рабочего пространства.
    var autumnDir = "src/test/resources/mcp/autumn-deadlock";
    var autumnFile = autumnDir + "/src/Приложение.os";
    workspaceBootstrap.index(Absolute.path(autumnDir));

    var types = assertTimeoutPreemptively(Duration.ofSeconds(60),
      () -> documentReader.analyze(autumnFile, document -> {
        var lines = document.getContentList();
        var line = -1;
        for (var i = 0; i < lines.length; i++) {
          if (lines[i].contains("Возврат Логгер")) {
            line = i;
          }
        }
        assertThat(line).as("строка с обращением к внедрённому бину не найдена в фикстуре").isNotEqualTo(-1);
        var position = new Position(line, lines[line].indexOf("Логгер") + 1);
        try {
          return diagnosticComputerExecutor
            .submit(() -> typeService.expressionTypesAt(document, position))
            .get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        } catch (ExecutionException e) {
          throw new IllegalStateException(e);
        }
      }));

    // Проверяем не только отсутствие дедлока, но и что вывод типа реально дошёл до Autumn-резолва:
    // получатель `Логгер` резолвится в одноимённый желудь (иначе цепочка до сборки индекса не дошла бы).
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).contains("Логгер");
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
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.name()).isEqualTo("Массив");
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Добавить", "Количество");
  }

  @Test
  void typeInfoReturnsMethodsAndPropertiesWithOsFileType() {
    var result = typeInfoTool.typeInfo("Массив", FileType.OS, WORKSPACE_FOLDER, null);

    assertThat(result.name()).isEqualTo("Массив");
    assertThat(result.methods()).extracting(TypeMemberDto::name).contains("Добавить", "Количество");
  }

  @Test
  void typeInfoThrowsForUnknownType() {
    assertThatThrownBy(() -> typeInfoTool.typeInfo("НетТакогоТипа", FileType.BSL, WORKSPACE_FOLDER, null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void typeInfoReturnsConstructorsForPlatformClass() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.constructors()).isNotEmpty();
    assertThat(result.constructors().get(0).parameters()).isNotNull();
  }

  @Test
  void typeInfoExposesEventsListEvenIfEmpty() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.events()).isNotNull();
    // У стандартных коллекций событий нет — но поле всегда присутствует.
    assertThat(result.events()).allSatisfy(event -> assertThat(event.kind()).isEqualTo("EVENT"));
  }

  @Test
  void typeInfoAcceptsExplicitLanguageParameter() {
    var ru = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, Language.RU);
    var en = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, Language.EN);

    assertThat(ru.methods()).isNotEmpty();
    assertThat(en.methods()).isNotEmpty();
    // В обоих локалях имя типа корректное (хоть оно может быть только в одной локали).
    assertThat(ru.name()).isNotBlank();
    assertThat(en.name()).isNotBlank();
  }

  @Test
  void typeInfoReturnsNullDefinedAtForPlatformType() {
    var result = typeInfoTool.typeInfo("Массив", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.definedAt()).isNull();
  }

  @Test
  void globalMemberInfoResolvesPlatformFunction() {
    var result = globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
    assertThat(result.member().kind()).isEqualTo("METHOD");
    assertThat(result.member().name()).isEqualTo("Сообщить");
    assertThat(result.member().signatures()).isNotNull();
  }

  @Test
  void globalMemberInfoResolvesByEnglishAlias() {
    var result = globalMemberInfoTool.globalMemberInfo("Message", FileType.BSL, WORKSPACE_FOLDER, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
    assertThat(result.member().kind()).isEqualTo("METHOD");
  }

  @Test
  void globalMemberInfoThrowsForUnknownName() {
    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("НетТакогоИмени", FileType.BSL, WORKSPACE_FOLDER, null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void globalMemberInfoAcceptsOscriptFileType() {
    var result = globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.OS, WORKSPACE_FOLDER, null);

    assertThat(result.kind()).isEqualTo("FUNCTION");
  }

  @Test
  void globalMemberSearchListsAllCategoriesByDefault() {
    var result = globalMemberSearchTool.globalMemberSearch(FileType.BSL, WORKSPACE_FOLDER, null, null, null);

    assertThat(result.count())
      .isEqualTo(result.functions().size() + result.properties().size() + result.enums().size());
    assertThat(result.functions()).isNotEmpty();
    assertThat(result.functions()).extracting(TypeMemberDto::name).contains("Сообщить");
    // Свойства и перечисления тоже должны присутствовать в полной выборке.
    assertThat(result.properties()).isNotEmpty();
    assertThat(result.enums()).isNotEmpty();
  }

  @Test
  void globalMemberSearchRestrictsToRequestedCategories() {
    var result = globalMemberSearchTool.globalMemberSearch(
      FileType.BSL, WORKSPACE_FOLDER, null, List.of(GlobalMemberCategory.FUNCTION), null);

    assertThat(result.functions()).isNotEmpty();
    assertThat(result.properties()).isEmpty();
    assertThat(result.enums()).isEmpty();
    assertThat(result.count()).isEqualTo(result.functions().size());
  }

  @Test
  void globalMemberSearchReturnsOnlyEnumsWhenRequested() {
    var result = globalMemberSearchTool.globalMemberSearch(
      FileType.BSL, WORKSPACE_FOLDER, null, List.of(GlobalMemberCategory.ENUM), null);

    assertThat(result.enums()).isNotEmpty();
    assertThat(result.functions()).isEmpty();
    assertThat(result.properties()).isEmpty();
  }

  @Test
  void globalMemberSearchMatchesFuzzilyAcrossCategories() {
    var result = globalMemberSearchTool.globalMemberSearch(FileType.BSL, WORKSPACE_FOLDER, "Сообщ", null, null);

    assertThat(result.functions()).extracting(TypeMemberDto::name).contains("Сообщить");
    assertThat(result.functions()).allSatisfy(member ->
      assertThat(member.name().toLowerCase()).contains("сообщ"));
  }

  @Test
  void globalMemberSearchRanksExactPrefixMatchFirst() {
    // Запрос совпадает как префикс с «Сообщить» и как подпоследовательность с другими именами —
    // более релевантное «Сообщить» должно быть выше в выдаче (ранжирование, как в автодополнении).
    var result = globalMemberSearchTool.globalMemberSearch(
      FileType.BSL, WORKSPACE_FOLDER, "Сообщить", List.of(GlobalMemberCategory.FUNCTION), null);

    assertThat(result.functions()).isNotEmpty();
    assertThat(result.functions().get(0).name()).isEqualTo("Сообщить");
  }

  @Test
  void globalMemberSearchReturnsEmptyForUnmatchedQuery() {
    var result = globalMemberSearchTool.globalMemberSearch(
      FileType.BSL, WORKSPACE_FOLDER, "btzzzqqqxyz", null, null);

    assertThat(result.count()).isZero();
    assertThat(result.functions()).isEmpty();
    assertThat(result.properties()).isEmpty();
    assertThat(result.enums()).isEmpty();
  }

  @Test
  void globalMemberSearchAcceptsOscriptFileType() {
    var result = globalMemberSearchTool.globalMemberSearch(FileType.OS, WORKSPACE_FOLDER, null, null, null);

    assertThat(result.functions()).isNotEmpty();
    assertThat(result.functions()).extracting(TypeMemberDto::name).contains("Сообщить");
  }

  @Test
  void globalMemberSearchThrowsWhenWorkspaceFolderIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() ->
      globalMemberSearchTool.globalMemberSearch(FileType.BSL, unknownRoot, null, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace folder matches");
  }

  @Test
  void globalMemberSearchThrowsWhenWorkspaceFolderIsMissing() {
    assertThatThrownBy(() ->
      globalMemberSearchTool.globalMemberSearch(FileType.BSL, null, null, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace folder is required");
  }

  @Test
  void typeInfoThrowsWhenWorkspaceFolderIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, unknownRoot, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace folder matches");
  }

  @Test
  void globalMemberInfoThrowsWhenWorkspaceFolderIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, unknownRoot, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace folder matches");
  }

  @Test
  void typeInfoThrowsWhenWorkspaceFolderIsMissing() {
    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, "  ", null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace folder is required");
  }

  @Test
  void globalMemberInfoThrowsWhenWorkspaceFolderIsMissing() {
    assertThatThrownBy(() -> globalMemberInfoTool.globalMemberInfo("Сообщить", FileType.BSL, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace folder is required");
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
  void listWorkspacesReportsIndexedWorkspace() {
    var result = listWorkspaceFoldersTool.listWorkspaceFolders();

    assertThat(result.workspaceFolders()).extracting(WorkspaceFolderDto::uri).contains(WORKSPACE_URI);
    assertThat(result.workspaceFolders())
      .filteredOn(folder -> WORKSPACE_URI.equals(folder.uri()))
      .singleElement()
      .satisfies(folder ->
        assertThat(folder.name()).isEqualTo(Absolute.path(SRC_DIR).getFileName().toString()));
    assertThat(result.hint()).contains("Registered workspace folders", WORKSPACE_FOLDER);
  }

  @Test
  void listWorkspacesTellsHowToRegisterWhenNothingIsIndexed() {
    unregisterWorkspaceFolderTool.unregisterWorkspaceFolder(WORKSPACE_FOLDER);

    var result = listWorkspaceFoldersTool.listWorkspaceFolders();

    assertThat(result.workspaceFolders()).isEmpty();
    assertThat(result.hint()).contains("No workspace folder is registered", "register_workspace_folder");
  }

  @Test
  void registerWorkspaceIndexesDirectoryAndUnblocksTools() {
    var cliDir = Absolute.path("src/test/resources/cli");

    var result = registerWorkspaceFolderTool.registerWorkspaceFolder(cliDir.toString(), null);

    assertThat(result.alreadyRegistered()).isFalse();
    assertThat(result.workspaceFolder().uri()).isEqualTo(cliDir.toUri());
    assertThat(result.workspaceFolder().name()).isEqualTo("cli");
    assertThat(analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl").diagnostics()).isNotEmpty();
  }

  @Test
  void registerWorkspaceAcceptsFileUri() {
    var cliUri = Absolute.path("src/test/resources/cli").toUri();

    var result = registerWorkspaceFolderTool.registerWorkspaceFolder(cliUri.toString(), null);

    assertThat(result.workspaceFolder().uri()).isEqualTo(cliUri);
  }

  @Test
  void registerWorkspaceKeepsNameGivenByClient() {
    var cliDir = Absolute.path("src/test/resources/cli");

    var result = registerWorkspaceFolderTool.registerWorkspaceFolder(cliDir.toString(), "Демо-конфигурация");

    assertThat(result.workspaceFolder().name()).isEqualTo("Демо-конфигурация");
    assertThat(listWorkspaceFoldersTool.listWorkspaceFolders().workspaceFolders())
      .filteredOn(folder -> cliDir.toUri().equals(folder.uri()))
      .singleElement()
      .satisfies(folder -> assertThat(folder.name()).isEqualTo("Демо-конфигурация"));
  }

  @Test
  void registerWorkspaceDoesNotReindexKnownDirectory() {
    var result = registerWorkspaceFolderTool.registerWorkspaceFolder(Absolute.path(SRC_DIR).toString(), null);

    assertThat(result.alreadyRegistered()).isTrue();
    assertThat(result.workspaceFolder().uri()).isEqualTo(WORKSPACE_URI);
  }

  @Test
  void registerWorkspaceRejectsFileInsteadOfDirectory() {
    assertThatThrownBy(() -> registerWorkspaceFolderTool.registerWorkspaceFolder(Absolute.path(FILE).toString(), null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is a file, not a directory");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void registerWorkspaceExplainsPathThatCannotBeCanonicalized() {
    // Канонизация пути падает необъявленным IOException («File name too long»); клиент должен
    // получить объяснение, что передать, а не сырую ошибку ввода-вывода.
    var tooLong = Absolute.path("src/test/resources").resolve("x".repeat(5000)).toString();

    assertThatThrownBy(() -> registerWorkspaceFolderTool.registerWorkspaceFolder(tooLong, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported workspace folder");
  }

  @Test
  void registerWorkspaceRejectsMissingDirectory() {
    var missing = Absolute.path("src/test/resources/there-is-no-such-directory").toString();

    assertThatThrownBy(() -> registerWorkspaceFolderTool.registerWorkspaceFolder(missing, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("does not exist");
  }

  @Test
  void unregisterWorkspaceRemovesItAndReportsTheRest() {
    registerWorkspaceFolderTool.registerWorkspaceFolder(Absolute.path("src/test/resources/cli").toString(), null);

    var result = unregisterWorkspaceFolderTool.unregisterWorkspaceFolder(WORKSPACE_FOLDER);

    assertThat(result.workspaceFolder().uri()).isEqualTo(WORKSPACE_URI);
    assertThat(result.stillDeclaredByRoots()).isFalse();
    assertThat(result.remaining()).extracting(WorkspaceFolderDto::uri).doesNotContain(WORKSPACE_URI);
    assertThat(result.remaining()).isNotEmpty();
  }

  @Test
  void unregisterWorkspaceFolderThrowsWhenWorkspaceFolderIsUnknown() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> unregisterWorkspaceFolderTool.unregisterWorkspaceFolder(unknownRoot))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("No registered workspace folder matches")
      .hasMessageContaining(WORKSPACE_FOLDER);
  }

  @Test
  void unknownWorkspaceFolderErrorPointsAtWorkspaceFolderTools() {
    var unknownRoot = Absolute.path("src/test/resources/diagnostics").toUri().toString();

    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, unknownRoot, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(WORKSPACE_FOLDER)
      .hasMessageContaining("register_workspace_folder")
      .hasMessageContaining("list_workspace_folders");
  }

  @Test
  void missingWorkspaceFolderErrorPointsAtWorkspaceFolderTools() {
    assertThatThrownBy(() -> typeInfoTool.typeInfo("Массив", FileType.BSL, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Workspace folder is required")
      .hasMessageContaining("list_workspace_folders");
  }

  @Test
  void fileOutsideWorkspaceErrorPointsAtRegisterTool() {
    assertThatThrownBy(() -> analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("File is not part of any registered workspace folder")
      .hasMessageContaining("register_workspace_folder");
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

  @Test
  void mcpRootAndExplicitRegistrationDoNotEvictEachOther() {
    var cliDir = Absolute.path("src/test/resources/cli");
    var root = new Root(cliDir.toUri().toString(), "cli");
    registerWorkspaceFolderTool.registerWorkspaceFolder(cliDir.toString(), null);
    rootsChangeConsumer.accept(null, List.of(root));

    // Папку держат оба владельца: снятие явной регистрации её не убирает.
    var unregistered = unregisterWorkspaceFolderTool.unregisterWorkspaceFolder(cliDir.toUri().toString());
    assertThat(unregistered.stillDeclaredByRoots()).isTrue();
    assertThat(analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl").diagnostics()).isNotEmpty();

    // Ушёл последний владелец — ушла и папка.
    rootsChangeConsumer.accept(null, List.of());
    assertThatThrownBy(() -> analyzeFileTool.analyzeFile("src/test/resources/cli/test.bsl"))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
