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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.lsp.BSLTextDocumentService;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.CallHierarchyTool;
import com.github._1c_syntax.bsl.languageserver.mcp.tools.FindReferencesTool;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
  "spring.ai.mcp.server.enabled=false",
  "spring.ai.mcp.server.annotation-scanner.enabled=false",
  "spring.main.web-application-type=none"
})
@ActiveProfiles("mcp")
class SourceReferencesWithoutContentTest extends AbstractServerContextAwareTest {

  @Autowired
  private ReferenceResolver resolver;
  @Autowired
  private ReferencesProvider references;
  @Autowired
  private CallHierarchyProvider hierarchy;
  @Autowired
  private BSLTextDocumentService lsp;
  @Autowired
  private FindReferencesTool mcpReferences;
  @Autowired
  private CallHierarchyTool mcpHierarchy;

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void everyPositionKeepsSourceResolutionAndProviderResultsAfterContentIsReleased(String extension) {
    initServerContext();
    var content = """
      Перем Модульная;
      Процедура Тест(Параметр)
          Перем Локальная;
          Локальная = Новый Структура;
          Локальная.Вставить("Ключ", Параметр);
          Вызвать();
          // Комментарий
      КонецПроцедуры

      Процедура Вызвать()
      КонецПроцедуры
      """;
    if (extension.equals("os")) {
      content = "&НеизвестнаяАннотация(Параметр = \"x\")\n" + content;
    }
    var uri = Path.of("src/test/resources/references/withoutContent." + extension).toUri();
    var document = TestUtils.getDocumentContext(uri, content, context);

    assertEveryPositionSurvivesRelease(document, content);
  }

  @Test
  void annotationNamesParametersAndNestedAnnotationsKeepTheirSourceTargets() {
    initServerContext("src/test/resources/references/annotations");
    var content = """
      &ТестоваяАннотация(ВторойПараметр = "x", "y")
      Перем Значение;
      &ТестоваяАннотация2
      Перем Другая;
      &ТестоваяАннотация(ВторойПараметр = &ТестоваяАннотация2)
      Процедура Тест()
      КонецПроцедуры
      """;
    var document = TestUtils.getDocumentContext(
      Path.of("src/test/resources/references/withoutContentAnnotations.os").toUri(), content, context);
    assertThat(resolver.findSourceDefinedSymbol(document.getUri(), new Position(0, 2)))
      .isPresent().get().extracting(SourceDefinedSymbol::getName).isEqualTo("ТестоваяАннотация");
    assertThat(resolver.findSourceDefinedSymbol(document.getUri(), new Position(0, 23)))
      .isPresent().get().extracting(SourceDefinedSymbol::getName).isEqualTo("ВторойПараметр");

    assertEveryPositionSurvivesRelease(document, content);
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void lspAndMcpReturnEmptyAtKeywordWithoutRebuildingContent(String extension) throws Exception {
    initServerContext();
    var document = TestUtils.getDocumentContext(
      Path.of("src/test/resources/references/withoutContentProtocols." + extension).toUri(), """
      Процедура Тест()
          Тест();
      КонецПроцедуры
      """, context);
    release(document);
    var identifier = new TextDocumentIdentifier(document.getUri().toString());
    var params = referenceParams(document, new Position(0, 0));

    assertThat(lsp.references(params).get()).isEmpty();
    assertThat(lsp.prepareCallHierarchy(new CallHierarchyPrepareParams(identifier, new Position(0, 0))).get()).isNull();
    var file = Path.of(document.getUri()).toString();
    assertThat(mcpReferences.findReferences(file, 0, 0).references()).isEmpty();
    assertThat(mcpHierarchy.callHierarchy(file, 0, 0).target()).isNull();
    assertThat(mcpReferences.findReferences(file, 0, 11).references()).hasSize(2);
    assertThat(mcpHierarchy.callHierarchy(file, 0, 11).target()).isNotNull();
    assertReleased(document);
  }

  @Test
  void allAnnotationLiteralsKeepFullResolverSemanticsWithoutContent() throws Exception {
    initServerContext("src/test/resources/references/annotations");
    var file = Path.of("src/test/resources/references/AnnotationReferenceFinder.os");
    var document = TestUtils.getDocumentContextFromFile(file.toString(), context);

    assertEveryPositionSurvivesRelease(document, Files.readString(file));
  }

  @Test
  void annotationTargetTracksItsRebuiltDefinitionAndRemovalWithoutReindexingUsage() {
    initServerContext("src/test/resources/references/annotations");
    var definition = context.getDocument(Path.of(
      "src/test/resources/references/annotations/ТестоваяАннотация.os").toAbsolutePath().toUri());
    var usage = TestUtils.getDocumentContext(
      Path.of("src/test/resources/references/retainedAnnotation.os").toUri(), """
      &ТестоваяАннотация("x")
      Перем Значение;
      """, context);
    var position = new Position(0, 2);
    var original = resolver.findSourceDefinedSymbol(usage.getUri(), position).orElseThrow();
    release(usage);

    context.rebuildDocument(definition, """
      &Аннотация("ТестоваяАннотация")
      Процедура ПриСозданииОбъекта(Значение)
      КонецПроцедуры
      """, 1);

    var updated = resolver.findSourceDefinedSymbol(usage.getUri(), position).orElseThrow();
    assertThat(updated.getSelectionRange().getStart().getLine()).isEqualTo(1);
    assertThat(updated.getSelectionRange()).isNotEqualTo(original.getSelectionRange());
    assertReleased(usage);

    context.removeDocument(definition.getUri());
    assertThat(resolver.findSourceDefinedSymbol(usage.getUri(), position)).isEmpty();
  }

  @Test
  void rebuildingAndRemovingUsageDropsItsOldAnnotationPositions() {
    initServerContext("src/test/resources/references/annotations");
    var usage = TestUtils.getDocumentContext(
      Path.of("src/test/resources/references/changedAnnotation.os").toUri(), """
      &ТестоваяАннотация
      Перем Значение;
      """, context);
    assertThat(resolver.findSourceDefinedSymbol(usage.getUri(), new Position(0, 2))).isPresent();

    context.rebuildDocument(usage, """
      // Новая позиция

      &ТестоваяАннотация2
      Перем Значение;
      """, 1);
    release(usage);

    assertThat(resolver.findSourceDefinedSymbol(usage.getUri(), new Position(0, 2))).isEmpty();
    assertThat(resolver.findSourceDefinedSymbol(usage.getUri(), new Position(2, 2)))
      .isPresent().get().extracting(SourceDefinedSymbol::getName).isEqualTo("ТестоваяАннотация2");
    context.removeDocument(usage.getUri());
    assertThat(resolver.findSourceDefinedSymbol(usage.getUri(), new Position(2, 2))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"bsl", "os"})
  void incomingAndOutgoingCallsRemainAvailableAfterRelease(String extension) {
    initServerContext();
    var document = TestUtils.getDocumentContext(
      Path.of("src/test/resources/references/retainedCalls." + extension).toUri(), """
      Процедура Первый()
          Второй();
      КонецПроцедуры
      Процедура Второй()
          Первый();
      КонецПроцедуры
      """, context);
    var prepare = new CallHierarchyPrepareParams(
      new TextDocumentIdentifier(document.getUri().toString()), new Position(0, 12));
    var item = hierarchy.prepareCallHierarchy(document, prepare).getFirst();
    var incomingParams = new CallHierarchyIncomingCallsParams(item);
    var outgoingParams = new CallHierarchyOutgoingCallsParams(item);
    var incoming = hierarchy.incomingCalls(document, incomingParams);
    var outgoing = hierarchy.outgoingCalls(document, outgoingParams);
    assertThat(incoming).hasSize(1);
    assertThat(outgoing).hasSize(1);

    release(document);

    assertThat(hierarchy.incomingCalls(document, incomingParams)).containsExactlyElementsOf(incoming);
    assertThat(hierarchy.outgoingCalls(document, outgoingParams)).containsExactlyElementsOf(outgoing);
    assertReleased(document);
  }

  private void assertEveryPositionSurvivesRelease(DocumentContext document, String content) {
    var positions = positions(content);
    var symbols = positions.stream()
      .map(position -> resolver.findReference(document.getUri(), position).flatMap(Reference::getSourceDefinedSymbol))
      .toList();
    var locations = positions.stream()
      .map(position -> references.getReferences(document, referenceParams(document, position)))
      .toList();
    var items = positions.stream()
      .map(position -> hierarchy.prepareCallHierarchy(document,
        new CallHierarchyPrepareParams(new TextDocumentIdentifier(document.getUri().toString()), position)))
      .toList();

    release(document);

    for (var i = 0; i < positions.size(); i++) {
      var position = positions.get(i);
      assertThat(resolver.findSourceDefinedSymbol(document.getUri(), position))
        .as("symbol at %s", position).isEqualTo(symbols.get(i));
      assertThat(references.getReferences(document, referenceParams(document, position))).as("references at %s", position)
        .containsExactlyElementsOf(locations.get(i));
      assertThat(hierarchy.prepareCallHierarchy(document,
        new CallHierarchyPrepareParams(new TextDocumentIdentifier(document.getUri().toString()), position))).as("hierarchy at %s", position)
        .containsExactlyElementsOf(items.get(i));
    }
    assertReleased(document);
  }

  private void release(DocumentContext document) {
    context.tryClearDocument(document);
    assertReleased(document);
  }

  private void assertReleased(DocumentContext document) {
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    assertThatThrownBy(document::getAst).isInstanceOf(DocumentContentUnavailableException.class);
  }

  private static ReferenceParams referenceParams(DocumentContext document, Position position) {
    var params = new ReferenceParams();
    params.setTextDocument(new TextDocumentIdentifier(document.getUri().toString()));
    params.setPosition(position);
    params.setContext(new ReferenceContext(true));
    return params;
  }

  private static List<Position> positions(String content) {
    var positions = new ArrayList<Position>();
    var lines = content.split("\n", -1);
    for (var line = 0; line < lines.length; line++) {
      for (var column = 0; column <= lines[line].length(); column++) {
        positions.add(new Position(line, column));
      }
    }
    return positions;
  }
}
